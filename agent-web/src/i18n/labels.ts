const COLLAB_STATUS: Record<string, string> = {
  REQUESTED: 'Chờ khách hàng',
  ACTIVE: 'Đang chia sẻ',
  REJECTED: 'Đã từ chối',
  ENDED: 'Đã kết thúc',
};

const RECORDING_STATUS: Record<string, string> = {
  REQUESTED: 'Đã yêu cầu',
  STARTING: 'Đang bắt đầu',
  RECORDING: 'Đang ghi',
  STOPPING: 'Đang dừng',
  COMPLETED: 'Hoàn tất',
  FAILED: 'Thất bại',
};

const ORIENTATION: Record<string, string> = {
  PORTRAIT: 'Dọc',
  LANDSCAPE: 'Ngang',
};

export function collabStatusLabel(status: string): string {
  return COLLAB_STATUS[status] ?? status;
}

export function recordingStatusLabel(status: string): string {
  return RECORDING_STATUS[status] ?? status;
}

export function orientationLabel(value: string): string {
  return ORIENTATION[value] ?? value;
}
