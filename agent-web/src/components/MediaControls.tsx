import type { ReactNode } from 'react';

interface MediaControlsProps {
  micEnabled: boolean;
  cameraEnabled: boolean;
  onToggleMic: () => void;
  onToggleCamera: () => void;
  disabled?: boolean;
  extra?: ReactNode;
}

export function MediaControls({
  micEnabled,
  cameraEnabled,
  onToggleMic,
  onToggleCamera,
  disabled = false,
  extra,
}: MediaControlsProps) {
  return (
    <div className="controls-row">
      <button type="button" onClick={onToggleMic} disabled={disabled}>
        {micEnabled ? 'Tắt mic' : 'Bật mic'}
      </button>
      <button type="button" onClick={onToggleCamera} disabled={disabled}>
        {cameraEnabled ? 'Tắt camera' : 'Bật camera'}
      </button>
      {extra}
    </div>
  );
}
