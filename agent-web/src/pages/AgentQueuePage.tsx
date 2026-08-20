import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  acceptCall,
  listWaitingCalls,
  type SessionResponse,
} from '../api/virtualBranchApi';

const QUEUE_POLL_MS = 3000;

export function AgentQueuePage() {
  const navigate = useNavigate();
  const [queue, setQueue] = useState<SessionResponse[]>([]);
  const [pageError, setPageError] = useState<string | null>(null);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);
  const identityRef = useRef(`agent-${crypto.randomUUID().slice(0, 8)}`);

  const refreshQueue = useCallback(async () => {
    try {
      const waiting = await listWaitingCalls();
      setQueue(waiting);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không tải được hàng chờ cuộc gọi.');
    }
  }, []);

  useEffect(() => {
    void refreshQueue();
    const id = setInterval(() => void refreshQueue(), QUEUE_POLL_MS);
    return () => clearInterval(id);
  }, [refreshQueue]);

  async function handleAccept(call: SessionResponse) {
    setAcceptingId(call.sessionId);
    setPageError(null);
    try {
      await acceptCall(call.sessionId, identityRef.current, 'Tổng đài viên');
      navigate(`/agent/call/${call.sessionId}`);
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : 'Không nhận được cuộc gọi.');
      setAcceptingId(null);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Bảng tổng đài</h1>
          <p className="muted">Cuộc gọi từ mobile — nhận để bắt đầu</p>
        </div>
        <span className="queue-badge">{queue.length} đang chờ</span>
      </header>

      {queue.length === 0 ? (
        <section className="queue-empty">
          <p>Chưa có cuộc gọi trong hàng chờ.</p>
          <p className="muted">
            Dùng trang Mô phỏng mobile để yêu cầu cuộc gọi. Danh sách tự làm mới sau vài giây.
          </p>
        </section>
      ) : (
        <ul className="call-queue">
          {queue.map((call) => (
            <li key={call.sessionId} className="call-queue-item">
              <div className="call-queue-meta">
                <strong>{call.customerName ?? 'Khách hàng'}</strong>
                <code>{call.sessionId}</code>
                <span className="muted">
                  Yêu cầu lúc {new Date(call.createdAt).toLocaleTimeString('vi-VN')}
                </span>
              </div>
              <button
                type="button"
                onClick={() => void handleAccept(call)}
                disabled={acceptingId != null}
              >
                {acceptingId === call.sessionId ? 'Đang nhận…' : 'Nhận cuộc gọi'}
              </button>
            </li>
          ))}
        </ul>
      )}

      {pageError && <div className="error-banner">{pageError}</div>}
    </div>
  );
}
