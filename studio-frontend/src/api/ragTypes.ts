export type KnowledgeBaseStatus = 'ready' | 'disabled' | 'deleting';

export type DocumentStatus =
  | 'pending'
  | 'processing'
  | 'ready'
  | 'failed'
  | 'deleting';

export type DocumentSourceType = 'text' | 'txt' | 'markdown' | 'pdf';

export interface KnowledgeBase {
  id: string;
  name: string;
  description: string;
  status: KnowledgeBaseStatus;
  documentCount?: number;
  readyDocumentCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeDocument {
  id: string;
  knowledgeBaseId: string;
  name: string;
  sourceType: DocumentSourceType;
  status: DocumentStatus;
  progress: number;
  chunkCount: number;
  sizeBytes: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateKnowledgeBaseRequest {
  name: string;
  description?: string;
}

export interface UpdateKnowledgeBaseRequest {
  name: string;
  description?: string;
}

export interface CreateTextDocumentRequest {
  title: string;
  content: string;
}

export type ChatRole = 'user' | 'assistant' | 'system';
export type MessageStatus = 'completed' | 'streaming' | 'failed' | 'cancelled';

export interface Citation {
  id: string;
  documentId: string;
  documentName: string;
  chunkId?: string;
  excerpt: string;
  pageNumber?: number;
  position?: string;
  available: boolean;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  role: ChatRole;
  content: string;
  status: MessageStatus;
  citations: Citation[];
  createdAt: string;
  errorMessage?: string;
}

export interface ChatConversation {
  id: string;
  title: string;
  knowledgeBaseIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateConversationRequest {
  title?: string;
  knowledgeBaseIds: string[];
}

export interface SendMessageRequest {
  question: string;
}

export interface TokenSseEvent {
  type: 'token';
  content: string;
}

export interface DoneSseEvent {
  type: 'done';
  messageId: string;
  content?: string;
  citations: Citation[];
}

export interface ErrorSseEvent {
  type: 'error';
  code?: string;
  message: string;
}

export type ChatSseEvent = TokenSseEvent | DoneSseEvent | ErrorSseEvent;

export interface ChatStreamHandlers {
  onToken: (event: TokenSseEvent) => void;
  onDone: (event: DoneSseEvent) => void;
  onError: (event: ErrorSseEvent) => void;
}
