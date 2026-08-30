import client, { ApiError } from './client';
import type {
  CreateKnowledgeBaseRequest,
  CreateTextDocumentRequest,
  DocumentSourceType,
  DocumentStatus,
  KnowledgeBase,
  KnowledgeBaseStatus,
  KnowledgeDocument,
  UpdateKnowledgeBaseRequest,
} from './ragTypes';

const API_PREFIX = '/v1/knowledge-bases';

type JsonRecord = Record<string, unknown>;

function record(value: unknown, label: string): JsonRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(-1, `${label}响应格式不正确`);
  }
  return value as JsonRecord;
}

function text(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function identifier(value: unknown, label: string): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  throw new ApiError(-1, `${label}缺少标识`);
}

function numberValue(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function normalizeBaseStatus(value: unknown): KnowledgeBaseStatus {
  const status = text(value, 'ready').toLowerCase();
  return status === 'disabled' || status === 'deleting' ? status : 'ready';
}

function normalizeDocumentStatus(value: unknown): DocumentStatus {
  const status = text(value, 'pending').toLowerCase();
  if (status === 'processing' || status === 'ready' || status === 'failed' || status === 'deleting') {
    return status;
  }
  return 'pending';
}

function normalizeSourceType(value: unknown, mediaType: unknown, filename: unknown): DocumentSourceType {
  const sourceType = text(value, 'text').toLowerCase();
  if (sourceType === 'txt' || sourceType === 'markdown' || sourceType === 'pdf') return sourceType;
  const media = text(mediaType).toLowerCase();
  const extension = text(filename).split('.').pop()?.toLowerCase();
  if (media === 'application/pdf' || extension === 'pdf') return 'pdf';
  if (media === 'text/markdown' || extension === 'md' || extension === 'markdown') return 'markdown';
  if (sourceType === 'file' || media === 'text/plain' || extension === 'txt') return 'txt';
  return 'text';
}

export function parseKnowledgeBase(value: unknown): KnowledgeBase {
  const item = record(value, '知识库');
  return {
    id: identifier(item.id ?? item.knowledgeBaseId, '知识库'),
    name: text(item.name, '未命名知识库'),
    description: text(item.description),
    status: normalizeBaseStatus(item.status),
    documentCount:
      typeof item.documentCount === 'number' ? numberValue(item.documentCount) : undefined,
    readyDocumentCount:
      typeof item.readyDocumentCount === 'number' ? numberValue(item.readyDocumentCount) : undefined,
    createdAt: text(item.createdAt),
    updatedAt: text(item.updatedAt),
  };
}

export function parseKnowledgeDocument(value: unknown): KnowledgeDocument {
  const item = record(value, '文档');
  return {
    id: identifier(item.id ?? item.documentId, '文档'),
    knowledgeBaseId: identifier(item.knowledgeBaseId, '知识库'),
    name: text(item.title ?? item.name ?? item.originalFilename, '未命名文档'),
    sourceType: normalizeSourceType(
      item.sourceType ?? item.type,
      item.mediaType,
      item.originalFilename,
    ),
    status: normalizeDocumentStatus(item.status),
    progress: Math.min(100, Math.max(0, numberValue(item.progress))),
    chunkCount: numberValue(item.chunkCount),
    sizeBytes: numberValue(item.sizeBytes ?? item.fileSize),
    errorMessage: text(item.errorSummary ?? item.errorMessage ?? item.failureReason) || undefined,
    createdAt: text(item.createdAt),
    updatedAt: text(item.updatedAt),
  };
}

function listPayload(value: unknown, label: string): unknown[] {
  if (Array.isArray(value)) return value;
  const payload = record(value, label);
  const items = payload.items ?? payload.records ?? payload.content;
  if (Array.isArray(items)) return items;
  throw new ApiError(-1, `${label}列表响应格式不正确`);
}

export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  const response = await client.get<unknown>(API_PREFIX);
  return listPayload(response, '知识库').map(parseKnowledgeBase);
}

export async function getKnowledgeBase(id: string): Promise<KnowledgeBase> {
  return parseKnowledgeBase(await client.get<unknown>(`${API_PREFIX}/${id}`));
}

export async function createKnowledgeBase(
  request: CreateKnowledgeBaseRequest,
): Promise<KnowledgeBase> {
  return parseKnowledgeBase(await client.post<unknown>(API_PREFIX, request));
}

export async function updateKnowledgeBase(
  id: string,
  request: UpdateKnowledgeBaseRequest,
): Promise<KnowledgeBase> {
  return parseKnowledgeBase(await client.put<unknown>(`${API_PREFIX}/${id}`, request));
}

export async function deleteKnowledgeBase(id: string): Promise<void> {
  await client.delete(`${API_PREFIX}/${id}`);
}

export async function listDocuments(knowledgeBaseId: string): Promise<KnowledgeDocument[]> {
  const response = await client.get<unknown>(`${API_PREFIX}/${knowledgeBaseId}/documents`);
  return listPayload(response, '文档').map(parseKnowledgeDocument);
}

export async function createTextDocument(
  knowledgeBaseId: string,
  request: CreateTextDocumentRequest,
): Promise<KnowledgeDocument> {
  return parseKnowledgeDocument(
    await client.post<unknown>(`${API_PREFIX}/${knowledgeBaseId}/documents/text`, request),
  );
}

export async function uploadDocument(
  knowledgeBaseId: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<KnowledgeDocument> {
  const body = new FormData();
  body.append('file', file);
  const response = await client.post<unknown>(`${API_PREFIX}/${knowledgeBaseId}/documents/upload`, body, {
    onUploadProgress: (event) => {
      if (event.total && onProgress) onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });
  return parseKnowledgeDocument(response);
}

export async function retryDocument(
  knowledgeBaseId: string,
  documentId: string,
): Promise<KnowledgeDocument> {
  return parseKnowledgeDocument(
    await client.post<unknown>(
      `${API_PREFIX}/${knowledgeBaseId}/documents/${documentId}/retry`,
    ),
  );
}

export async function deleteDocument(
  knowledgeBaseId: string,
  documentId: string,
): Promise<void> {
  await client.delete(`${API_PREFIX}/${knowledgeBaseId}/documents/${documentId}`);
}

export const DOCUMENT_POLLING_STATUSES = new Set<DocumentStatus>([
  'pending',
  'processing',
  'deleting',
]);

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
const SUPPORTED_EXTENSIONS = new Set(['txt', 'md', 'markdown', 'pdf']);
const SUPPORTED_MIME_TYPES = new Set([
  'text/plain',
  'text/markdown',
  'application/pdf',
  'application/octet-stream',
  '',
]);

export function validateKnowledgeFile(file: Pick<File, 'name' | 'size' | 'type'>): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!SUPPORTED_EXTENSIONS.has(extension) || !SUPPORTED_MIME_TYPES.has(file.type)) {
    return '仅支持 TXT、Markdown 和 PDF 文件';
  }
  if (file.size <= 0) return '文件内容不能为空';
  if (file.size > MAX_DOCUMENT_BYTES) return '文件不能超过 10 MB';
  return null;
}
