import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  endSession,
  endDocCollab,
  getChatHistory,
  getDocumentUrl,
  getMobileDisplay,
  getDocCollab,
  getRecordingModeSetting,
  getSession,
  issueToken,
  type DocumentResponse,
  type DocCollabResponse,
  type MobileDisplayResponse,
  type RecordingResponse,
  type RecordingTrackResponse,
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
import { ChatPanel } from '../chat/ChatPanel';
import type { ChatMessage } from '../chat/types';
import { findActiveCollabFromHistory } from '../chat/utils';

const COLLAB_POLL_MS = 1000;

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

function sideLabel(side: string): string {
  if (side === 'AGENT') return 'Agent';
  if (side === 'CUSTOMER') return 'Khách hàng';
  return 'Gộp (composite)';
}

/** Dual luôn có 2 slot Agent + KH; composite thì 1 file. */
function buildPlaybackTracks(finalRecording: RecordingResponse | null): RecordingTrackResponse[] {
  if (!finalRecording) return [];

  if (finalRecording.mode === 'DUAL_PARTICIPANT') {
    const bySide = new Map(
      (finalRecording.tracks ?? []).map((track) => [track.side, track] as const),
    );
    return (['AGENT', 'CUSTOMER'] as const).map((side) => {
      const existing = bySide.get(side);
      if (existing) return existing;
      return {
        recordingId: `${finalRecording.recordingId}-${side.toLowerCase()}`,
        side,
        egressId: null,
        status: 'FAILED',
        objectKey: null,
        playbackUrl: null,
        errorMessage: 'Không có bản ghi phía này',
      };
    });
  }

  if (finalRecording.tracks?.length) {
    return finalRecording.tracks;
  }

  if (finalRecording.playbackUrl || finalRecording.status === 'FAILED') {
    return [
      {
        recordingId: finalRecording.recordingId,
        side: 'COMPOSITE',
        egressId: finalRecording.egressId,
        status: finalRecording.status,
        objectKey: finalRecording.objectKey,
        playbackUrl: finalRecording.playbackUrl,
        errorMessage: finalRecording.errorMessage,
      },
    ];
  }

  return [];
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
  const [collab, setCollab] = useState<DocCollabResponse | null>(null);
  const [collabBusy, setCollabBusy] = useState(false);
  const [mobileDisplay, setMobileDisplay] = useState<MobileDisplayResponse | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [postCallPhase, setPostCallPhase] = useState<PostCallPhase>(null);
  const [finalRecording, setFinalRecording] = useState<RecordingResponse | null>(null);

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const connectAttemptedRef = useRef(false);
  const autoRecordStartedRef = useRef(false);
  const collabRestoredRef = useRef(false);

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

  // Auto-start recording when connected. Dual mode waits for KH in room first.
  useEffect(() => {
    if (!sessionId || !isConnected || !room || autoRecordStartedRef.current || postCallPhase != null) {
      return;
    }
    autoRecordStartedRef.current = true;
    let cancelled = false;

    void (async () => {
      try {
        const setting = await getRecordingModeSetting();
        if (setting.mode === 'DUAL_PARTICIPANT') {
          const deadline = Date.now() + 25_000;
          while (!cancelled && Date.now() < deadline && room.remoteParticipants.size === 0) {
            await new Promise((resolve) => setTimeout(resolve, 500));
          }
        }
        if (cancelled) return;
        await startRecording(sessionId);
      } catch {
        if (!cancelled) {
          autoRecordStartedRef.current = false;
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [sessionId, isConnected, room, startRecording, postCallPhase]);

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
    collabRestoredRef.current = false;
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId || !isConnected || collabRestoredRef.current || postCallPhase != null) return;

    void (async () => {
      try {
        const history = await getChatHistory(sessionId);
        const active = findActiveCollabFromHistory(history.messages);
        if (!active) return;

        const status = await getDocCollab(active.collabId);
        if (status.status !== 'ACTIVE') return;

        const docUrl = await getDocumentUrl(active.document.documentId);
        setCollab({
          collabId: active.collabId,
          sessionId,
          documentId: active.document.documentId,
          status: 'ACTIVE',
          consentDecision: 'ACCEPT',
        });
        setDocument({
          documentId: active.document.documentId,
          fileName: active.document.fileName,
          contentType: active.document.contentType,
          size: active.document.sizeBytes,
          readUrl: docUrl.readUrl,
        });
        collabRestoredRef.current = true;
        await sendDocState(active.collabId, active.document.documentId, viewState);
      } catch {
        // ignore restore errors
      }
    })();
  }, [sessionId, isConnected, postCallPhase, sendDocState, viewState]);

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

  function startCollabPolling(collabId: string, documentId: string, fileName: string) {
    stopCollabPolling();
    let resendTick = 0;
    pollRef.current = setInterval(async () => {
      try {
        const status = await getDocCollab(collabId);
        setCollab(status);
        if (status.status === 'REQUESTED') {
          resendTick += 1;
          // Re-broadcast every ~2s while waiting so mobile still receives if first packet was missed.
          if (resendTick % 2 === 1) {
            await sendCollabRequest(collabId, documentId, fileName);
          }
        }
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
    setPostCallPhase('saving');
    setRecordingState('uploading');

    try {
      let finished: RecordingResponse | null = null;
      try {
        finished = await stopAndWaitRecording();
      } catch {
        // continue ending call even if recording fails
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

      setFinalRecording(finished);
      const playbackTracks = buildPlaybackTracks(finished);
      const hasPlayback = playbackTracks.some((track) => Boolean(track.playbackUrl));
      if (finished?.status === 'COMPLETED' && hasPlayback) {
        setPostCallPhase('ready');
      } else if (hasPlayback) {
        // Partial dual success (one side failed) still show playable tracks
        setPostCallPhase('ready');
      } else if (finished?.status === 'FAILED') {
        setPostCallPhase('failed');
      } else {
        setPostCallPhase(finished ? 'ready' : 'failed');
      }
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không kết thúc được cuộc gọi.');
      setPostCallPhase('failed');
    } finally {
      setBusy(false);
      setRecordingState('idle');
      setHeaderExtra(null);
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
      setDocument(null);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không kết thúc được chia sẻ tài liệu.');
    } finally {
      setCollabBusy(false);
    }
  }

  const handleChatCollabRequest = useCallback(
    (message: ChatMessage) => {
      if (message.senderIdentity !== getAgentIdentity()) return;
      const collabInfo = message.collab;
      const docInfo = message.document;
      if (!sessionId || !collabInfo || !docInfo) return;

      setCollab({
        collabId: collabInfo.collabId,
        sessionId,
        documentId: collabInfo.documentId,
        status: 'REQUESTED',
        consentDecision: null,
      });

      void (async () => {
        try {
          await sendCollabRequest(collabInfo.collabId, collabInfo.documentId, docInfo.fileName);
          await sendCollabRequest(collabInfo.collabId, collabInfo.documentId, docInfo.fileName);
          startCollabPolling(collabInfo.collabId, collabInfo.documentId, docInfo.fileName);
        } catch (cause) {
          setPageError(
            cause instanceof Error ? cause.message : 'Không gửi được sự kiện Doc Collab.',
          );
        }
      })();
    },
    [sessionId, sendCollabRequest],
  );

  const handleChatCollabStatus = useCallback(
    (message: ChatMessage) => {
      const collabInfo = message.collab;
      const docInfo = message.document;
      if (!sessionId || !collabInfo) return;

      setCollab({
        collabId: collabInfo.collabId,
        sessionId,
        documentId: collabInfo.documentId,
        status: collabInfo.status,
        consentDecision:
          collabInfo.status === 'ACTIVE'
            ? 'ACCEPT'
            : collabInfo.status === 'REJECTED'
              ? 'REJECT'
              : null,
      });

      if (collabInfo.status === 'ACTIVE' && docInfo) {
        void (async () => {
          try {
            const docUrl = await getDocumentUrl(docInfo.documentId);
            setDocument({
              documentId: docInfo.documentId,
              fileName: docInfo.fileName,
              contentType: docInfo.contentType,
              size: docInfo.sizeBytes,
              readUrl: docUrl.readUrl,
            });
            stopCollabPolling();
            await sendDocState(collabInfo.collabId, docInfo.documentId, viewState);
          } catch (cause) {
            setPageError(
              cause instanceof Error ? cause.message : 'Không tải được tài liệu chia sẻ.',
            );
          }
        })();
      }

      if (collabInfo.status === 'REJECTED' || collabInfo.status === 'ENDED') {
        stopCollabPolling();
        if (collabInfo.status === 'ENDED') {
          const endedDocumentId = docInfo?.documentId ?? collabInfo.documentId;
          void (async () => {
            if (endedDocumentId) {
              try {
                await sendCollabEnd(collabInfo.collabId, endedDocumentId);
              } catch {
                // ignore livekit cleanup errors
              }
            }
            setDocument(null);
          })();
        }
      }
    },
    [sessionId, sendDocState, sendCollabEnd, viewState],
  );

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
    const playbackTracks = buildPlaybackTracks(finalRecording);
    const playableCount = playbackTracks.filter((track) => Boolean(track.playbackUrl)).length;
    const isDual = finalRecording?.mode === 'DUAL_PARTICIPANT';

    return (
      <div className="vb-postcall">
        <div
          className={
            isDual ? 'vb-postcall-card vb-postcall-card--wide vb-postcall-card--dual' : 'vb-postcall-card vb-postcall-card--wide'
          }
        >
          <h2>{postCallPhase === 'ready' ? 'Cuộc gọi đã kết thúc' : 'Kết thúc cuộc gọi'}</h2>
          {playbackTracks.length > 0 ? (
            <>
              <p className="muted">
                {isDual
                  ? `Hai file tách riêng (${playableCount}/2 sẵn sàng).`
                  : 'Xem lại bản ghi bên dưới.'}
              </p>
              <div className={isDual ? 'vb-playback-grid vb-playback-grid--dual' : 'vb-playback-grid'}>
                {playbackTracks.map((track) => (
                  <div key={track.recordingId} className="vb-playback-item">
                    <strong>{sideLabel(track.side)}</strong>
                    {track.playbackUrl ? (
                      <video className="vb-playback" src={track.playbackUrl} controls playsInline />
                    ) : (
                      <div className="vb-playback-missing">
                        <p className="muted">
                          {track.errorMessage
                            ?? (track.status === 'FAILED'
                              ? 'Ghi phía này thất bại.'
                              : 'Chưa có file xem lại.')}
                        </p>
                      </div>
                    )}
                  </div>
                ))}
              </div>
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

        <ChatPanel
          sessionId={sessionId}
          identity={getAgentIdentity()}
          name={getAgentDisplayName()}
          role="AGENT"
          compact={docCollabActive}
          enabled={isConnected && postCallPhase == null}
          onCollabRequest={handleChatCollabRequest}
          onCollabStatus={handleChatCollabStatus}
          onError={(message) => setPageError(message)}
        />
        {recordingBusy && isConnected && (
          <p className="vb-upload-hint">Đang khởi tạo ghi hình…</p>
        )}
      </div>

      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
