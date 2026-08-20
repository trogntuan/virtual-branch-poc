import type { RoomConnectionState } from '../livekit/types';

interface ConnectionBadgeProps {
  state: RoomConnectionState;
}

const LABELS: Record<RoomConnectionState, string> = {
  DISCONNECTED: 'Mất kết nối',
  CONNECTING: 'Đang kết nối…',
  CONNECTED: 'Đã kết nối',
  RECONNECTING: 'Đang kết nối lại…',
};

export function ConnectionBadge({ state }: ConnectionBadgeProps) {
  return <span className={`badge badge-${state.toLowerCase()}`}>{LABELS[state]}</span>;
}
