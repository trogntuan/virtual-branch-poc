import { useCallback, useRef, useState } from 'react';
import {
  startRecording as apiStart,
  stopRecording as apiStop,
  getRecording as apiGet,
  type RecordingResponse,
} from '../api/virtualBranchApi';

const TERMINAL_STATUSES = ['COMPLETED', 'FAILED'];
const POLL_INTERVAL_MS = 2500;
const WAIT_TIMEOUT_MS = 180_000;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Dual: wait until every track is terminal (so both Agent + Customer slots can render). */
function isTerminalRecording(rec: RecordingResponse): boolean {
  if (rec.mode === 'DUAL_PARTICIPANT' && rec.tracks && rec.tracks.length > 0) {
    return rec.tracks.every((track) => TERMINAL_STATUSES.includes(track.status));
  }
  return TERMINAL_STATUSES.includes(rec.status);
}

export function useRecording() {
  const [recording, setRecording] = useState<RecordingResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const recordingRef = useRef<RecordingResponse | null>(null);

  const setRecordingBoth = useCallback((rec: RecordingResponse | null) => {
    recordingRef.current = rec;
    setRecording(rec);
  }, []);

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
          setRecordingBoth(rec);
          if (isTerminalRecording(rec)) {
            stopPolling();
          }
        } catch {
          // keep polling
        }
      }, POLL_INTERVAL_MS);
    },
    [setRecordingBoth, stopPolling],
  );

  const waitForTerminal = useCallback(
    async (recordingId: string, timeoutMs = WAIT_TIMEOUT_MS): Promise<RecordingResponse> => {
      const deadline = Date.now() + timeoutMs;
      let last: RecordingResponse | null = null;
      while (Date.now() < deadline) {
        const rec = await apiGet(recordingId);
        last = rec;
        setRecordingBoth(rec);
        if (isTerminalRecording(rec)) {
          stopPolling();
          return rec;
        }
        await sleep(POLL_INTERVAL_MS);
      }
      if (last) {
        stopPolling();
        return last;
      }
      throw new Error('Hết thời gian chờ bản ghi tải lên.');
    },
    [setRecordingBoth, stopPolling],
  );

  const start = useCallback(
    async (sessionId: string) => {
      setBusy(true);
      setError(null);
      try {
        const rec = await apiStart(sessionId);
        setRecordingBoth(rec);
        startPolling(rec.recordingId);
        return rec;
      } catch (cause) {
        const message = cause instanceof Error ? cause.message : 'Không bắt đầu được ghi hình.';
        setError(message);
        throw cause instanceof Error ? cause : new Error(message);
      } finally {
        setBusy(false);
      }
    },
    [setRecordingBoth, startPolling],
  );

  const stop = useCallback(async () => {
    const current = recordingRef.current;
    if (!current) return null;
    setBusy(true);
    setError(null);
    try {
      const rec = await apiStop(current.recordingId);
      setRecordingBoth(rec);
      startPolling(rec.recordingId);
      return rec;
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'Không dừng được ghi hình.';
      setError(message);
      throw cause instanceof Error ? cause : new Error(message);
    } finally {
      setBusy(false);
    }
  }, [setRecordingBoth, startPolling]);

  const stopAndWait = useCallback(async (): Promise<RecordingResponse | null> => {
    const current = recordingRef.current;
    if (!current) return null;
    if (isTerminalRecording(current)) {
      return current;
    }

    setBusy(true);
    setError(null);
    try {
      stopPolling();
      let rec = current;
      if (!isTerminalRecording(current) && current.status !== 'STOPPING') {
        rec = await apiStop(current.recordingId);
        setRecordingBoth(rec);
      }
      if (isTerminalRecording(rec)) {
        return rec;
      }
      return await waitForTerminal(rec.recordingId);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'Không lưu được bản ghi.';
      setError(message);
      throw cause instanceof Error ? cause : new Error(message);
    } finally {
      setBusy(false);
    }
  }, [setRecordingBoth, stopPolling, waitForTerminal]);

  const reset = useCallback(() => {
    stopPolling();
    setRecordingBoth(null);
    setError(null);
  }, [setRecordingBoth, stopPolling]);

  return {
    recording,
    recordingError: error,
    recordingBusy: busy,
    startRecording: start,
    stopRecording: stop,
    stopAndWaitRecording: stopAndWait,
    resetRecording: reset,
  };
}
