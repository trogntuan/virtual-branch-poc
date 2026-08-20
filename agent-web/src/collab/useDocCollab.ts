import { useCallback, useEffect, useRef, useState } from 'react';
import { ConnectionState, Room, RoomEvent } from 'livekit-client';
import {
  DOC_COLLAB_TOPIC,
  DEFAULT_VIEW_STATE,
  RELIABLE_EVENTS,
  type DocCollabEvent,
  type DocCollabViewState,
} from './events';

interface UseDocCollabOptions {
  room: Room | null;
  role: 'AGENT' | 'CUSTOMER';
  sessionId: string | null;
}

async function waitForRoomConnected(room: Room, timeoutMs = 8000): Promise<void> {
  if (room.state === ConnectionState.Connected) return;

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error('LiveKit chưa kết nối xong để gửi Doc Collab.'));
    }, timeoutMs);

    const onConnected = () => {
      cleanup();
      resolve();
    };

    const cleanup = () => {
      clearTimeout(timer);
      room.off(RoomEvent.Connected, onConnected);
    };

    room.on(RoomEvent.Connected, onConnected);
    // Re-check asynchronously; TS narrows room.state after the early return above.
    queueMicrotask(() => {
      if (room.state === ConnectionState.Connected) {
        onConnected();
      }
    });
  });
}

export function useDocCollab({ room, role, sessionId }: UseDocCollabOptions) {
  const [viewState, setViewState] = useState<DocCollabViewState>(DEFAULT_VIEW_STATE);
  const [pendingRequest, setPendingRequest] = useState<DocCollabEvent<{ fileName: string }> | null>(null);
  const [collabEnded, setCollabEnded] = useState(false);
  const sequenceRef = useRef(0);
  const lastSequenceRef = useRef(0);
  const pendingCollabIdRef = useRef<string | null>(null);

  const publish = useCallback(
    async (event: Omit<DocCollabEvent, 'sequence' | 'timestamp' | 'version'>) => {
      if (role !== 'AGENT') return;
      if (!room) {
        throw new Error('Chưa kết nối LiveKit — không gửi được sự kiện Doc Collab.');
      }

      await waitForRoomConnected(room);

      sequenceRef.current += 1;
      const payload: DocCollabEvent = {
        version: 1,
        sequence: sequenceRef.current,
        timestamp: Date.now(),
        ...event,
      };

      const reliable = RELIABLE_EVENTS.has(event.type);
      const encoded = new TextEncoder().encode(JSON.stringify(payload));

      let lastError: unknown;
      for (let attempt = 1; attempt <= 3; attempt += 1) {
        try {
          await room.localParticipant.publishData(encoded, {
            reliable,
            topic: DOC_COLLAB_TOPIC,
          });
          return;
        } catch (cause) {
          lastError = cause;
          await new Promise((r) => setTimeout(r, 150 * attempt));
        }
      }
      throw lastError instanceof Error
        ? lastError
        : new Error('Không gửi được sự kiện Doc Collab qua Data Channel.');
    },
    [room, role],
  );

  const applyEvent = useCallback(
    (event: DocCollabEvent) => {
      if (sessionId && event.sessionId !== sessionId) return;

      // COLLAB_REQUEST must not be blocked by sequence (agent remount resets sequence).
      if (event.type !== 'COLLAB_REQUEST') {
        if (event.sequence <= lastSequenceRef.current && RELIABLE_EVENTS.has(event.type)) {
          return;
        }
        if (RELIABLE_EVENTS.has(event.type)) {
          lastSequenceRef.current = event.sequence;
        }
      } else if (event.sequence > lastSequenceRef.current) {
        lastSequenceRef.current = event.sequence;
      }

      switch (event.type) {
        case 'COLLAB_REQUEST':
          if (role === 'CUSTOMER') {
            // Keep showing consent; allow refresh of same/newer request payload.
            pendingCollabIdRef.current = event.collabId;
            setPendingRequest(event as DocCollabEvent<{ fileName: string }>);
            setCollabEnded(false);
          }
          break;
        case 'DOC_STATE': {
          const data = event.data as Partial<DocCollabViewState>;
          setViewState((prev) => ({
            ...prev,
            ...data,
            pointer: { ...prev.pointer, ...(data.pointer ?? {}) },
            highlight: { ...prev.highlight, ...(data.highlight ?? {}) },
          }));
          break;
        }
        case 'PAGE_CHANGE': {
          const data = event.data as { page: number };
          setViewState((prev) => ({ ...prev, page: data.page }));
          break;
        }
        case 'VIEWPORT_CHANGE': {
          const data = event.data as Partial<DocCollabViewState> & { page?: number; scrollRatio?: number };
          setViewState((prev) => ({
            ...prev,
            page: data.page ?? prev.page,
            scrollRatio: data.scrollRatio ?? prev.scrollRatio,
            viewMode: data.viewMode ?? prev.viewMode,
            zoomScale: data.zoomScale ?? prev.zoomScale,
          }));
          break;
        }
        case 'POINTER_MOVE': {
          const data = event.data as { page: number; visible: boolean; x: number; y: number };
          setViewState((prev) => ({
            ...prev,
            pointer: { visible: data.visible, page: data.page, x: data.x, y: data.y },
          }));
          break;
        }
        case 'POINTER_HIDE':
          setViewState((prev) => ({ ...prev, pointer: { ...prev.pointer, visible: false } }));
          break;
        case 'HIGHLIGHT_SET': {
          const data = event.data as { page: number; x: number; y: number; width: number; height: number };
          setViewState((prev) => ({
            ...prev,
            highlight: {
              visible: true,
              page: data.page,
              x: data.x,
              y: data.y,
              width: data.width,
              height: data.height,
            },
          }));
          break;
        }
        case 'HIGHLIGHT_CLEAR':
          setViewState((prev) => ({
            ...prev,
            highlight: { visible: false, page: prev.page, x: 0, y: 0, width: 0, height: 0 },
          }));
          break;
        case 'COLLAB_END':
          setCollabEnded(true);
          pendingCollabIdRef.current = null;
          setPendingRequest(null);
          setViewState(DEFAULT_VIEW_STATE);
          break;
        default:
          break;
      }
    },
    [role, sessionId],
  );

  useEffect(() => {
    if (!room) return;

    const onData = (
      payload: Uint8Array,
      _participant: unknown,
      _kind: unknown,
      topic?: string,
    ) => {
      if (topic != null && topic !== DOC_COLLAB_TOPIC) return;
      try {
        const event = JSON.parse(new TextDecoder().decode(payload)) as DocCollabEvent;
        if (!event?.type || !event?.sessionId) return;
        applyEvent(event);
      } catch {
        // ignore malformed payloads
      }
    };

    room.on(RoomEvent.DataReceived, onData);
    return () => {
      room.off(RoomEvent.DataReceived, onData);
    };
  }, [room, applyEvent]);

  const sendCollabRequest = useCallback(
    async (collabId: string, documentId: string, fileName: string) => {
      await publish({
        type: 'COLLAB_REQUEST',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: { fileName },
      });
    },
    [publish, sessionId],
  );

  const sendDocState = useCallback(
    async (collabId: string, documentId: string, state: DocCollabViewState) => {
      await publish({
        type: 'DOC_STATE',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: state,
      });
    },
    [publish, sessionId],
  );

  const sendPageChange = useCallback(
    async (collabId: string, documentId: string, page: number) => {
      setViewState((prev) => ({ ...prev, page }));
      await publish({
        type: 'PAGE_CHANGE',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: { page },
      });
    },
    [publish, sessionId],
  );

  const sendPointerMove = useCallback(
    async (collabId: string, documentId: string, page: number, x: number, y: number) => {
      setViewState((prev) => ({
        ...prev,
        pointer: { visible: true, page, x, y },
      }));
      await publish({
        type: 'POINTER_MOVE',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: { page, visible: true, x, y },
      });
    },
    [publish, sessionId],
  );

  const sendHighlightSet = useCallback(
    async (
      collabId: string,
      documentId: string,
      page: number,
      x: number,
      y: number,
      width: number,
      height: number,
    ) => {
      const highlight = { visible: true, page, x, y, width, height };
      setViewState((prev) => ({ ...prev, highlight }));
      await publish({
        type: 'HIGHLIGHT_SET',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: { page, x, y, width, height },
      });
    },
    [publish, sessionId],
  );

  const sendHighlightClear = useCallback(
    async (collabId: string, documentId: string) => {
      setViewState((prev) => ({
        ...prev,
        highlight: { visible: false, page: prev.page, x: 0, y: 0, width: 0, height: 0 },
      }));
      await publish({
        type: 'HIGHLIGHT_CLEAR',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: {},
      });
    },
    [publish, sessionId],
  );

  const sendCollabEnd = useCallback(
    async (collabId: string, documentId: string) => {
      await publish({
        type: 'COLLAB_END',
        collabId,
        sessionId: sessionId ?? '',
        documentId,
        data: { reason: 'AGENT_ENDED' },
      });
      setCollabEnded(true);
    },
    [publish, sessionId],
  );

  const clearPendingRequest = useCallback(() => {
    pendingCollabIdRef.current = null;
    setPendingRequest(null);
  }, []);

  return {
    viewState,
    setViewState,
    pendingRequest,
    clearPendingRequest,
    collabEnded,
    sendCollabRequest,
    sendDocState,
    sendPageChange,
    sendPointerMove,
    sendHighlightSet,
    sendHighlightClear,
    sendCollabEnd,
  };
}
