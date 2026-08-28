import { useCallback, useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import {
  getRecordingModeSetting,
  getRecordingSetting,
  setRecordingModeSetting,
  setRecordingSetting,
  type RecordingMode,
} from '../api/virtualBranchApi';
import { AGENT_WEB_BUILD } from '../buildId';
import { AgentChromeProvider, useAgentChrome } from './AgentChromeContext';

function AgentShellInner() {
  const location = useLocation();
  const { pageTitle, recordingState, headerExtra } = useAgentChrome();
  const onCall = location.pathname.startsWith('/agent/call');
  const onQueue = location.pathname === '/agent' || location.pathname === '/agent/';
  const [recordingEnabled, setRecordingEnabled] = useState(false);
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [recordingMode, setRecordingMode] = useState<RecordingMode>('ROOM_COMPOSITE');
  const [modeBusy, setModeBusy] = useState(false);

  const refreshSettings = useCallback(async () => {
    try {
      const [recording, mode] = await Promise.all([
        getRecordingSetting(),
        getRecordingModeSetting(),
      ]);
      setRecordingEnabled(recording.enabled);
      if (mode.mode === 'DUAL_PARTICIPANT' || mode.mode === 'ROOM_COMPOSITE') {
        setRecordingMode(mode.mode);
      }
    } catch {
      // keep previous values
    }
  }, []);

  useEffect(() => {
    void refreshSettings();
  }, [refreshSettings]);

  async function handleToggleRecording() {
    const next = !recordingEnabled;
    setRecordingBusy(true);
    try {
      const updated = await setRecordingSetting(next);
      setRecordingEnabled(updated.enabled);
    } catch {
      await refreshSettings();
    } finally {
      setRecordingBusy(false);
    }
  }

  async function handleModeChange(mode: RecordingMode) {
    if (mode === recordingMode || modeBusy || onCall) return;
    setModeBusy(true);
    try {
      const updated = await setRecordingModeSetting(mode);
      if (updated.mode === 'DUAL_PARTICIPANT' || updated.mode === 'ROOM_COMPOSITE') {
        setRecordingMode(updated.mode);
      }
    } catch {
      await refreshSettings();
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
          <button
            type="button"
            className={
              recordingEnabled
                ? 'vb-setting-toggle vb-setting-toggle--on'
                : 'vb-setting-toggle'
            }
            onClick={() => void handleToggleRecording()}
            disabled={recordingBusy || onCall}
            title={
              onCall
                ? 'Không đổi trong lúc đang gọi'
                : 'Bật/tắt ghi hình (lưu DB, không cần deploy)'
            }
          >
            <span className="vb-setting-toggle-label">Ghi hình</span>
            <span className="vb-setting-toggle-state">
              {recordingBusy ? '…' : recordingEnabled ? 'Bật' : 'Tắt'}
            </span>
          </button>
          <div className="vb-rec-mode" title="Lưu trong DB — không cần redeploy">
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
          <span className="vb-build-id" title={AGENT_WEB_BUILD}>
            {AGENT_WEB_BUILD}
          </span>
        </div>
      </aside>

      <div className="vb-main">
        <header className="vb-header">
          <h1 className="vb-header-title">{pageTitle}</h1>
          <div className="vb-header-search">
            <input type="search" placeholder="Tìm kiếm…" disabled />
          </div>
          <div className="vb-header-actions">
            {headerExtra}
            {recordingState !== 'idle' && (
              <span className={`vb-recording-pill vb-recording-pill--${recordingState}`}>
                {recordingState === 'recording' ? '● REC' : recordingState}
              </span>
            )}
          </div>
        </header>
        <main className="vb-content">
          <Outlet />
        </main>
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
