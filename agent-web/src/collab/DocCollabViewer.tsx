import { useEffect, useRef, useState } from 'react';
import * as pdfjsLib from 'pdfjs-dist';
import { proxyStorageUrl } from '../storageUrl';
import { clamp01, type DocCollabViewState } from './events';

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

const DRAG_THRESHOLD = 0.012;
const PDF_RENDER_SCALE = 1.1;

function isRenderCancelled(cause: unknown): boolean {
  if (!cause || typeof cause !== 'object') return false;
  const name = 'name' in cause ? String(cause.name) : '';
  const message = 'message' in cause ? String(cause.message) : '';
  return name === 'RenderingCancelledException' || message.includes('Rendering cancelled');
}

interface DocCollabViewerProps {
  url: string;
  mode: 'agent' | 'customer';
  viewState: DocCollabViewState;
  onPageChange?: (page: number) => void;
  onPointerMove?: (page: number, x: number, y: number) => void;
  onHighlightSelect?: (page: number, x: number, y: number, width: number, height: number) => void;
}

interface PageCoords {
  x: number;
  y: number;
}

interface DragRect extends PageCoords {
  width: number;
  height: number;
}

export function DocCollabViewer({
  url,
  mode,
  viewState,
  onPageChange,
  onPointerMove,
  onHighlightSelect,
}: DocCollabViewerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const renderTaskRef = useRef<pdfjsLib.RenderTask | null>(null);
  const [pdfDoc, setPdfDoc] = useState<pdfjsLib.PDFDocumentProxy | null>(null);
  const [pageCount, setPageCount] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [draftHighlight, setDraftHighlight] = useState<DragRect | null>(null);
  const dragStartRef = useRef<PageCoords | null>(null);
  const isDraggingRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);

    const fetchUrl = proxyStorageUrl(url);
    const loadingTask = pdfjsLib.getDocument({
      url: fetchUrl,
      // Range requests need extra GCS CORS headers; disable so Cloud Run can fetch the PDF.
      disableRange: true,
      disableStream: true,
    });
    loadingTask.promise
      .then((doc) => {
        if (cancelled) {
          void doc.destroy();
          return;
        }
        setPdfDoc(doc);
        setPageCount(doc.numPages);
        setLoading(false);
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setLoadError(cause instanceof Error ? cause.message : 'Không tải được PDF.');
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
      void loadingTask.destroy();
    };
  }, [url]);

  useEffect(() => {
    if (!pdfDoc || !canvasRef.current) return;

    let cancelled = false;
    const pageNumber = Math.min(Math.max(viewState.page, 1), pdfDoc.numPages);

    void (async () => {
      try {
        const previous = renderTaskRef.current;
        if (previous) {
          previous.cancel();
          try {
            await previous.promise;
          } catch {
            // RenderingCancelledException expected
          }
          if (renderTaskRef.current === previous) {
            renderTaskRef.current = null;
          }
        }
        if (cancelled || !canvasRef.current) return;

        const page = await pdfDoc.getPage(pageNumber);
        if (cancelled || !canvasRef.current) return;

        const viewport = page.getViewport({ scale: PDF_RENDER_SCALE });
        const canvas = canvasRef.current;
        const context = canvas.getContext('2d');
        if (!context) return;

        canvas.width = viewport.width;
        canvas.height = viewport.height;

        const task = page.render({ canvasContext: context, viewport });
        renderTaskRef.current = task;
        await task.promise;
        if (renderTaskRef.current === task) {
          renderTaskRef.current = null;
        }
      } catch (cause: unknown) {
        if (cancelled || isRenderCancelled(cause)) return;
        setLoadError(cause instanceof Error ? cause.message : 'Không hiển thị được trang PDF.');
      }
    })();

    return () => {
      cancelled = true;
      renderTaskRef.current?.cancel();
    };
  }, [pdfDoc, viewState.page]);

  function getPageCoords(event: React.MouseEvent<HTMLDivElement>): PageCoords | null {
    const canvas = canvasRef.current;
    if (!canvas) return null;

    const rect = canvas.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return null;

    return {
      x: clamp01((event.clientX - rect.left) / rect.width),
      y: clamp01((event.clientY - rect.top) / rect.height),
    };
  }

  function resetDrag() {
    dragStartRef.current = null;
    isDraggingRef.current = false;
    setDraftHighlight(null);
  }

  function handleMouseDown(event: React.MouseEvent<HTMLDivElement>) {
    if (mode !== 'agent' || event.button !== 0) return;
    const coords = getPageCoords(event);
    if (!coords) return;
    dragStartRef.current = coords;
    isDraggingRef.current = false;
    setDraftHighlight(null);
  }

  function handleMouseMove(event: React.MouseEvent<HTMLDivElement>) {
    if (mode !== 'agent') return;

    const coords = getPageCoords(event);
    if (!coords) return;

    if (event.buttons === 1 && dragStartRef.current) {
      const start = dragStartRef.current;
      const width = Math.abs(coords.x - start.x);
      const height = Math.abs(coords.y - start.y);

      if (!isDraggingRef.current && (width >= DRAG_THRESHOLD || height >= DRAG_THRESHOLD)) {
        isDraggingRef.current = true;
      }

      if (isDraggingRef.current) {
        setDraftHighlight({
          x: Math.min(start.x, coords.x),
          y: Math.min(start.y, coords.y),
          width,
          height,
        });
        return;
      }
    }

    if (!isDraggingRef.current && onPointerMove) {
      onPointerMove(viewState.page, coords.x, coords.y);
    }
  }

  function handleMouseUp() {
    if (mode !== 'agent' || !dragStartRef.current) return;

    if (isDraggingRef.current && draftHighlight && onHighlightSelect) {
      if (draftHighlight.width >= DRAG_THRESHOLD && draftHighlight.height >= DRAG_THRESHOLD) {
        onHighlightSelect(
          viewState.page,
          draftHighlight.x,
          draftHighlight.y,
          draftHighlight.width,
          draftHighlight.height,
        );
      }
    }

    resetDrag();
  }

  function handleMouseLeave() {
    if (isDraggingRef.current) {
      resetDrag();
    }
  }

  if (loading) {
    return <div className="pdf-viewer pdf-viewer-loading">Đang tải PDF…</div>;
  }

  if (loadError) {
    return <div className="error-banner">{loadError}</div>;
  }

  const showPointer =
    viewState.pointer.visible &&
    viewState.pointer.page === viewState.page;
  const showHighlight =
    viewState.highlight.visible &&
    viewState.highlight.page === viewState.page;
  const showDraft = draftHighlight != null;

  return (
    <div className="pdf-viewer">
      {mode === 'agent' && (
        <div className="pdf-viewer-toolbar">
          <button
            type="button"
            className="pdf-nav-btn"
            onClick={() => onPageChange?.(Math.max(1, viewState.page - 1))}
            disabled={viewState.page <= 1}
            title="Trang trước"
            aria-label="Trang trước"
          >
            ‹
          </button>
          <span className="pdf-viewer-page-info">
            Trang {viewState.page} / {pageCount}
          </span>
          <button
            type="button"
            className="pdf-nav-btn"
            onClick={() => onPageChange?.(Math.min(pageCount, viewState.page + 1))}
            disabled={viewState.page >= pageCount}
            title="Trang sau"
            aria-label="Trang sau"
          >
            ›
          </button>
          <span className="pdf-viewer-hint muted">Giữ chuột và kéo để tô vùng</span>
        </div>
      )}

      {mode === 'customer' && (
        <div className="pdf-viewer-toolbar">
          <span className="pdf-viewer-page-info">
            Trang {viewState.page} / {pageCount} (chỉ xem)
          </span>
        </div>
      )}

      <div className="doc-collab-frame">
        <div
          className="doc-page-surface"
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseLeave}
        >
          <canvas ref={canvasRef} className="pdf-viewer-canvas" />
          {showPointer && (
            <div
              className="doc-pointer"
              style={{
                left: `${viewState.pointer.x * 100}%`,
                top: `${viewState.pointer.y * 100}%`,
              }}
            />
          )}
          {showHighlight && (
            <div
              className="doc-highlight"
              style={{
                left: `${viewState.highlight.x * 100}%`,
                top: `${viewState.highlight.y * 100}%`,
                width: `${viewState.highlight.width * 100}%`,
                height: `${viewState.highlight.height * 100}%`,
              }}
            />
          )}
          {showDraft && (
            <div
              className="doc-highlight doc-highlight--draft"
              style={{
                left: `${draftHighlight.x * 100}%`,
                top: `${draftHighlight.y * 100}%`,
                width: `${draftHighlight.width * 100}%`,
                height: `${draftHighlight.height * 100}%`,
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
}
