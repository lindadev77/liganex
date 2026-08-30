import client, { ApiError, getAccessToken } from './client';
import type {
  ChatConversation,
  ChatMessage,
  ChatStreamHandlers,
  Citation,
  CreateConversationRequest,
  DoneSseEvent,
  ErrorSseEvent,
  MessageStatus,
  SendMessageRequest,
} from './ragTypes';

const API_PREFIX = '/v1/chat/conversations';
type JsonRecord = Record<string, unknown>;

function record(value: unknown, label: string): JsonRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(-1, `${label}响应格式不正确`);
  }
  return value as JsonRecord;
}

function identifier(value: unknown, label: string): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  throw new ApiError(-1, `${label}缺少标识`);
}

function text(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item) => typeof item === 'string' || typeof item === 'number').map(String)
    : [];
}

function listPayload(value: unknown, label: string): unknown[] {
  if (Array.isArray(value)) return value;
  const payload = record(value, label);
  const items = payload.items ?? payload.records ?? payload.content;
  if (Array.isArray(items)) return items;
  throw new ApiError(-1, `${label}列表响应格式不正确`);
}

function normalizeMessageStatus(value: unknown): MessageStatus {
  const status = text(value, 'completed').toLowerCase();
  if (status === 'streaming' || status === 'failed' || status === 'cancelled') return status;
  return 'completed';
}

export function parseCitation(value: unknown): Citation {
  const item = record(value, '引用');
  const page = item.pageNumber ?? item.page;
  const offsetPosition =
    typeof item.startOffset === 'number' && typeof item.endOffset === 'number'
      ? `字符 ${item.startOffset}-${item.endOffset}`
      : undefined;
  return {
    id: identifier(item.id ?? item.chunkId ?? `${String(item.documentId)}-${String(page ?? '')}`, '引用'),
    documentId: identifier(item.documentId, '引用文档'),
    documentName: text(item.documentName ?? item.sourceName, '未知文档'),
    chunkId:
      typeof item.chunkId === 'string' || typeof item.chunkId === 'number'
        ? String(item.chunkId)
        : undefined,
    excerpt: text(item.excerpt ?? item.content),
    pageNumber: typeof page === 'number' ? page : undefined,
    position: text(item.position ?? item.location) || offsetPosition,
    available: item.available !== false && item.deleted !== true,
  };
}

export function parseConversation(value: unknown): ChatConversation {
  const item = record(value, '会话');
  return {
    id: identifier(item.id ?? item.conversationId, '会话'),
    title: text(item.title, '新会话'),
    knowledgeBaseIds: stringArray(item.knowledgeBaseIds),
    createdAt: text(item.createdAt),
    updatedAt: text(item.updatedAt),
  };
}

export function parseChatMessage(value: unknown): ChatMessage {
  const item = record(value, '消息');
  const role = text(item.role).toLowerCase();
  let citations: unknown = item.citations;
  if (typeof citations === 'string' && citations.trim()) {
    try {
      citations = JSON.parse(citations);
    } catch {
      citations = [];
    }
  }
  return {
    id: identifier(item.id ?? item.messageId, '消息'),
    conversationId: identifier(item.conversationId, '会话'),
    role: role === 'user' || role === 'system' ? role : 'assistant',
    content: text(item.content),
    status: normalizeMessageStatus(item.status),
    citations: Array.isArray(citations) ? citations.map(parseCitation) : [],
    createdAt: text(item.createdAt),
    errorMessage: text(item.errorMessage) || undefined,
  };
}

export async function listConversations(): Promise<ChatConversation[]> {
  const response = await client.get<unknown>(API_PREFIX);
  return listPayload(response, '会话').map(parseConversation);
}

export async function createConversation(
  request: CreateConversationRequest,
): Promise<ChatConversation> {
  return parseConversation(
    await client.post<unknown>(API_PREFIX, {
      ...request,
      knowledgeBaseIds: request.knowledgeBaseIds.map(Number),
    }),
  );
}

export async function getConversation(id: string): Promise<ChatConversation> {
  return parseConversation(await client.get<unknown>(`${API_PREFIX}/${id}`));
}

export async function updateConversationKnowledgeBases(
  id: string,
  knowledgeBaseIds: string[],
): Promise<ChatConversation> {
  return parseConversation(
    await client.put<unknown>(`${API_PREFIX}/${id}/knowledge-bases`, {
      knowledgeBaseIds: knowledgeBaseIds.map(Number),
    }),
  );
}

export async function listMessages(conversationId: string): Promise<ChatMessage[]> {
  const response = await client.get<unknown>(`${API_PREFIX}/${conversationId}/messages`);
  return listPayload(response, '消息').map((value) => {
    const item = record(value, '消息');
    return parseChatMessage({ ...item, conversationId: item.conversationId ?? conversationId });
  });
}

export async function deleteConversation(id: string): Promise<void> {
  await client.delete(`${API_PREFIX}/${id}`);
}

export async function cancelGeneration(conversationId: string): Promise<void> {
  await client.post(`${API_PREFIX}/${conversationId}/cancel`);
}

interface RawSseMessage {
  event?: string;
  data: string;
}

export function parseSseBlock(block: string): RawSseMessage | null {
  let event: string | undefined;
  const data: string[] = [];
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trimEnd();
    if (!line || line.startsWith(':')) continue;
    if (line.startsWith('event:')) event = line.slice(6).trim();
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  }
  return data.length ? { event, data: data.join('\n') } : null;
}

function parseEvent(message: RawSseMessage) {
  let data: unknown;
  try {
    data = JSON.parse(message.data);
  } catch {
    data = message.data;
  }
  const payload = data && typeof data === 'object' ? (data as JsonRecord) : {};
  const type = message.event ?? text(payload.type, 'token');
  if (type === 'token') {
    const content = typeof data === 'string' ? data : text(payload.content ?? payload.delta ?? payload.token);
    return { type: 'token' as const, content };
  }
  if (type === 'done') {
    const done: DoneSseEvent = {
      type: 'done',
      messageId: identifier(payload.messageId ?? payload.id, '完成事件'),
      content: text(payload.answer ?? payload.content) || undefined,
      citations: Array.isArray(payload.citations) ? payload.citations.map(parseCitation) : [],
    };
    return done;
  }
  if (type === 'error') {
    const error: ErrorSseEvent = {
      type: 'error',
      code: text(payload.code) || undefined,
      message: typeof data === 'string' ? data : text(payload.message, '生成回答失败'),
    };
    return error;
  }
  return null;
}

export async function consumeSseStream(
  stream: ReadableStream<Uint8Array>,
  handlers: ChatStreamHandlers,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let completed = false;
  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() ?? '';
      for (const block of blocks) {
        const raw = parseSseBlock(block);
        if (!raw) continue;
        const event = parseEvent(raw);
        if (!event) continue;
        if (event.type === 'token') handlers.onToken(event);
        if (event.type === 'done') {
          completed = true;
          handlers.onDone(event);
        }
        if (event.type === 'error') {
          completed = true;
          handlers.onError(event);
        }
      }
      if (done) break;
    }
  } finally {
    reader.releaseLock();
  }
  if (!completed) throw new ApiError(-1, '回答连接意外中断，请重试');
}

async function openMessageStream(
  url: string,
  body: SendMessageRequest | undefined,
  handlers: ChatStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const token = getAccessToken();
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal,
  });
  if (!response.ok) {
    let message = `生成请求失败（HTTP ${response.status}）`;
    try {
      const error = (await response.json()) as { message?: string };
      if (error.message) message = error.message;
    } catch {
      // 非 JSON 错误体保持通用信息，避免把代理页或内部细节展示给用户。
    }
    throw new ApiError(response.status, message);
  }
  if (!response.body) throw new ApiError(-1, '浏览器未收到流式响应');
  await consumeSseStream(response.body, handlers);
}

export function streamMessage(
  conversationId: string,
  request: SendMessageRequest,
  handlers: ChatStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  return openMessageStream(
    `/api${API_PREFIX}/${conversationId}/messages/stream`,
    request,
    handlers,
    signal,
  );
}
