interface MediaControlsProps {
  micEnabled: boolean;
  cameraEnabled: boolean;
  onToggleMic: () => void;
  onToggleCamera: () => void;
  onEndCall?: () => void;
  disabled?: boolean;
}

function IconMic({ muted }: { muted?: boolean }) {
  return (
    <svg className="vb-ctrl-icon" viewBox="0 0 24 24" aria-hidden>
      {muted ? (
        <>
          <path
            d="M9 5a3 3 0 0 1 6 0v5a3 3 0 0 1-4.5 2.6"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
          <path
            d="M5 11a7 7 0 0 0 8.5 6.8M12 18v3M8 21h8M4 4l16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </>
      ) : (
        <>
          <rect x="9" y="3" width="6" height="11" rx="3" fill="none" stroke="currentColor" strokeWidth="1.8" />
          <path
            d="M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </>
      )}
    </svg>
  );
}

function IconCamera({ off }: { off?: boolean }) {
  return (
    <svg className="vb-ctrl-icon" viewBox="0 0 24 24" aria-hidden>
      {off ? (
        <>
          <path
            d="M4 8.5A2.5 2.5 0 0 1 6.5 6H12"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
          <path
            d="M15 10.5V15a2.5 2.5 0 0 1-2.5 2.5H6.5A2.5 2.5 0 0 1 4 15V9"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
          <path
            d="M15 11.5l4-2.2v6.4l-4-2.2M4 4l16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </>
      ) : (
        <>
          <rect x="3" y="7" width="12" height="10" rx="2.2" fill="none" stroke="currentColor" strokeWidth="1.8" />
          <path
            d="M15 10.5l5-2.5v9l-5-2.5V10.5z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinejoin="round"
          />
        </>
      )}
    </svg>
  );
}

function IconEndCall() {
  return (
    <svg className="vb-ctrl-icon" viewBox="0 0 24 24" aria-hidden>
      <path
        d="M6.5 15.5c1.8 1.8 4.1 2.9 6.6 3.1h.2c1.1 0 2-.9 2-2v-1.4c0-.7-.5-1.3-1.2-1.5l-1.8-.5c-.7-.2-1.4.1-1.7.7l-.3.6c-.2.4-.6.6-1 .5-1.2-.4-2.3-1.2-3.1-2.1-.9-.9-1.6-1.9-2-3.1-.1-.4.1-.8.5-1l.6-.3c.6-.3.9-1 .7-1.7l-.5-1.8C7.2 5.4 6.6 5 5.9 5H4.5c-1.1 0-2 .9-2 2v.2C2.7 9.7 3.8 12 5.6 13.8l.9 1.7z"
        fill="currentColor"
        transform="rotate(135 12 12)"
      />
    </svg>
  );
}

function IconScreenShare() {
  return (
    <svg className="vb-ctrl-icon" viewBox="0 0 24 24" aria-hidden>
      <rect x="3" y="5" width="18" height="12" rx="2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 21h8M12 17v4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

function IconSpeaker() {
  return (
    <svg className="vb-ctrl-icon" viewBox="0 0 24 24" aria-hidden>
      <path
        d="M4 10v4h3l4 3V7l-4 3H4z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="M15 9.5a3.5 3.5 0 0 1 0 5M17.5 7.5a6.5 6.5 0 0 1 0 9"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function MediaControls({
  micEnabled,
  cameraEnabled,
  onToggleMic,
  onToggleCamera,
  onEndCall,
  disabled = false,
}: MediaControlsProps) {
  return (
    <div className="vb-call-controls">
      <button
        type="button"
        className={micEnabled ? 'vb-ctrl' : 'vb-ctrl vb-ctrl--off'}
        onClick={onToggleMic}
        disabled={disabled}
        title={micEnabled ? 'Tắt mic' : 'Bật mic'}
        aria-label={micEnabled ? 'Tắt mic' : 'Bật mic'}
      >
        <IconMic muted={!micEnabled} />
      </button>
      <button
        type="button"
        className={cameraEnabled ? 'vb-ctrl' : 'vb-ctrl vb-ctrl--off'}
        onClick={onToggleCamera}
        disabled={disabled}
        title={cameraEnabled ? 'Tắt camera' : 'Bật camera'}
        aria-label={cameraEnabled ? 'Tắt camera' : 'Bật camera'}
      >
        <IconCamera off={!cameraEnabled} />
      </button>
      {onEndCall && (
        <button
          type="button"
          className="vb-ctrl vb-ctrl--end"
          onClick={onEndCall}
          disabled={disabled}
          title="Kết thúc cuộc gọi"
          aria-label="Kết thúc cuộc gọi"
        >
          <IconEndCall />
        </button>
      )}
      <button type="button" className="vb-ctrl" disabled title="Chia sẻ màn hình (chưa hỗ trợ)">
        <IconScreenShare />
      </button>
      <button type="button" className="vb-ctrl" disabled title="Loa (chưa hỗ trợ)">
        <IconSpeaker />
      </button>
    </div>
  );
}
