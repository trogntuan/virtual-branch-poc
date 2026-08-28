import { useCallback, useEffect, useRef, useState } from 'react';
import { getChatHistory, getChatSettings, type ChatMessageResponse, type ChatSettingsResponse } from '../api/virtualBranchApi';
import { buildChatWsUrl, decodeDisplayName, newClientMessageId } from './utils';
import type {
  ChatMessage,
  ChatMessageType,
  ChatSenderRole,
  WsChatErrorEnvelope,
  WsChatMessageEnvelope,
} from './types';

export type ChatConnectionState = 'idle' | 'connecting' | 'connected' | 'disconnected';

export interface UseSessionChatOptions {
  sessionId: string | null;
  identity: string;
  name: string;
  role: ChatSenderRole;
  enabled?: boolean;
  onCollabRequest?: (message: ChatMessage) => void;
  onCollabStatus?: (message: ChatMessage) => void;
  onError?: (message: string) => void;
}

function mapRestMessage(raw: ChatMessageResponse): ChatMessage {
  return {
    messageId: raw.messageId,
    sentAt: raw.sentAt,
    senderRole: raw.senderRole,
    senderIdentity: raw.senderIdentity,
    senderName: raw.senderName
      ? decodeDisplayName(raw.senderName, raw.senderRole === 'AGENT' ? 'Tổng đài' : 'Khách hàng')
      : raw.senderName,
    messageType: raw.messageType as ChatMessage['messageType'],
    text: raw.text,
    document: raw.document,
    collab: raw.collab,
    clientMessageId: raw.clientMessageId,
  };
}

function mapWsMessage(envelope: WsChatMessageEnvelope): ChatMessage {
  const payload = envelope.payload ?? {};
  const document =
    envelope.messageType === 'FILE' ||
    envelope.messageType === 'COLLAB_REQUEST' ||
    envelope.messageType === 'COLLAB_STATUS' ||
    envelope.messageType === 'COLLAB_CANCEL'
      ? {
          documentId: String(payload.documentId ?? ''),
          fileName: String(payload.fileName ?? 'Tài liệu'),
          contentType: String(payload.contentType ?? 'application/octet-stream'),
          sizeBytes: Number(payload.sizeBytes ?? 0),
        }
      : null;

  const collab =
    envelope.messageType === 'COLLAB_REQUEST' ||
    envelope.messageType === 'COLLAB_STATUS' ||
    envelope.messageType === 'COLLAB_CANCEL'
      ? {
          collabId: String(payload.collabId ?? ''),
          status: String(payload.collabStatus ?? payload.status ?? ''),
          documentId: String(payload.documentId ?? document?.documentId ?? ''),
        }
      : null;

  return {
    messageId: envelope.messageId,
    sentAt: envelope.sentAt,
    senderRole: envelope.senderRole,
    senderIdentity: envelope.senderIdentity,
    senderName: envelope.senderName
      ? decodeDisplayName(
          envelope.senderName,
          envelope.senderRole === 'AGENT' ? 'Tổng đài' : 'Khách hàng',
        )
      : null,
    messageType: envelope.messageType,
    text: envelope.messageType === 'TEXT' ? String(payload.text ?? '') : null,
    document: document && document.documentId ? document : null,
    collab: collab && collab.collabId ? collab : null,
    clientMessageId: envelope.clientMessageId ?? null,
    payload,
  };
}

export function useSessionChat({
  sessionId,
  identity,
  name,
  role,
  enabled = true,
  onCollabRequest,
  onCollabStatus,
  onError,
}: UseSessionChatOptions) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [settings, setSettings] = useState<ChatSettingsResponse | null>(null);
  const [connectionState, setConnectionState] = useState<ChatConnectionState>('idle');
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
  const wsGenerationRef = useRef(0);
  const seenMessageIdsRef = useRef<Set<string>>(new Set());
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onCollabRequestRef = useRef(onCollabRequest);
  const onCollabStatusRef = useRef(onCollabStatus);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onCollabRequestRef.current = onCollabRequest;
    onCollabStatusRef.current = onCollabStatus;
    onErrorRef.current = onError;
  }, [onCollabRequest, onCollabStatus, onError]);

  const appendMessage = useCallback((message: ChatMessage) => {
    if (seenMessageIdsRef.current.has(message.messageId)) {
      return;
    }
    seenMessageIdsRef.current.add(message.messageId);

    setMessages((prev) => {
      if (prev.some((item) => item.messageId === message.messageId)) {
        return prev;
      }
      return [...prev, message].sort(
        (a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime(),
      );
    });

    if (message.messageType === 'COLLAB_REQUEST') {
      onCollabRequestRef.current?.(message);
    }
    if (message.messageType === 'COLLAB_STATUS') {
      onCollabStatusRef.current?.(message);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    if (!sessionId) return;
    const history = await getChatHistory(sessionId);
    const mapped = history.messages.map(mapRestMessage);
    seenMessageIdsRef.current = new Set(mapped.map((m) => m.messageId));
    setMessages(mapped);
    setHistoryLoaded(true);
  }, [sessionId]);

  const connectSocket = useCallback(() => {
    if (!sessionId || !enabled) return;

    if (socketRef.current) {
      socketRef.current.close();
      socketRef.current = null;
    }

    const generation = wsGenerationRef.current + 1;
    wsGenerationRef.current = generation;

    setConnectionState('connecting');
    const ws = new WebSocket(buildChatWsUrl(sessionId, identity, role, name));
    socketRef.current = ws;

    ws.onopen = () => {
      if (generation !== wsGenerationRef.current) return;
      setConnectionState('connected');
    };

    ws.onmessage = (event) => {
      if (generation !== wsGenerationRef.current) return;
      try {
        const data = JSON.parse(String(event.data)) as WsChatMessageEnvelope | WsChatErrorEnvelope;
        if (data.type === 'CHAT_MESSAGE') {
          appendMessage(mapWsMessage(data));
        } else if (data.type === 'CHAT_ERROR') {
          onErrorRef.current?.(`${data.code}: ${data.message}`);
        }
      } catch {
        // ignore malformed frames
      }
    };

    ws.onclose = () => {
      if (generation !== wsGenerationRef.current) return;
      setConnectionState('disconnected');
      socketRef.current = null;
      if (enabled && sessionId) {
        reconnectTimerRef.current = setTimeout(() => {
          connectSocket();
        }, 2000);
      }
    };

    ws.onerror = () => {
      if (generation !== wsGenerationRef.current) return;
      ws.close();
    };
  }, [appendMessage, enabled, identity, name, role, sessionId]);

  useEffect(() => {
    if (!sessionId || !enabled) {
      setMessages([]);
      seenMessageIdsRef.current = new Set();
      setHistoryLoaded(false);
      setConnectionState('idle');
      return;
    }

    let cancelled = false;
    void (async () => {
      try {
        const [chatSettings] = await Promise.all([getChatSettings(), loadHistory()]);
        if (!cancelled) {
          setSettings(chatSettings);
        }
      } catch (cause) {
        if (!cancelled) {
          onErrorRef.current?.(
            cause instanceof Error ? cause.message : 'Không tải được cài đặt chat.',
          );
        }
      }
    })();

    connectSocket();

    return () => {
      cancelled = true;
      wsGenerationRef.current += 1;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, [connectSocket, enabled, loadHistory, sessionId]);

  const sendWs = useCallback(
    (messageType: ChatMessageType, payload: Record<string, unknown>) => {
      const socket = socketRef.current;
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        throw new Error('Chat chưa kết nối.');
      }
      const clientMessageId = newClientMessageId();
      socket.send(
        JSON.stringify({
          version: 1,
          type: 'CHAT_SEND',
          clientMessageId,
          payload: { messageType, ...payload },
        }),
      );
      return clientMessageId;
    },
    [],
  );

  const sendText = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed) return;
      sendWs('TEXT', { text: trimmed });
    },
    [sendWs],
  );

  const sendFileMessage = useCallback(
    async (documentId: string) => {
      sendWs('FILE', { documentId });
    },
    [sendWs],
  );

  const sendCollabRequest = useCallback(
    async (documentId: string) => {
      sendWs('COLLAB_REQUEST', { documentId });
    },
    [sendWs],
  );

  const sendCollabCancel = useCallback(
    async (collabId: string) => {
      sendWs('COLLAB_CANCEL', { collabId });
    },
    [sendWs],
  );

  return {
    messages,
    settings,
    connectionState,
    historyLoaded,
    sendText,
    sendFileMessage,
    sendCollabRequest,
    sendCollabCancel,
  };
}
