import { useEffect, useRef, useState } from 'react';
import {
  getCollabDocumentUrl,
  getDocumentUrl,
  getSession,
  issueToken,
  requestCall,
  submitCollabConsent,
} from '../api/virtualBranchApi';
import { ConnectionBadge } from '../components/ConnectionBadge';
import { MediaControls } from '../components/MediaControls';
import { VideoTile } from '../components/VideoTile';
import { DocCollabViewer } from '../collab/DocCollabViewer';
import { useDocCollab } from '../collab/useDocCollab';
import { useLiveKitRoom } from '../livekit/useLiveKitRoom';
import { ChatPanel } from '../chat/ChatPanel';
import type { ChatMessage } from '../chat/types';

const DEFAULT_MOBILE_DISPLAY = {
  viewportWidth: 390,
  viewportHeight: 844,
  devicePixelRatio: 3,
  orientation: 'PORTRAIT',
};

const STATUS_POLL_MS = 2000;

type CallPhase = 'idle' | 'waiting' | 'in_call';

export function CustomerTestPage() {
  const [phase, setPhase] = useState<CallPhase>('idle');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [activeCollabId, setActiveCollabId] = useState<string | null>(null);
  const [consentBusy, setConsentBusy] = useState(false);
  const [chatCollabRequest, setChatCollabRequest] = useState<ChatMessage | null>(null);
  const identityRef = useRef(`customer-${crypto.randomUUID().slice(0, 8)}`);
  const prevConnectionRef = useRef<string>('DISCONNECTED');
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const {
    connect,
    disconnect,
    toggleMic,
    toggleCamera,
    room,
    connectionState,
    micEnabled,
    cameraEnabled,
    error: liveKitError,
    localVideoRef,
    remoteVideoRef,
  } = useLiveKitRoom();

  const {
    viewState,
    pendingRequest,
    clearPendingRequest,
    collabEnded,
  } = useDocCollab({
    room,
    role: 'CUSTOMER',
    sessionId,
  });

  const isConnected = connectionState === 'CONNECTED' || connectionState === 'RECONNECTING';
  const error = pageError ?? liveKitError;

  useEffect(() => {
    if (collabEnded) {
      setPdfUrl(null);
      setActiveCollabId(null);
    }
  }, [collabEnded]);

  useEffect(() => {
    const wasReconnect = prevConnectionRef.current !== 'CONNECTED' && connectionState === 'CONNECTED';
    prevConnectionRef.current = connectionState;

    if (!wasReconnect || !activeCollabId || collabEnded) {
      return;
    }

    void getCollabDocumentUrl(activeCollabId)
      .then((docUrl) => setPdfUrl(docUrl.readUrl))
      .catch((cause) => {
        setPageError(cause instanceof Error ? cause.message : 'Không tải lại được PDF sau khi kết nối lại.');
      });
  }, [connectionState, activeCollabId, collabEnded]);

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  function stopStatusPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  async function joinCall(activeSessionId: string) {
    const token = await issueToken(
      activeSessionId,
      identityRef.current,
      'Khách hàng',
      'CUSTOMER',
    );
    await connect(token.serverUrl, token.participantToken);
    setPhase('in_call');
  }

  function startWaitingPoll(activeSessionId: string) {
    stopStatusPolling();
    pollRef.current = setInterval(async () => {
      try {
        const session = await getSession(activeSessionId);
        if (session.status === 'ACTIVE') {
          stopStatusPolling();
          setBusy(true);
          try {
            await joinCall(activeSessionId);
          } catch (cause) {
            setPageError(cause instanceof Error ? cause.message : 'Không vào được cuộc gọi.');
            setPhase('idle');
            setSessionId(null);
          } finally {
            setBusy(false);
          }
        } else if (session.status === 'ENDED') {
          stopStatusPolling();
          setPhase('idle');
          setSessionId(null);
          setPageError('Tổng đài viên đã bỏ qua cuộc gọi.');
        }
      } catch {
        // keep polling
      }
    }, STATUS_POLL_MS);
  }

  async function handleRequestCall() {
    setBusy(true);
    setPageError(null);
    try {
      await disconnect();
      const session = await requestCall(
        identityRef.current,
        'Khách hàng',
        DEFAULT_MOBILE_DISPLAY,
      );
      setSessionId(session.sessionId);
      setPhase('waiting');
      startWaitingPoll(session.sessionId);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không yêu cầu được cuộc gọi.');
    } finally {
      setBusy(false);
    }
  }

  async function handleCancelWaiting() {
    stopStatusPolling();
    setPhase('idle');
    setSessionId(null);
    setPageError(null);
  }

  async function handleLeave() {
    setBusy(true);
    setPageError(null);
    try {
      await disconnect();
      stopStatusPolling();
      setPhase('idle');
      setSessionId(null);
      setPdfUrl(null);
      setActiveCollabId(null);
      setChatCollabRequest(null);
      clearPendingRequest();
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không rời được cuộc gọi.');
    } finally {
      setBusy(false);
    }
  }

  async function handleConsent(decision: 'ACCEPT' | 'REJECT') {
    const collabId = chatCollabRequest?.collab?.collabId ?? pendingRequest?.collabId;
    if (!collabId) return;
    setConsentBusy(true);
    setPageError(null);
    try {
      const result = await submitCollabConsent(collabId, decision);
      clearPendingRequest();
      setChatCollabRequest(null);

      if (decision === 'ACCEPT' && result.status === 'ACTIVE') {
        setActiveCollabId(collabId);
        const docUrl = await getCollabDocumentUrl(collabId);
        setPdfUrl(docUrl.readUrl);
      } else {
        setPdfUrl(null);
        setActiveCollabId(null);
      }
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không gửi được phản hồi chia sẻ tài liệu.');
    } finally {
      setConsentBusy(false);
    }
  }

  function handleChatCollabRequest(message: ChatMessage) {
    if (message.senderRole !== 'AGENT') return;
    setChatCollabRequest(message);
  }

  function handleChatCollabStatus(message: ChatMessage) {
    const collabInfo = message.collab;
    const docInfo = message.document;
    if (!collabInfo) return;

    if (collabInfo.status === 'ACTIVE' && docInfo) {
      setActiveCollabId(collabInfo.collabId);
      void getCollabDocumentUrl(collabInfo.collabId)
        .then((docUrl) => setPdfUrl(docUrl.readUrl))
        .catch(() => {
          void getDocumentUrl(docInfo.documentId)
            .then((fallback) => setPdfUrl(fallback.readUrl))
            .catch(() => undefined);
        });
      setChatCollabRequest(null);
      clearPendingRequest();
    }

    if (collabInfo.status === 'REJECTED' || collabInfo.status === 'ENDED') {
      setChatCollabRequest(null);
      if (collabInfo.status === 'ENDED') {
        setPdfUrl(null);
        setActiveCollabId(null);
      }
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Mô phỏng mobile</h1>
          <p className="muted">Yêu cầu cuộc gọi — tổng đài viên nhận từ hàng chờ</p>
        </div>
        <ConnectionBadge state={connectionState} />
      </header>

      <section className="join-panel">
        {phase === 'idle' && (
          <button type="button" onClick={() => void handleRequestCall()} disabled={busy}>
            {busy ? 'Đang gửi yêu cầu…' : 'Yêu cầu cuộc gọi'}
          </button>
        )}
        {phase === 'waiting' && (
          <>
            <p className="hint">
              Đang chờ tổng đài viên… <code>{sessionId}</code>
            </p>
            <button type="button" onClick={() => void handleCancelWaiting()} disabled={busy}>
              Hủy
            </button>
          </>
        )}
        {phase === 'in_call' && (
          <>
            <p className="hint">
              Đang trong cuộc gọi — <code>{sessionId}</code>
            </p>
            <button type="button" onClick={() => void handleLeave()} disabled={busy}>
              Rời cuộc gọi
            </button>
          </>
        )}
      </section>

      {(pendingRequest || chatCollabRequest) && (
        <section className="consent-panel">
          <h2 className="section-title">Yêu cầu chia sẻ tài liệu</h2>
          <p>
            Tổng đài viên muốn chia sẻ tài liệu:{' '}
            <strong>
              {chatCollabRequest?.document?.fileName ?? pendingRequest?.data.fileName}
            </strong>
          </p>
          <div className="controls-row">
            <button type="button" onClick={() => void handleConsent('REJECT')} disabled={consentBusy}>
              Từ chối
            </button>
            <button type="button" onClick={() => void handleConsent('ACCEPT')} disabled={consentBusy}>
              {consentBusy ? 'Đang xử lý…' : 'Đồng ý'}
            </button>
          </div>
        </section>
      )}

      {phase === 'in_call' && (
        <div className="vb-mobile-call">
          <div className={pdfUrl && !collabEnded ? 'call-stage call-stage--doc' : 'call-stage'}>
            {pdfUrl && !collabEnded && (
              <section className="call-stage-doc">
                <DocCollabViewer url={pdfUrl} mode="customer" viewState={viewState} />
              </section>
            )}

            <aside className="call-stage-video">
              <section className="video-grid">
                <VideoTile label="Tổng đài viên" videoRef={remoteVideoRef} />
                <VideoTile label="Khách hàng" videoRef={localVideoRef} muted />
              </section>
              <MediaControls
                micEnabled={micEnabled}
                cameraEnabled={cameraEnabled}
                onToggleMic={() => void toggleMic()}
                onToggleCamera={() => void toggleCamera()}
                disabled={!isConnected || busy}
              />
            </aside>
          </div>

          <ChatPanel
            sessionId={sessionId}
            identity={identityRef.current}
            name="Khách hàng"
            role="CUSTOMER"
            compact={Boolean(pdfUrl && !collabEnded)}
            enabled={isConnected}
            onCollabRequest={handleChatCollabRequest}
            onCollabStatus={handleChatCollabStatus}
            onError={(message) => setPageError(message)}
          />
        </div>
      )}

      {collabEnded && (
        <p className="hint">Tổng đài viên đã kết thúc chia sẻ tài liệu.</p>
      )}

      {connectionState === 'RECONNECTING' && activeCollabId && (
        <p className="hint">Đang kết nối lại… PDF sẽ tải lại khi kết nối thành công.</p>
      )}

      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
