import { useCallback, useRef, useState } from 'react';
import {
  startRecording as apiStart,
  stopRecording as apiStop,
  getRecording as apiGet,
  type RecordingResponse,
} from '../api/virtualBranchApi';

const TERMINAL_STATUSES = ['COMPLETED', 'FAILED'];
const POLL_INTERVAL_MS = 3000;

export function useRecording() {
  const [recording, setRecording] = useState<RecordingResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const startPolling = useCallback(
    (recordingId: string) => {
      stopPolling();
      pollRef.current = setInterval(async () => {
        try {
          const rec = await apiGet(recordingId);
          setRecording(rec);
          if (TERMINAL_STATUSES.includes(rec.status)) {
            stopPolling();
          }
        } catch {
          // keep polling
        }
      }, POLL_INTERVAL_MS);
    },
    [stopPolling],
  );

  const start = useCallback(
    async (sessionId: string) => {
      setBusy(true);
      setError(null);
      try {
        const rec = await apiStart(sessionId);
        setRecording(rec);
        startPolling(rec.recordingId);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Không bắt đầu được ghi hình.');
      } finally {
        setBusy(false);
      }
    },
    [startPolling],
  );

  const stop = useCallback(async () => {
    if (!recording) return;
    setBusy(true);
    setError(null);
    try {
      const rec = await apiStop(recording.recordingId);
      setRecording(rec);
      startPolling(rec.recordingId);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không dừng được ghi hình.');
    } finally {
      setBusy(false);
    }
  }, [recording, startPolling]);

  const reset = useCallback(() => {
    stopPolling();
    setRecording(null);
    setError(null);
  }, [stopPolling]);

  return {
    recording,
    recordingError: error,
    recordingBusy: busy,
    startRecording: start,
    stopRecording: stop,
    resetRecording: reset,
  };
}
