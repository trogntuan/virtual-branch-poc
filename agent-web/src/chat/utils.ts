import type { ChatMessage, ChatSenderRole } from './types';
import type { ChatMessageResponse } from '../api/virtualBranchApi';

export function buildChatWsUrl(
  sessionId: string,
  identity: string,
  role: ChatSenderRole,
  name: string,
): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const params = new URLSearchParams({
    identity,
    role,
    name,
  });
  return `${proto}//${window.location.host}/api/v1/ws/sessions/${encodeURIComponent(sessionId)}/chat?${params}`;
}

export function newClientMessageId(): string {
  return `cli-${crypto.randomUUID()}`;
}

export function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(0)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export function isPdfDocument(contentType: string, fileName: string): boolean {
  if (contentType === 'application/pdf') return true;
  return fileName.toLowerCase().endsWith('.pdf');
}

export function isImageDocument(contentType: string): boolean {
  return contentType.startsWith('image/');
}

export function formatChatTime(sentAt: string): string {
  return new Date(sentAt).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

/** Fix legacy rows / query strings stored without UTF-8 decode. */
export function decodeDisplayName(name: string | null | undefined, fallback: string): string {
  if (!name?.trim()) return fallback;
  const raw = name.trim();
  if (!raw.includes('%')) return raw;
  try {
    return decodeURIComponent(raw.replace(/\+/g, ' '));
  } catch {
    return raw;
  }
}

export function fileTypeLabel(contentType: string, fileName: string): string {
  if (isPdfDocument(contentType, fileName)) return 'PDF';
  if (contentType.includes('spreadsheet') || fileName.match(/\.xlsx?$/i)) return 'Excel';
  if (contentType.includes('msword') || fileName.match(/\.doc$/i)) return 'DOC';
  if (isImageDocument(contentType)) return 'Ảnh';
  return 'File';
}

export function buildFileAccept(settings: { allowedExtensions: string[]; allowedContentTypes: string[] }): string {
  const extPart = settings.allowedExtensions.map((ext) => `.${ext}`).join(',');
  const mimePart = settings.allowedContentTypes.join(',');
  return [extPart, mimePart].filter(Boolean).join(',');
}

/** Latest collab status per collabId (messages must be sorted by sentAt). */
export function buildLatestCollabStatusMap(messages: ChatMessage[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const message of messages) {
    const collabId = message.collab?.collabId;
    const status = message.collab?.status;
    if (!collabId || !status) continue;
    if (
      message.messageType !== 'COLLAB_REQUEST' &&
      message.messageType !== 'COLLAB_STATUS' &&
      message.messageType !== 'COLLAB_CANCEL'
    ) {
      continue;
    }
    map.set(collabId, status);
  }
  return map;
}

export function findActiveCollabFromHistory(
  messages: ChatMessageResponse[],
): { collabId: string; document: NonNullable<ChatMessageResponse['document']> } | null {
  const latestStatus = new Map<string, string>();
  const documents = new Map<string, NonNullable<ChatMessageResponse['document']>>();

  for (const message of messages) {
    const collabId = message.collab?.collabId;
    const status = message.collab?.status;
    if (!collabId || !status) continue;
    if (
      message.messageType !== 'COLLAB_REQUEST' &&
      message.messageType !== 'COLLAB_STATUS' &&
      message.messageType !== 'COLLAB_CANCEL'
    ) {
      continue;
    }
    latestStatus.set(collabId, status);
    if (message.document) {
      documents.set(collabId, message.document);
    }
  }

  for (const [collabId, status] of latestStatus) {
    if (status !== 'ACTIVE') continue;
    const document = documents.get(collabId);
    if (document) {
      return { collabId, document };
    }
  }
  return null;
}
