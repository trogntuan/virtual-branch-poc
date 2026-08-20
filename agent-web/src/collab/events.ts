export const DOC_COLLAB_TOPIC = 'doc-collab';

export type DocCollabEventType =
  | 'COLLAB_REQUEST'
  | 'DOC_STATE'
  | 'PAGE_CHANGE'
  | 'VIEWPORT_CHANGE'
  | 'POINTER_MOVE'
  | 'POINTER_HIDE'
  | 'HIGHLIGHT_SET'
  | 'HIGHLIGHT_CLEAR'
  | 'COLLAB_END';

export interface PointerState {
  visible: boolean;
  page: number;
  x: number;
  y: number;
}

export interface HighlightState {
  visible: boolean;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DocCollabViewState {
  page: number;
  viewMode: 'FIT_WIDTH' | 'FIT_PAGE' | 'CUSTOM';
  zoomScale: number;
  scrollRatio: number;
  pointer: PointerState;
  highlight: HighlightState;
}

export interface DocCollabEvent<T = unknown> {
  version: number;
  type: DocCollabEventType;
  collabId: string;
  sessionId: string;
  documentId: string;
  sequence: number;
  timestamp: number;
  data: T;
}

export const DEFAULT_VIEW_STATE: DocCollabViewState = {
  page: 1,
  viewMode: 'FIT_WIDTH',
  zoomScale: 1.0,
  scrollRatio: 0,
  pointer: { visible: false, page: 1, x: 0, y: 0 },
  highlight: { visible: false, page: 1, x: 0, y: 0, width: 0, height: 0 },
};

export const RELIABLE_EVENTS = new Set<DocCollabEventType>([
  'COLLAB_REQUEST',
  'DOC_STATE',
  'PAGE_CHANGE',
  'HIGHLIGHT_SET',
  'HIGHLIGHT_CLEAR',
  'COLLAB_END',
]);

export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}
