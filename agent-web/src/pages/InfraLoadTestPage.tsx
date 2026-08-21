import { useCallback, useEffect, useRef, useState } from 'react';
import { Room } from 'livekit-client';
import {
  acceptCall,
  getRecording,
  issueToken,
  requestCall,
  startRecording,
  stopRecording,
  type RecordingResponse,
  type SessionResponse,
} from '../api/virtualBranchApi';
import {
  downloadLoadTestExcel,
  type LoadTestExportInput,
  type MetricSampleRow,
} from '../infra/exportLoadTestExcel';

const METRICS_POLL_MS = 4000;
const DEFAULT_MOBILE_DISPLAY = {
  viewportWidth: 390,
  viewportHeight: 844,
  devicePixelRatio: 3,
  orientation: 'PORTRAIT',
};

interface ContainerMetric {
  name: string;
  cpuPercent?: number | null;
  memUsage?: string;
  memPercent?: number | null;
  netIO?: string;
  blockIO?: string;
  error?: string;
  raw?: string;
}

interface LiveKitSfuMetric {
  name?: string;
  pid?: number;
  cpuPercent?: number | null;
  memPercent?: number | null;
  memUsage?: string;
  rssMb?: number;
  elapsed?: string;
  rooms?: number | null;
  participants?: number | null;
  packetsIn?: number | null;
  packetsOut?: number | null;
  packetsDropped?: number | null;
  bytesInHuman?: string | null;
  bytesOutHuman?: string | null;
  bytesTotalHuman?: string | null;
  prometheusOk?: boolean;
  prometheusError?: string;
  error?: string;
}

interface MetricsSnapshot {
  timestamp: string;
  livekit?: LiveKitSfuMetric;
  containers: ContainerMetric[];
}

type WorkerStatus = 'pending' | 'running' | 'recording' | 'stopping' | 'done' | 'failed';

interface CallWorkerResult {
  index: number;
  sessionId: string;
  recordingId?: string;
  status: WorkerStatus;
  error?: string;
  startedAt?: number;
  stoppedAt?: number;
  recording?: RecordingResponse;
  peakEgressCpu?: number;
}

const STATUS_LABEL: Record<WorkerStatus, string> = {
  pending: 'Chờ',
  running: 'Kết nối',
  recording: 'Đang ghi',
  stopping: 'Dừng & upload',
  done: 'Hoàn tất',
  failed: 'Lỗi',
};

async function fetchMetrics(): Promise<MetricsSnapshot> {
  const res = await fetch('/api/v1/infra/metrics');
  if (!res.ok) {
    throw new Error(`metrics ${res.status}`);
  }
  return (await res.json()) as MetricsSnapshot;
}

function egressCpu(snapshot: MetricsSnapshot | null): number | null {
  const row = snapshot?.containers.find((c) => c.name === 'vb-egress');
  return row?.cpuPercent ?? null;
}

function fmtCount(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '—';
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}k`;
  }
  return String(Math.round(value));
}

async function connectParticipant(
  sessionId: string,
  identity: string,
  name: string,
  role: 'AGENT' | 'CUSTOMER',
  enableMedia: boolean,
): Promise<Room> {
  const token = await issueToken(sessionId, identity, name, role);
  const room = new Room({ adaptiveStream: true, dynacast: true });
  await room.connect(token.serverUrl, token.participantToken);
  if (enableMedia) {
    try {
      await room.localParticipant.setMicrophoneEnabled(true);
      await room.localParticipant.setCameraEnabled(true);
    } catch {
      // continue without media
    }
  }
  return room;
}

export function InfraLoadTestPage() {
  const [concurrency, setConcurrency] = useState(5);
  const [durationSec, setDurationSec] = useState(45);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<MetricsSnapshot | null>(null);
  const [history, setHistory] = useState<
    Array<{ t: string; egressCpu: number | null; sfuCpu: number | null; minioNet?: string }>
  >([]);
  const [workers, setWorkers] = useState<CallWorkerResult[]>([]);
  const [summary, setSummary] = useState<string | null>(null);
  const [peakEgressCpu, setPeakEgressCpu] = useState(0);
  const [peakSfuCpu, setPeakSfuCpu] = useState(0);
  const [lastExport, setLastExport] = useState<LoadTestExportInput | null>(null);
  const [lastExportFile, setLastExportFile] = useState<string | null>(null);
  const roomsRef = useRef<Room[]>([]);
  const peakEgressRef = useRef(0);
  const peakSfuRef = useRef(0);
  const busyRef = useRef(false);
  const runSamplesRef = useRef<MetricSampleRow[]>([]);
  const metricsRef = useRef<MetricsSnapshot | null>(null);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const snap = await fetchMetrics();
        if (cancelled) return;
        setMetrics(snap);
        metricsRef.current = snap;
        const cpu = egressCpu(snap);
        if (cpu != null) {
          peakEgressRef.current = Math.max(peakEgressRef.current, cpu);
          setPeakEgressCpu(peakEgressRef.current);
        }
        const sfuCpu = snap.livekit?.cpuPercent ?? null;
        if (sfuCpu != null) {
          peakSfuRef.current = Math.max(peakSfuRef.current, sfuCpu);
          setPeakSfuCpu(peakSfuRef.current);
        }
        const minio = snap.containers.find((c) => c.name === 'vb-minio');
        const egress = snap.containers.find((c) => c.name === 'vb-egress');
        if (busyRef.current) {
          runSamplesRef.current.push({
            t: snap.timestamp,
            egressCpu: cpu,
            sfuCpu,
            minioNet: minio?.netIO,
            sfuRooms: snap.livekit?.rooms ?? null,
            sfuParticipants: snap.livekit?.participants ?? null,
            sfuMem: snap.livekit?.memUsage ?? null,
            egressMem: egress?.memUsage ?? null,
          });
        }
        setHistory((prev) => {
          const next = [
            ...prev,
            { t: snap.timestamp, egressCpu: cpu, sfuCpu, minioNet: minio?.netIO },
          ];
          return next.slice(-90);
        });
      } catch {
        // ignore while backend/docker unavailable
      }
    };
    void tick();
    const id = setInterval(() => void tick(), METRICS_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const cleanupRooms = useCallback(async () => {
    const rooms = [...roomsRef.current];
    roomsRef.current = [];
    await Promise.all(
      rooms.map(async (room) => {
        try {
          room.removeAllListeners();
          await room.disconnect();
        } catch {
          // ignore
        }
      }),
    );
  }, []);

  useEffect(() => {
    return () => {
      void cleanupRooms();
    };
  }, [cleanupRooms]);

  async function runWorker(index: number, recordSeconds: number): Promise<CallWorkerResult> {
    const result: CallWorkerResult = {
      index,
      sessionId: '',
      status: 'pending',
    };
    let agentRoom: Room | null = null;
    let customerRoom: Room | null = null;
    try {
      result.status = 'running';
      setWorkers((prev) => {
        const copy = [...prev];
        copy[index] = { ...result };
        return copy;
      });

      const waiting: SessionResponse = await requestCall(
        `load-cust-${index}-${crypto.randomUUID().slice(0, 6)}`,
        `Load Customer ${index + 1}`,
        DEFAULT_MOBILE_DISPLAY,
      );
      result.sessionId = waiting.sessionId;
      await acceptCall(waiting.sessionId, `load-agent-${index}`, `Load Agent ${index + 1}`);

      customerRoom = await connectParticipant(
        waiting.sessionId,
        `load-cust-${index}`,
        `Load Customer ${index + 1}`,
        'CUSTOMER',
        true,
      );
      agentRoom = await connectParticipant(
        waiting.sessionId,
        `load-agent-${index}`,
        `Load Agent ${index + 1}`,
        'AGENT',
        true,
      );
      roomsRef.current.push(customerRoom, agentRoom);

      await new Promise((r) => setTimeout(r, 800 + index * 1200));
      await new Promise((r) => setTimeout(r, 1500));

      let recording: RecordingResponse | null = null;
      let lastStartError: unknown;
      for (let attempt = 1; attempt <= 4; attempt += 1) {
        try {
          recording = await startRecording(waiting.sessionId);
          break;
        } catch (cause) {
          lastStartError = cause;
          const message = cause instanceof Error ? cause.message : String(cause);
          if (!message.includes('RECORDING_START_FAILED') && !message.includes('Egress')) {
            throw cause;
          }
          await new Promise((r) => setTimeout(r, 1500 * attempt));
        }
      }
      if (!recording) {
        throw lastStartError instanceof Error
          ? lastStartError
          : new Error('Failed to start recording after retries');
      }
      result.recordingId = recording.recordingId;
      result.status = 'recording';
      result.startedAt = Date.now();
      result.recording = recording;
      setWorkers((prev) => {
        const copy = [...prev];
        copy[index] = { ...result };
        return copy;
      });

      await new Promise((r) => setTimeout(r, recordSeconds * 1000));

      result.status = 'stopping';
      setWorkers((prev) => {
        const copy = [...prev];
        copy[index] = { ...result };
        return copy;
      });

      let finalRec = await stopRecording(recording.recordingId);
      const deadline = Date.now() + 180_000;
      while (
        Date.now() < deadline &&
        finalRec.status !== 'COMPLETED' &&
        finalRec.status !== 'FAILED'
      ) {
        await new Promise((r) => setTimeout(r, 2500));
        finalRec = await getRecording(recording.recordingId);
      }
      result.stoppedAt = Date.now();
      result.recording = finalRec;
      result.peakEgressCpu = peakEgressRef.current;
      result.status = finalRec.status === 'COMPLETED' ? 'done' : 'failed';
      if (finalRec.status === 'FAILED') {
        result.error = finalRec.errorMessage ?? 'Recording failed';
      }
    } catch (cause) {
      result.status = 'failed';
      result.error = cause instanceof Error ? cause.message : String(cause);
    } finally {
      if (agentRoom) {
        try {
          await agentRoom.disconnect();
        } catch {
          // ignore
        }
      }
      if (customerRoom) {
        try {
          await customerRoom.disconnect();
        } catch {
          // ignore
        }
      }
      setWorkers((prev) => {
        const copy = [...prev];
        copy[index] = { ...result };
        return copy;
      });
    }
    return result;
  }

  async function handleRun() {
    setBusy(true);
    busyRef.current = true;
    setError(null);
    setSummary(null);
    setLastExportFile(null);
    setPeakEgressCpu(0);
    setPeakSfuCpu(0);
    peakEgressRef.current = 0;
    peakSfuRef.current = 0;
    runSamplesRef.current = [];
    await cleanupRooms();
    const n = Math.min(5, Math.max(1, concurrency));
    setWorkers(
      Array.from({ length: n }, (_, index) => ({
        index,
        sessionId: '',
        status: 'pending' as const,
      })),
    );

    const startedAt = new Date().toISOString();
    try {
      const results = await Promise.all(
        Array.from({ length: n }, (_, index) => runWorker(index, durationSec)),
      );
      const ok = results.filter((r) => r.status === 'done').length;
      const failed = results.filter((r) => r.status === 'failed').length;
      const finishedAt = new Date().toISOString();
      const snap = metricsRef.current;
      const exportPayload: LoadTestExportInput = {
        startedAt,
        finishedAt,
        concurrency: n,
        durationSec,
        peakEgressCpu: peakEgressRef.current,
        peakSfuCpu: peakSfuRef.current,
        okCount: ok,
        failedCount: failed,
        workers: results,
        samples: [...runSamplesRef.current],
        finalContainers: snap?.containers,
        finalSfu: snap?.livekit
          ? {
              cpuPercent: snap.livekit.cpuPercent,
              memUsage: snap.livekit.memUsage ?? null,
              rooms: snap.livekit.rooms,
              participants: snap.livekit.participants,
              packetsIn: snap.livekit.packetsIn,
              packetsOut: snap.livekit.packetsOut,
              packetsDropped: snap.livekit.packetsDropped,
            }
          : undefined,
      };
      setLastExport(exportPayload);
      try {
        const fileName = downloadLoadTestExcel(exportPayload);
        setLastExportFile(fileName);
        setSummary(
          `${ok} thành công · ${failed} lỗi · peak egress ${peakEgressRef.current.toFixed(1)}% · peak SFU ${peakSfuRef.current.toFixed(1)}% · Excel: ${fileName}`,
        );
      } catch (exportError) {
        setSummary(
          `${ok} thành công · ${failed} lỗi · peak egress ${peakEgressRef.current.toFixed(1)}% · peak SFU ${peakSfuRef.current.toFixed(1)}%`,
        );
        setError(
          exportError instanceof Error
            ? `Đo xong nhưng xuất Excel lỗi: ${exportError.message}`
            : 'Đo xong nhưng xuất Excel lỗi',
        );
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Load test failed');
    } finally {
      busyRef.current = false;
      setBusy(false);
      await cleanupRooms();
    }
  }

  function handleReExport() {
    if (!lastExport) {
      return;
    }
    try {
      const fileName = downloadLoadTestExcel(lastExport);
      setLastExportFile(fileName);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Xuất Excel thất bại');
    }
  }

  const egress = metrics?.containers.find((c) => c.name === 'vb-egress');
  const minio = metrics?.containers.find((c) => c.name === 'vb-minio');
  const sfu = metrics?.livekit;
  const recentCpu = history
    .slice(-8)
    .map((h) => (h.egressCpu == null ? '—' : h.egressCpu.toFixed(0)))
    .join(' → ');
  const recentSfuCpu = history
    .slice(-8)
    .map((h) => (h.sfuCpu == null ? '—' : h.sfuCpu.toFixed(0)))
    .join(' → ');

  return (
    <div className="infra-page">
      <header className="infra-hero">
        <div className="infra-hero-text">
          <p className="infra-kicker">Virtual Branch</p>
          <h1>Đo lường hạ tầng</h1>
        </div>
        <div className="infra-toolbar">
          <label className="infra-field">
            <span>Song song</span>
            <input
              type="number"
              min={1}
              max={5}
              value={concurrency}
              disabled={busy}
              onChange={(e) => setConcurrency(Number(e.target.value) || 1)}
            />
          </label>
          <label className="infra-field">
            <span>Thời lượng (s)</span>
            <input
              type="number"
              min={15}
              max={300}
              value={durationSec}
              disabled={busy}
              onChange={(e) => setDurationSec(Number(e.target.value) || 45)}
            />
          </label>
          <button
            type="button"
            className="infra-run-btn"
            onClick={() => void handleRun()}
            disabled={busy}
          >
            {busy ? (
              <svg className="infra-run-icon infra-run-icon--spin" viewBox="0 0 24 24" aria-hidden>
                <path
                  fill="currentColor"
                  d="M12 4a8 8 0 1 0 8 8 1 1 0 1 1 2 0A10 10 0 1 1 12 2a1 1 0 1 1 0 2Z"
                />
              </svg>
            ) : (
              <svg className="infra-run-icon" viewBox="0 0 24 24" aria-hidden>
                <path
                  fill="currentColor"
                  d="M8.5 5.8a1.2 1.2 0 0 1 1.85-.98l9.2 5.7a1.2 1.2 0 0 1 0 2.06l-9.2 5.7A1.2 1.2 0 0 1 8.5 16.3V5.8Z"
                />
              </svg>
            )}
            <span>{busy ? 'Đang chạy…' : 'Bắt đầu đo'}</span>
          </button>
          <button
            type="button"
            className="infra-export-btn"
            onClick={handleReExport}
            disabled={busy || !lastExport}
            aria-label="Tải Excel kết quả đo"
            title={lastExportFile ? `Tải lại ${lastExportFile}` : 'Tải Excel kết quả lần đo gần nhất'}
          >
            <svg className="infra-export-icon" viewBox="0 0 24 24" aria-hidden>
              <path
                fill="currentColor"
                d="M12 3a1 1 0 0 1 1 1v9.586l2.293-2.293a1 1 0 1 1 1.414 1.414l-4 4a1 1 0 0 1-1.414 0l-4-4a1 1 0 1 1 1.414-1.414L11 13.586V4a1 1 0 0 1 1-1Z"
              />
              <path
                fill="currentColor"
                d="M5 18a1 1 0 0 1 1-1h12a1 1 0 1 1 0 2H6a1 1 0 0 1-1-1Z"
              />
            </svg>
          </button>
        </div>
      </header>

      <section className="infra-panel">
        <div className="infra-panel-head">
          <h2>LiveKit SFU</h2>
          {history.length > 0 && (
            <span className="infra-panel-meta">SFU CPU {recentSfuCpu}%</span>
          )}
        </div>
        <div className="infra-metrics-grid infra-metrics-grid--sfu">
          <article className="infra-metric-card infra-metric-card--accent">
            <span className="infra-metric-label">SFU CPU</span>
            <strong className="infra-metric-value">
              {sfu?.cpuPercent != null ? `${sfu.cpuPercent.toFixed(1)}%` : sfu?.error ?? '—'}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">SFU MEM</span>
            <strong className="infra-metric-value infra-metric-value--sm">
              {sfu?.memUsage ?? (sfu?.rssMb != null ? `${sfu.rssMb}MiB` : '—')}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">Rooms / Participants</span>
            <strong className="infra-metric-value infra-metric-value--sm">
              {sfu?.rooms != null || sfu?.participants != null
                ? `${sfu?.rooms ?? '—'} / ${sfu?.participants ?? '—'}`
                : sfu?.prometheusError ?? '—'}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">Packets in / out</span>
            <strong className="infra-metric-value infra-metric-value--sm">
              {sfu?.packetsIn != null || sfu?.packetsOut != null
                ? `${fmtCount(sfu?.packetsIn)} / ${fmtCount(sfu?.packetsOut)}`
                : '—'}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">SFU bytes / dropped</span>
            <strong className="infra-metric-value infra-metric-value--sm">
              {sfu?.bytesInHuman || sfu?.bytesOutHuman
                ? `${sfu?.bytesInHuman ?? '—'} / ${sfu?.bytesOutHuman ?? '—'}`
                : sfu?.packetsDropped != null
                  ? `drop ${fmtCount(sfu.packetsDropped)}`
                  : sfu?.bytesTotalHuman ?? '—'}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">Peak SFU CPU</span>
            <strong className="infra-metric-value">
              {peakSfuCpu ? `${peakSfuCpu.toFixed(1)}%` : '—'}
            </strong>
          </article>
        </div>
      </section>

      <section className="infra-panel">
        <div className="infra-panel-head">
          <h2>Egress / storage</h2>
        </div>
        <div className="infra-metrics-grid">
          <article className="infra-metric-card">
            <span className="infra-metric-label">Egress CPU</span>
            <strong className="infra-metric-value">
              {egress?.cpuPercent != null ? `${egress.cpuPercent.toFixed(1)}%` : egress?.error ?? '—'}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">Egress MEM</span>
            <strong className="infra-metric-value infra-metric-value--sm">
              {egress?.memUsage ?? '—'}
            </strong>
          </article>
          <article className="infra-metric-card">
            <span className="infra-metric-label">MinIO Net I/O</span>
            <strong className="infra-metric-value infra-metric-value--sm">
              {minio?.netIO ?? minio?.error ?? '—'}
            </strong>
          </article>
          <article className="infra-metric-card infra-metric-card--accent">
            <span className="infra-metric-label">Peak egress CPU</span>
            <strong className="infra-metric-value">
              {peakEgressCpu ? `${peakEgressCpu.toFixed(1)}%` : '—'}
            </strong>
          </article>
        </div>
      </section>

      <section className="infra-panel">
        <div className="infra-panel-head">
          <h2>Containers</h2>
          {history.length > 0 && (
            <span className="infra-panel-meta">
              {history.length} samples · CPU {recentCpu}%
            </span>
          )}
        </div>
        <div className="infra-table-wrap">
          <table className="infra-table">
            <thead>
              <tr>
                <th>Container</th>
                <th>CPU%</th>
                <th>Mem</th>
                <th>Mem%</th>
                <th>Net I/O</th>
                <th>Block I/O</th>
              </tr>
            </thead>
            <tbody>
              {(metrics?.containers ?? []).map((c) => (
                <tr key={c.name}>
                  <td>
                    <span className="infra-container-name">{c.name}</span>
                  </td>
                  <td>{c.error ? c.error : c.cpuPercent?.toFixed(1) ?? '—'}</td>
                  <td>{c.memUsage ?? '—'}</td>
                  <td>{c.memPercent?.toFixed(1) ?? '—'}</td>
                  <td>{c.netIO ?? '—'}</td>
                  <td>{c.blockIO ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="infra-panel">
        <div className="infra-panel-head">
          <h2>Workers</h2>
          {summary && <span className="infra-panel-meta">{summary}</span>}
        </div>
        {workers.length === 0 ? (
          <div className="infra-workers-empty">Chưa có worker — bấm Bắt đầu đo</div>
        ) : (
          <div className="infra-workers-grid">
            {workers.map((w) => (
              <article
                key={w.index}
                className={`infra-worker-card infra-worker-card--${w.status}`}
              >
                <div className="infra-worker-top">
                  <span className="infra-worker-index">#{w.index + 1}</span>
                  <span className={`infra-worker-status infra-worker-status--${w.status}`}>
                    {STATUS_LABEL[w.status]}
                  </span>
                </div>
                <div className="infra-worker-body">
                  <div className="infra-worker-row">
                    <span>Session</span>
                    <code>{w.sessionId || '…'}</code>
                  </div>
                  {w.recordingId && (
                    <div className="infra-worker-row">
                      <span>Recording</span>
                      <code>{w.recordingId}</code>
                    </div>
                  )}
                  {w.recording && (
                    <div className="infra-worker-row">
                      <span>Status</span>
                      <strong>{w.recording.status}</strong>
                    </div>
                  )}
                  {w.recording?.objectKey && (
                    <div className="infra-worker-row">
                      <span>Object</span>
                      <code className="infra-worker-object">{w.recording.objectKey}</code>
                    </div>
                  )}
                  {w.error && <p className="infra-worker-error">{w.error}</p>}
                </div>
                {w.recording?.playbackUrl && (
                  <a
                    className="infra-worker-link"
                    href={w.recording.playbackUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Xem MP4
                  </a>
                )}
              </article>
            ))}
          </div>
        )}
      </section>

      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
