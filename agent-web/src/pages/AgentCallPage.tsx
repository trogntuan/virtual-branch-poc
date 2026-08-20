import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  endSession,
  endDocCollab,
  getMobileDisplay,
  getDocCollab,
  issueToken,
  startDocCollab,
  uploadDocument,
  type DocumentResponse,
  type DocCollabResponse,
  type MobileDisplayResponse,
} from '../api/virtualBranchApi';
import { ConnectionBadge } from '../components/ConnectionBadge';
import { MediaControls } from '../components/MediaControls';
import { VideoTile } from '../components/VideoTile';
import { DocCollabViewer } from '../collab/DocCollabViewer';
import { useDocCollab } from '../collab/useDocCollab';
import { useLiveKitRoom } from '../livekit/useLiveKitRoom';
import { useRecording } from '../recording/useRecording';
import { collabStatusLabel, orientationLabel, recordingStatusLabel } from '../i18n/labels';

const COLLAB_POLL_MS = 1000;

export function AgentCallPage() {
  const { sessionId: routeSessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const sessionId = routeSessionId ?? null;

  const [roomName, setRoomName] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [uploadBusy, setUploadBusy] = useState(false);
  const [collab, setCollab] = useState<DocCollabResponse | null>(null);
  const [collabBusy, setCollabBusy] = useState(false);
  const [mobileDisplay, setMobileDisplay] = useState<MobileDisplayResponse | null>(null);
  const identityRef = useRef(`agent-${crypto.randomUUID().slice(0, 8)}`);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const connectAttemptedRef = useRef(false);

  const {
    connect,
    disconnect,
    toggleMic,
    toggleCamera,
    connectionState,
    micEnabled,
    cameraEnabled,
    error: liveKitError,
    localVideoRef,
    remoteVideoRef,
    getRoom,
  } = useLiveKitRoom();

  const {
    recording,
    recordingError,
    recordingBusy,
    startRecording,
    stopRecording,
    resetRecording,
  } = useRecording();

  const {
    viewState,
    sendCollabRequest,
    sendDocState,
    sendPageChange,
    sendPointerMove,
    sendHighlightSet,
    sendHighlightClear,
    sendCollabEnd,
  } = useDocCollab({
    room: getRoom(),
    role: 'AGENT',
    sessionId,
  });

  const isConnected = connectionState === 'CONNECTED' || connectionState === 'RECONNECTING';
  const error = pageError ?? liveKitError ?? recordingError;

  const isRecording =
    recording != null &&
    !['COMPLETED', 'FAILED', 'STOPPING'].includes(recording.status) &&
    recording.status !== 'REQUESTED';

  const docCollabActive = collab?.status === 'ACTIVE' && Boolean(document?.readUrl);

  const connectToRoom = useCallback(async () => {
    if (!sessionId || connectAttemptedRef.current) return;
    connectAttemptedRef.current = true;
    setBusy(true);
    setPageError(null);
    try {
      const token = await issueToken(sessionId, identityRef.current, 'Tổng đài viên', 'AGENT');
      setRoomName(token.roomName);
      await connect(token.serverUrl, token.participantToken);
    } catch (cause) {
      connectAttemptedRef.current = false;
      setPageError(cause instanceof Error ? cause.message : 'Không kết nối được cuộc gọi.');
    } finally {
      setBusy(false);
    }
  }, [sessionId, connect]);

  useEffect(() => {
    if (!sessionId) {
      navigate('/agent', { replace: true });
      return;
    }
    void connectToRoom();
    return () => {
      void disconnect();
    };
  }, [sessionId, navigate, connectToRoom, disconnect]);

  useEffect(() => {
    if (!sessionId || !isConnected) return;

    const loadDisplay = () => {
      void getMobileDisplay(sessionId)
        .then(setMobileDisplay)
        .catch(() => undefined);
    };

    loadDisplay();
    const id = setInterval(loadDisplay, 5000);
    return () => clearInterval(id);
  }, [sessionId, isConnected]);

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  function stopCollabPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  function startCollabPolling(collabId: string, documentId: string) {
    stopCollabPolling();
    pollRef.current = setInterval(async () => {
      try {
        const status = await getDocCollab(collabId);
        setCollab(status);
        if (status.status === 'ACTIVE') {
          stopCollabPolling();
          await sendDocState(collabId, documentId, viewState);
        }
        if (status.status === 'REJECTED' || status.status === 'ENDED') {
          stopCollabPolling();
        }
      } catch {
        // keep polling
      }
    }, COLLAB_POLL_MS);
  }

  async function handleEndSession() {
    if (!sessionId) return;
    setBusy(true);
    setPageError(null);
    try {
      if (collab && collab.status === 'ACTIVE') {
        await endDocCollab(collab.collabId);
        if (document) {
          await sendCollabEnd(collab.collabId, document.documentId);
        }
      }
      stopCollabPolling();
      await endSession(sessionId);
      await disconnect();
      resetRecording();
      navigate('/agent');
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không kết thúc được cuộc gọi.');
    } finally {
      setBusy(false);
    }
  }

  async function handleUploadPdf(file: File) {
    if (!sessionId) return;
    setUploadBusy(true);
    setPageError(null);
    try {
      const uploaded = await uploadDocument(sessionId, file);
      setDocument(uploaded);
      setCollab(null);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không tải lên được PDF.');
    } finally {
      setUploadBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function handleRequestCollab() {
    if (!sessionId || !document) return;
    setCollabBusy(true);
    setPageError(null);
    try {
      const started = await startDocCollab(sessionId, document.documentId);
      setCollab(started);
      await sendCollabRequest(started.collabId, document.documentId, document.fileName);
      startCollabPolling(started.collabId, document.documentId);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không bắt đầu được chia sẻ tài liệu.');
    } finally {
      setCollabBusy(false);
    }
  }

  async function handleEndCollab() {
    if (!collab || !document) return;
    setCollabBusy(true);
    try {
      await endDocCollab(collab.collabId);
      await sendCollabEnd(collab.collabId, document.documentId);
      stopCollabPolling();
      setCollab(null);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không kết thúc được chia sẻ tài liệu.');
    } finally {
      setCollabBusy(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Cuộc gọi đang diễn ra</h1>
          <p className="muted">Ghi hình · Chia sẻ tài liệu</p>
        </div>
        <ConnectionBadge state={connectionState} />
      </header>

      <section className="info-panel">
        <div>
          <span className="label">Mã phiên</span>
          <code>{sessionId ?? '—'}</code>
        </div>
        <div>
          <span className="label">Phòng</span>
          <code>{roomName ?? '—'}</code>
        </div>
        {mobileDisplay?.viewportWidth && mobileDisplay.viewportHeight && (
          <div>
            <span className="label">Màn hình mobile</span>
            <code>
              {mobileDisplay.viewportWidth} x {mobileDisplay.viewportHeight}{' '}
              {orientationLabel(mobileDisplay.orientation ?? 'PORTRAIT')}
            </code>
          </div>
        )}
        {collab && (
          <div>
            <span className="label">Chia sẻ tài liệu</span>
            <code>
              {collabStatusLabel(collab.status)} ({collab.collabId})
            </code>
          </div>
        )}
      </section>

      <div className={docCollabActive ? 'call-stage call-stage--doc' : 'call-stage'}>
        {docCollabActive && (
          <section className="call-stage-doc">
            <DocCollabViewer
              url={document!.readUrl}
              mode="agent"
              viewState={viewState}
              onPageChange={(page) => {
                void sendPageChange(collab!.collabId, document!.documentId, page);
              }}
              onPointerMove={(page, x, y) => {
                void sendPointerMove(collab!.collabId, document!.documentId, page, x, y);
              }}
              onHighlightSelect={(page, x, y, width, height) => {
                void sendHighlightSet(collab!.collabId, document!.documentId, page, x, y, width, height);
              }}
            />
            <div className="controls-row">
              <button
                type="button"
                onClick={() => {
                  void sendHighlightClear(collab!.collabId, document!.documentId);
                }}
              >
                Xóa vùng tô
              </button>
              <button
                type="button"
                onClick={() => {
                  void sendDocState(collab!.collabId, document!.documentId, viewState);
                }}
              >
                Đồng bộ lại
              </button>
              <button type="button" onClick={() => void handleEndCollab()} disabled={collabBusy}>
                Kết thúc chia sẻ
              </button>
            </div>
          </section>
        )}

        <aside className="call-stage-video">
          <section className="video-grid">
            <VideoTile label="Khách hàng" videoRef={remoteVideoRef} />
            <VideoTile label="Tổng đài viên" videoRef={localVideoRef} muted />
          </section>
          <MediaControls
            micEnabled={micEnabled}
            cameraEnabled={cameraEnabled}
            onToggleMic={() => void toggleMic()}
            onToggleCamera={() => void toggleCamera()}
            disabled={!isConnected || busy}
            extra={
              <>
                {(!recording || ['COMPLETED', 'FAILED'].includes(recording.status)) && !isRecording ? (
                  <button
                    type="button"
                    onClick={() => sessionId && void startRecording(sessionId)}
                    disabled={recordingBusy || busy || !isConnected}
                  >
                    {recordingBusy ? 'Đang bắt đầu ghi…' : recording ? 'Ghi mới' : 'Bắt đầu ghi'}
                  </button>
                ) : isRecording ? (
                  <button type="button" onClick={() => void stopRecording()} disabled={recordingBusy || busy}>
                    {recordingBusy ? 'Đang dừng ghi…' : 'Dừng ghi'}
                  </button>
                ) : null}
                <button type="button" onClick={() => void handleEndSession()} disabled={busy}>
                  Kết thúc cuộc gọi
                </button>
              </>
            }
          />
        </aside>
      </div>

      {isConnected && sessionId && !docCollabActive && (
        <section className="info-panel">
          <div>
            <span className="label">Tài liệu PDF</span>
            <div className="controls-row">
              <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf,.pdf"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void handleUploadPdf(file);
                }}
                disabled={uploadBusy || busy}
              />
              {document && !collab && (
                <button type="button" onClick={() => void handleRequestCollab()} disabled={collabBusy}>
                  {collabBusy ? 'Đang gửi yêu cầu…' : 'Yêu cầu chia sẻ tài liệu'}
                </button>
              )}
              {collab && collab.status !== 'ENDED' && collab.status !== 'REJECTED' && collab.status !== 'ACTIVE' && (
                <button type="button" onClick={() => void handleEndCollab()} disabled={collabBusy}>
                  Kết thúc chia sẻ
                </button>
              )}
              {uploadBusy && <span className="muted">Đang tải lên…</span>}
            </div>
          </div>
        </section>
      )}

      {error && <div className="error-banner">{error}</div>}

      {recording && (
        <section className="info-panel">
          <div>
            <span className="label">Bản ghi</span>
            <code>{recording.recordingId}</code>
          </div>
          <div>
            <span className="label">Trạng thái</span>
            <code>{recordingStatusLabel(recording.status)}</code>
          </div>
          {recording.playbackUrl && (
            <div>
              <span className="label">Xem lại</span>
              <a href={recording.playbackUrl} target="_blank" rel="noreferrer">
                Mở file MP4
              </a>
            </div>
          )}
          {recording.status === 'FAILED' && recording.errorMessage && (
            <div>
              <span className="label">Lỗi</span>
              <code>{recording.errorMessage}</code>
            </div>
          )}
        </section>
      )}

      {!isConnected && !pageError && (
        <p className="hint">{busy ? 'Đang vào cuộc gọi…' : 'Đang chuẩn bị kết nối…'}</p>
      )}
    </div>
  );
}
