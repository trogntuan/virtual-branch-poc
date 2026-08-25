import { useCallback, useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import {
  getRecordingModeSetting,
  setRecordingModeSetting,
  type RecordingMode,
} from '../api/virtualBranchApi';
import { AgentChromeProvider, useAgentChrome } from './AgentChromeContext';

function AgentShellInner() {
  const location = useLocation();
  const { pageTitle, recordingState, headerExtra } = useAgentChrome();
  const onCall = location.pathname.startsWith('/agent/call');
  const onQueue = location.pathname === '/agent' || location.pathname === '/agent/';
  const [recordingMode, setRecordingMode] = useState<RecordingMode>('ROOM_COMPOSITE');
  const [modeBusy, setModeBusy] = useState(false);

  const refreshMode = useCallback(async () => {
    try {
      const setting = await getRecordingModeSetting();
      if (setting.mode === 'DUAL_PARTICIPANT' || setting.mode === 'ROOM_COMPOSITE') {
        setRecordingMode(setting.mode);
      }
    } catch {
      // keep previous
    }
  }, []);

  useEffect(() => {
    void refreshMode();
  }, [refreshMode]);

  async function handleModeChange(mode: RecordingMode) {
    if (mode === recordingMode || modeBusy || onCall) return;
    setModeBusy(true);
    try {
      const updated = await setRecordingModeSetting(mode);
      if (updated.mode === 'DUAL_PARTICIPANT' || updated.mode === 'ROOM_COMPOSITE') {
        setRecordingMode(updated.mode);
      }
    } catch {
      await refreshMode();
    } finally {
      setModeBusy(false);
    }
  }

  return (
    <div className="vb-shell">
      <aside className="vb-sidebar">
        <div className="vb-sidebar-brand">
          <div className="vb-sidebar-avatar" aria-hidden>
            VB
          </div>
          <span>Virtual Branch</span>
        </div>
        <nav className="vb-sidebar-nav">
          <Link to="/agent" className={onQueue ? 'vb-nav-item active' : 'vb-nav-item'}>
            <span className="vb-nav-icon" aria-hidden>
              ▤
            </span>
            <span className="vb-nav-label">Hàng Đợi</span>
            {onQueue && <span className="vb-nav-pill" />}
          </Link>
          <span className={onCall ? 'vb-nav-item active' : 'vb-nav-item vb-nav-item--disabled'}>
            <span className="vb-nav-icon" aria-hidden>
              ◉
            </span>
            <span className="vb-nav-label">Đang xử lý</span>
          </span>
        </nav>
        <div className="vb-sidebar-footer">
          <div className="vb-rec-mode" title="Lưu trong DB local — không cần redeploy">
            <span className="vb-rec-mode-title">Rec</span>
            <button
              type="button"
              className={
                recordingMode === 'ROOM_COMPOSITE'
                  ? 'vb-rec-mode-btn vb-rec-mode-btn--on'
                  : 'vb-rec-mode-btn'
              }
              disabled={modeBusy || onCall}
              onClick={() => void handleModeChange('ROOM_COMPOSITE')}
              title="RoomComposite — gộp 2 phía thành 1 video"
            >
              Gộp
            </button>
            <button
              type="button"
              className={
                recordingMode === 'DUAL_PARTICIPANT'
                  ? 'vb-rec-mode-btn vb-rec-mode-btn--on'
                  : 'vb-rec-mode-btn'
              }
              disabled={modeBusy || onCall}
              onClick={() => void handleModeChange('DUAL_PARTICIPANT')}
              title="Participant — mỗi phía 1 video, không gộp"
            >
              Tách
            </button>
          </div>
          <Link
            to="/infra-load-test"
            className="vb-nav-item vb-nav-item--muted"
            title="Đo lường hạ tầng"
          >
            <span className="vb-nav-icon" aria-hidden>
              ▣
            </span>
            <span className="vb-nav-label">Đo HT</span>
          </Link>
          <Link to="/customer-test" className="vb-nav-item vb-nav-item--muted" title="Mobile Test">
            <span className="vb-nav-icon" aria-hidden>
              ▤
            </span>
            <span className="vb-nav-label">Mobile</span>
          </Link>
        </div>
      </aside>

      <div className="vb-main">
        <header className="vb-header">
          <div className="vb-header-left">
            <h1 className="vb-header-title">{pageTitle}</h1>
            {recordingState === 'recording' && (
              <span className="vb-rec-badge" title="Đang ghi hình">
                <span className="vb-rec-dot" />
                REC
              </span>
            )}
            {recordingState === 'uploading' && (
              <span className="vb-rec-badge vb-rec-badge--upload" title="Đang lưu bản ghi">
                <span className="vb-rec-spinner" />
                Đang lưu…
              </span>
            )}
          </div>
          <div className="vb-header-search">
            <span className="vb-search-icon" aria-hidden>
              ⌕
            </span>
            <input type="search" placeholder="Tìm kiếm" aria-label="Tìm kiếm" disabled />
          </div>
          <div className="vb-header-right">
            {headerExtra}
            <button type="button" className="vb-icon-btn" aria-label="Thông báo" disabled>
              <span aria-hidden>🔔</span>
              <span className="vb-badge-count">3</span>
            </button>
            <div className="vb-agent-chip">
              <span className="vb-agent-avatar">T</span>
              <div className="vb-agent-meta">
                <strong>TuanNT10</strong>
                <span className="vb-agent-status">
                  <i className="vb-online-dot" /> Đang trực
                </span>
              </div>
            </div>
          </div>
        </header>
        <div className="vb-content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

export function AgentShell() {
  return (
    <AgentChromeProvider>
      <AgentShellInner />
    </AgentChromeProvider>
  );
}
