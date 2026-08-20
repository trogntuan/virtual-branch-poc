import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

export type RecordingChromeState = 'idle' | 'recording' | 'uploading';

interface AgentChromeValue {
  pageTitle: string;
  setPageTitle: (title: string) => void;
  recordingState: RecordingChromeState;
  setRecordingState: (state: RecordingChromeState) => void;
  headerExtra: ReactNode;
  setHeaderExtra: (node: ReactNode) => void;
}

const AgentChromeContext = createContext<AgentChromeValue | null>(null);

export function AgentChromeProvider({ children }: { children: ReactNode }) {
  const [pageTitle, setPageTitle] = useState('Hàng đợi');
  const [recordingState, setRecordingState] = useState<RecordingChromeState>('idle');
  const [headerExtra, setHeaderExtra] = useState<ReactNode>(null);

  const value = useMemo(
    () => ({
      pageTitle,
      setPageTitle,
      recordingState,
      setRecordingState,
      headerExtra,
      setHeaderExtra,
    }),
    [pageTitle, recordingState, headerExtra],
  );

  return <AgentChromeContext.Provider value={value}>{children}</AgentChromeContext.Provider>;
}

export function useAgentChrome() {
  const ctx = useContext(AgentChromeContext);
  if (!ctx) {
    throw new Error('useAgentChrome must be used within AgentChromeProvider');
  }
  return ctx;
}

/** Reset chrome fields when leaving a page. */
export function useAgentChromePage(title: string) {
  const { setPageTitle, setRecordingState, setHeaderExtra } = useAgentChrome();

  const reset = useCallback(() => {
    setRecordingState('idle');
    setHeaderExtra(null);
  }, [setRecordingState, setHeaderExtra]);

  return { setPageTitle, setRecordingState, setHeaderExtra, reset, title };
}
