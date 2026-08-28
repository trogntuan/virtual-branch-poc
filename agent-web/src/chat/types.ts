export type ChatMessageType =
  | 'TEXT'
  | 'FILE'
  | 'COLLAB_REQUEST'
  | 'COLLAB_STATUS'
  | 'COLLAB_CANCEL';

export type ChatSenderRole = 'AGENT' | 'CUSTOMER';

export interface ChatDocumentInfo {
  documentId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
}

export interface ChatCollabInfo {
  collabId: string;
  status: string;
  documentId: string;
}

export interface ChatMessage {
  messageId: string;
  sentAt: string;
  senderRole: ChatSenderRole;
  senderIdentity: string;
  senderName: string | null;
  messageType: ChatMessageType;
  text?: string | null;
  document?: ChatDocumentInfo | null;
  collab?: ChatCollabInfo | null;
  clientMessageId?: string | null;
  payload?: Record<string, unknown>;
}

export interface ChatSettings {
  maxFileSizeBytes: number;
  maxFileSizeLabel: string;
  allowedContentTypes: string[];
  allowedExtensions: string[];
  allowedExtensionsLabel: string;
}

export interface ChatHistoryResponse {
  sessionId: string;
  messages: ChatMessage[];
  hasMore: boolean;
}

export interface WsChatMessageEnvelope {
  version: number;
  type: 'CHAT_MESSAGE';
  messageId: string;
  sessionId: string;
  sentAt: string;
  senderRole: ChatSenderRole;
  senderIdentity: string;
  senderName?: string | null;
  messageType: ChatMessageType;
  clientMessageId?: string | null;
  payload: Record<string, unknown>;
}

export interface WsChatErrorEnvelope {
  version: number;
  type: 'CHAT_ERROR';
  code: string;
  message: string;
  clientMessageId?: string | null;
}
