import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  acceptCall,
  listWaitingCalls,
  type SessionResponse,
} from '../api/virtualBranchApi';
import {
  formatWaitDuration,
  getAgentDisplayName,
  getAgentIdentity,
  initialsFromName,
} from '../agentIdentity';
import { useAgentChrome } from '../layout/AgentChromeContext';

const QUEUE_POLL_MS = 3000;
const WAIT_TICK_MS = 1000;

export function AgentQueuePage() {
  const navigate = useNavigate();
  const { setPageTitle, setRecordingState, setHeaderExtra } = useAgentChrome();
  const [queue, setQueue] = useState<SessionResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filter, setFilter] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);
  const [, setTick] = useState(0);

  useEffect(() => {
    setPageTitle('Hàng đợi');
    setRecordingState('idle');
    setHeaderExtra(null);
  }, [setPageTitle, setRecordingState, setHeaderExtra]);

  useEffect(() => {
    const id = setInterval(() => setTick((n) => n + 1), WAIT_TICK_MS);
    return () => clearInterval(id);
  }, []);

  const refreshQueue = useCallback(async () => {
    try {
      const waiting = await listWaitingCalls();
      setQueue(waiting);
      setPageError(null);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không tải được hàng chờ cuộc gọi.');
    }
  }, []);

  useEffect(() => {
    void refreshQueue();
    const id = setInterval(() => void refreshQueue(), QUEUE_POLL_MS);
    return () => clearInterval(id);
  }, [refreshQueue]);

  useEffect(() => {
    if (selectedId && !queue.some((c) => c.sessionId === selectedId)) {
      setSelectedId(null);
    }
  }, [queue, selectedId]);

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return queue;
    return queue.filter((call) => {
      const name = (call.customerName ?? '').toLowerCase();
      return name.includes(q) || call.sessionId.toLowerCase().includes(q);
    });
  }, [queue, filter]);

  const selected = filtered.find((c) => c.sessionId === selectedId) ?? null;

  async function handleAccept(call: SessionResponse) {
    setAcceptingId(call.sessionId);
    setPageError(null);
    try {
      await acceptCall(call.sessionId, getAgentIdentity(), getAgentDisplayName());
      navigate(`/agent/call/${call.sessionId}`);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không nhận được cuộc gọi.');
      setAcceptingId(null);
    }
  }

  function handleSkip() {
    if (!selected) return;
    const idx = filtered.findIndex((c) => c.sessionId === selected.sessionId);
    const next = filtered[idx + 1] ?? filtered[idx - 1] ?? null;
    setSelectedId(next?.sessionId ?? null);
  }

  return (
    <div className="vb-queue">
      <section className="vb-queue-list">
        <div className="vb-queue-list-head">
          <div>
            <h2>Hàng đợi</h2>
            <p className="vb-queue-today">Hôm nay: {queue.length} cuộc gọi đang chờ</p>
          </div>
        </div>

        <div className="vb-tabs">
          <button type="button" className="vb-tab active">
            Tất cả <span className="vb-tab-count">{queue.length}</span>
          </button>
          <button type="button" className="vb-tab" disabled>
            Chưa xem
          </button>
        </div>

        <div className="vb-queue-filter">
          <span aria-hidden>⌕</span>
          <input
            type="search"
            placeholder="Tìm trong hàng đợi"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>

        {filtered.length === 0 ? (
          <div className="vb-queue-empty">
            <p>Chưa có cuộc gọi trong hàng chờ.</p>
          </div>
        ) : (
          <ul className="vb-queue-items">
            {filtered.map((call) => {
              const name = call.customerName ?? 'Khách hàng';
              const active = call.sessionId === selectedId;
              return (
                <li key={call.sessionId}>
                  <button
                    type="button"
                    className={active ? 'vb-queue-item active' : 'vb-queue-item'}
                    onClick={() => setSelectedId(call.sessionId)}
                  >
                    <span className="vb-queue-avatar">{initialsFromName(name)}</span>
                    <span className="vb-queue-item-body">
                      <span className="vb-queue-item-top">
                        <strong>{name}</strong>
                        <time>{formatWaitDuration(call.createdAt)}</time>
                      </span>
                      <span className="vb-queue-item-desc">
                        Yêu cầu video call · {new Date(call.createdAt).toLocaleTimeString('vi-VN')}
                      </span>
                      <span className="vb-pill-new">Mới</span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="vb-queue-detail">
        {!selected ? (
          <div className="vb-welcome">
            <h2>Chào mừng đến với Virtual Branch</h2>
            <p className="muted">
              Chọn một yêu cầu ở danh sách bên trái để xem chi tiết và nhận cuộc gọi.
            </p>
            <div className="vb-welcome-art" aria-hidden>
              <div className="vb-welcome-headset">☎</div>
            </div>
          </div>
        ) : (
          <div className="vb-detail-card">
            <div className="vb-detail-bar">
              <h2>Chi tiết yêu cầu</h2>
              <span className="vb-status-pill">Đang chờ</span>
            </div>

            <div className="vb-detail-customer">
              <span className="vb-queue-avatar lg">
                {initialsFromName(selected.customerName ?? 'KH')}
              </span>
              <div>
                <strong>{selected.customerName ?? 'Khách hàng'}</strong>
                <p className="muted">{selected.customerIdentity ?? selected.sessionId}</p>
              </div>
            </div>

            <p className="vb-channel">Kênh: Mobile / eFAST</p>

            <div className="vb-detail-stats">
              <div className="vb-stat">
                <span className="label">Loại yêu cầu</span>
                <strong>Video Call</strong>
              </div>
              <div className="vb-stat">
                <span className="label">Thời gian chờ</span>
                <strong>{formatWaitDuration(selected.createdAt)}</strong>
              </div>
            </div>

            <div className="vb-detail-actions">
              <button type="button" className="vb-btn-secondary" onClick={handleSkip}>
                Bỏ qua
              </button>
              <button
                type="button"
                className="vb-btn-primary"
                onClick={() => void handleAccept(selected)}
                disabled={acceptingId != null}
              >
                {acceptingId === selected.sessionId ? 'Đang nhận…' : 'Nhận yêu cầu'}
              </button>
            </div>
          </div>
        )}
      </section>

      {pageError && <div className="error-banner vb-queue-error">{pageError}</div>}
    </div>
  );
}
