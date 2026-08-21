import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  endSession,
  endDocCollab,
  getMobileDisplay,
  getDocCollab,
  getRecordingSetting,
  getSession,
  issueToken,
  startDocCollab,
  uploadDocument,
  type DocumentResponse,
  type DocCollabResponse,
  type MobileDisplayResponse,
  type RecordingResponse,
  type SessionResponse,
} from '../api/virtualBranchApi';
import {
  getAgentDisplayName,
  getAgentIdentity,
  initialsFromName,
} from '../agentIdentity';
import { MediaControls } from '../components/MediaControls';
import { DocCollabViewer } from '../collab/DocCollabViewer';
import { useDocCollab } from '../collab/useDocCollab';
import { useLiveKitRoom } from '../livekit/useLiveKitRoom';
import { useAgentChrome } from '../layout/AgentChromeContext';
import { useRecording } from '../recording/useRecording';
import { collabStatusLabel } from '../i18n/labels';
import demoContractPdfUrl from '../docs/Hợp đồng (demo).pdf?url';

const COLLAB_POLL_MS = 1000;
const DEMO_PDF_NAME = 'Hợp đồng (demo).pdf';
const DEMO_PDF_SIZE_BYTES = 179_631;

type PostCallPhase = null | 'saving' | 'ready' | 'failed';

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(0)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export function AgentCallPage() {
  const { sessionId: routeSessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const sessionId = routeSessionId ?? null;
  const { setPageTitle, setRecordingState, setHeaderExtra } = useAgentChrome();

  const [session, setSession] = useState<SessionResponse | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [uploadBusy, setUploadBusy] = useState(false);
  const [collab, setCollab] = useState<DocCollabResponse | null>(null);
  const [collabBusy, setCollabBusy] = useState(false);
  const [mobileDisplay, setMobileDisplay] = useState<MobileDisplayResponse | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [postCallPhase, setPostCallPhase] = useState<PostCallPhase>(null);
  const [finalRecording, setFinalRecording] = useState<RecordingResponse | null>(null);
  const [recordingEnabled, setRecordingEnabled] = useState(false);
  const [recordingSettingLoaded, setRecordingSettingLoaded] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const connectAttemptedRef = useRef(false);
  const autoRecordStartedRef = useRef(false);
  const collabRequestInFlightRef = useRef(false);

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
    recording,
    recordingError,
    recordingBusy,
    startRecording,
    stopAndWaitRecording,
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
    room,
    role: 'AGENT',
    sessionId,
    destinationIdentity: session?.customerIdentity ?? null,
  });

  const isConnected = connectionState === 'CONNECTED' || connectionState === 'RECONNECTING';
  const error = pageError ?? liveKitError ?? recordingError;

  const isRecordingActive =
    recording != null && !['COMPLETED', 'FAILED'].includes(recording.status);

  const docCollabActive = collab?.status === 'ACTIVE' && Boolean(document?.readUrl);
  const customerName = session?.customerName ?? 'Khách hàng';

  useEffect(() => {
    setPageTitle('Đang xử lý');
    return () => {
      setRecordingState('idle');
      setHeaderExtra(null);
    };
  }, [setPageTitle, setRecordingState, setHeaderExtra]);

  useEffect(() => {
    let cancelled = false;
    void getRecordingSetting()
      .then((setting) => {
        if (!cancelled) {
          setRecordingEnabled(setting.enabled);
          setRecordingSettingLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRecordingEnabled(false);
          setRecordingSettingLoaded(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (postCallPhase === 'saving') {
      setRecordingState('uploading');
    } else if (isRecordingActive && postCallPhase == null) {
      setRecordingState('recording');
    } else {
      setRecordingState('idle');
    }
  }, [isRecordingActive, postCallPhase, setRecordingState]);

  useEffect(() => {
    if (postCallPhase != null) {
      setHeaderExtra(null);
      return;
    }
    setHeaderExtra(
      <button
        type="button"
        className="vb-end-call-header"
        onClick={() => void handleEndSession()}
        disabled={busy || !sessionId}
      >
        ✕ Kết thúc cuộc gọi
      </button>,
    );
    // handleEndSession is stable enough via refs; intentional omit to avoid re-bind loop
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busy, sessionId, postCallPhase, setHeaderExtra]);

  useEffect(() => {
    if (!sessionId) return;
    void getSession(sessionId)
      .then(setSession)
      .catch(() => undefined);
  }, [sessionId]);

  useEffect(() => {
    if (!isConnected || postCallPhase != null) return;
    const id = setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => clearInterval(id);
  }, [isConnected, postCallPhase]);

  const connectToRoom = useCallback(async () => {
    if (!sessionId || connectAttemptedRef.current) return;
    connectAttemptedRef.current = true;
    setBusy(true);
    setPageError(null);
    try {
      const token = await issueToken(sessionId, getAgentIdentity(), getAgentDisplayName(), 'AGENT');
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

  // Auto-start recording when connected (gated by DB setting recording.enabled)
  useEffect(() => {
    if (!recordingSettingLoaded || !recordingEnabled) return;
    if (!sessionId || !isConnected || autoRecordStartedRef.current || postCallPhase != null) return;
    autoRecordStartedRef.current = true;
    void startRecording(sessionId).catch(() => {
      autoRecordStartedRef.current = false;
    });
  }, [sessionId, isConnected, startRecording, postCallPhase, recordingEnabled, recordingSettingLoaded]);

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
    if (!sessionId || postCallPhase != null) return;
    setBusy(true);
    setPageError(null);

    if (recordingEnabled) {
      setPostCallPhase('saving');
      setRecordingState('uploading');
    }

    try {
      let finished: RecordingResponse | null = null;
      if (recordingEnabled) {
        try {
          finished = await stopAndWaitRecording();
        } catch {
          // continue ending call even if recording fails
        }
      }

      if (collab && collab.status === 'ACTIVE') {
        try {
          await endDocCollab(collab.collabId);
          if (document) {
            await sendCollabEnd(collab.collabId, document.documentId);
          }
        } catch {
          // ignore
        }
      }
      stopCollabPolling();

      try {
        await endSession(sessionId);
      } catch {
        // still disconnect
      }
      await disconnect();

      if (!recordingEnabled) {
        goBackToQueue();
        return;
      }

      setFinalRecording(finished);
      if (finished?.status === 'COMPLETED' && finished.playbackUrl) {
        setPostCallPhase('ready');
      } else if (finished?.status === 'FAILED' || (finished && !finished.playbackUrl)) {
        setPostCallPhase(finished?.status === 'COMPLETED' ? 'ready' : 'failed');
      } else {
        setPostCallPhase(finished ? 'ready' : 'failed');
      }
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không kết thúc được cuộc gọi.');
      if (recordingEnabled) {
        setPostCallPhase('failed');
      } else {
        goBackToQueue();
      }
    } finally {
      setBusy(false);
      setRecordingState('idle');
      setHeaderExtra(null);
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

  async function handleSendDemoPdf() {
    if (!sessionId || !isConnected || uploadBusy || busy || document) return;
    setUploadBusy(true);
    setPageError(null);
    try {
      const response = await fetch(demoContractPdfUrl);
      if (!response.ok) {
        throw new Error('Không tải được file PDF mẫu.');
      }
      const blob = await response.blob();
      const file = new File([blob], DEMO_PDF_NAME, { type: 'application/pdf' });
      const uploaded = await uploadDocument(sessionId, file);
      setDocument(uploaded);
      setCollab(null);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không gửi được PDF mẫu.');
    } finally {
      setUploadBusy(false);
    }
  }

  async function handleRequestCollab() {
    if (!sessionId || !document) return;
    if (collabRequestInFlightRef.current || collabBusy) return;
    if (collab && (collab.status === 'REQUESTED' || collab.status === 'ACTIVE')) return;

    collabRequestInFlightRef.current = true;
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
      collabRequestInFlightRef.current = false;
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

  function goBackToQueue() {
    resetRecording();
    navigate('/agent');
  }

  if (postCallPhase === 'saving') {
    return (
      <div className="vb-postcall">
        <div className="vb-postcall-card">
          <div className="vb-spinner" />
          <h2>Đang lưu bản ghi cuộc gọi…</h2>
          <p className="muted">Vui lòng đợi video được tải lên storage.</p>
        </div>
      </div>
    );
  }

  if (postCallPhase === 'ready' || postCallPhase === 'failed') {
    return (
      <div className="vb-postcall">
        <div className="vb-postcall-card vb-postcall-card--wide">
          <h2>{postCallPhase === 'ready' ? 'Cuộc gọi đã kết thúc' : 'Kết thúc cuộc gọi'}</h2>
          {finalRecording?.playbackUrl ? (
            <>
              <p className="muted">Xem lại bản ghi bên dưới.</p>
              <video
                className="vb-playback"
                src={finalRecording.playbackUrl}
                controls
                playsInline
              />
            </>
          ) : (
            <p className="muted">
              {finalRecording?.status === 'FAILED'
                ? finalRecording.errorMessage ?? 'Lưu bản ghi thất bại.'
                : 'Không có URL xem lại (kiểm tra egress / storage local).'}
            </p>
          )}
          {error && <div className="error-banner">{error}</div>}
          <button type="button" className="vb-btn-primary" onClick={goBackToQueue}>
            Về hàng đợi
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="vb-call">
      <div className="vb-call-customer-bar">
        <span className="vb-queue-avatar">{initialsFromName(customerName)}</span>
        <div>
          <strong>{customerName}</strong>
          <p className="muted">
            {session?.customerIdentity ?? sessionId}
            {mobileDisplay?.viewportWidth
              ? ` · ${mobileDisplay.viewportWidth}×${mobileDisplay.viewportHeight}`
              : ''}
          </p>
        </div>
        <time className="vb-call-timer">{formatElapsed(elapsed)}</time>
      </div>

      <div className={docCollabActive ? 'vb-call-workspace vb-call-workspace--doc' : 'vb-call-workspace'}>
        <section className="vb-phone-stage">
          <div className="vb-phone">
            <div className="vb-phone-top">
              <span className="vb-phone-channel">Kênh: eFAST Mobile</span>
              <span className="vb-phone-status">
                {isConnected ? 'Đang gọi video' : busy ? 'Đang kết nối…' : 'Chưa kết nối'}
              </span>
            </div>

            <div className="vb-phone-video">
              <video
                ref={remoteVideoRef}
                autoPlay
                playsInline
                className="vb-phone-remote"
              />
              <div className="vb-phone-pip">
                <video ref={localVideoRef} autoPlay playsInline muted className="vb-phone-local" />
                {!cameraEnabled && (
                  <div className="vb-phone-pip-off" title="Camera đang tắt">
                    {initialsFromName(getAgentDisplayName())}
                  </div>
                )}
              </div>
            </div>

            <MediaControls
              micEnabled={micEnabled}
              cameraEnabled={cameraEnabled}
              onToggleMic={() => void toggleMic()}
              onToggleCamera={() => void toggleCamera()}
              onEndCall={() => void handleEndSession()}
              disabled={!isConnected || busy}
            />
          </div>
        </section>

        {docCollabActive && (
          <section className="vb-call-doc">
            <div className="vb-doc-filebar">
              <strong>{document!.fileName}</strong>
              <span className="muted">{formatBytes(document!.size)}</span>
            </div>
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
                className="vb-btn-secondary"
                onClick={() => {
                  void sendHighlightClear(collab!.collabId, document!.documentId);
                }}
              >
                Xóa vùng tô
              </button>
              <button
                type="button"
                className="vb-btn-secondary"
                onClick={() => void handleEndCollab()}
                disabled={collabBusy}
              >
                Kết thúc chia sẻ
              </button>
            </div>
          </section>
        )}

        <aside className="vb-chat-panel">
          <div className="vb-chat-scroll">
            <div className="vb-pdf-message">
              <div className="vb-pdf-bubble vb-pdf-bubble--demo">
                <div className="vb-pdf-icon">PDF</div>
                <div className="vb-pdf-bubble-body">
                  <strong>{DEMO_PDF_NAME}</strong>
                  <p className="muted">{formatBytes(DEMO_PDF_SIZE_BYTES)} · Mẫu sẵn</p>
                </div>
                <button
                  type="button"
                  className="vb-pdf-send-btn"
                  onClick={() => void handleSendDemoPdf()}
                  disabled={uploadBusy || busy || !isConnected || Boolean(document)}
                  title={
                    document
                      ? 'Đã gửi file — dùng Yêu cầu xem cùng bên dưới'
                      : 'Gửi PDF mẫu (upload + collab như thường)'
                  }
                >
                  {uploadBusy && !document ? 'Đang gửi…' : 'Gửi'}
                </button>
              </div>
            </div>

            {!document && (
              <div className="vb-chat-empty">
                <p>Ấn Gửi trên file mẫu, hoặc đính kèm PDF khác bên dưới.</p>
              </div>
            )}

            {document && (
              <div className="vb-pdf-message">
                <div className="vb-pdf-bubble">
                  <div className="vb-pdf-icon">PDF</div>
                  <div>
                    <strong>{document.fileName}</strong>
                    <p className="muted">{formatBytes(document.size)}</p>
                  </div>
                </div>
                {!collab && (
                  <button
                    type="button"
                    className="vb-collab-request-btn"
                    onClick={() => void handleRequestCollab()}
                    disabled={collabBusy || !isConnected}
                  >
                    {collabBusy ? 'Đang gửi…' : 'Yêu cầu xem cùng'}
                  </button>
                )}
                {collab &&
                  collab.status !== 'ENDED' &&
                  collab.status !== 'REJECTED' &&
                  collab.status !== 'ACTIVE' && (
                    <button
                      type="button"
                      className="vb-collab-request-btn vb-collab-request-btn--muted"
                      onClick={() => void handleEndCollab()}
                      disabled={collabBusy}
                    >
                      Hủy yêu cầu
                    </button>
                  )}
              </div>
            )}

            {collab && !docCollabActive && (
              <div className="vb-collab-card">
                <strong>Phiên Document Collab</strong>
                <p>
                  {collab.status === 'REQUESTED'
                    ? 'Đã gửi yêu cầu Collab — Chờ KH xác nhận'
                    : collabStatusLabel(collab.status)}
                </p>
              </div>
            )}
          </div>

          <div className="vb-chat-composer">
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf,.pdf"
              hidden
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void handleUploadPdf(file);
              }}
            />
            <button
              type="button"
              className="vb-attach-btn"
              title="Tải lên PDF"
              disabled={uploadBusy || busy || !isConnected}
              onClick={() => fileInputRef.current?.click()}
            >
              📎
            </button>
            <input
              type="text"
              placeholder="Chỉ hỗ trợ upload PDF…"
              disabled
              readOnly
            />
            <button type="button" className="vb-send-btn" disabled title="Không dùng chat">
              ➤
            </button>
          </div>
          {uploadBusy && <p className="vb-upload-hint">Đang tải PDF lên…</p>}
          {recordingEnabled && recordingBusy && isConnected && (
            <p className="vb-upload-hint">Đang khởi tạo ghi hình…</p>
          )}
        </aside>
      </div>

      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
