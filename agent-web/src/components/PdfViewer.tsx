import { useEffect, useRef, useState } from 'react';
import * as pdfjsLib from 'pdfjs-dist';

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

interface PdfViewerProps {
  url: string;
}

export function PdfViewer({ url }: PdfViewerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [pdfDoc, setPdfDoc] = useState<pdfjsLib.PDFDocumentProxy | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageCount, setPageCount] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    setPdfDoc(null);
    setPageNumber(1);
    setPageCount(0);

    const loadingTask = pdfjsLib.getDocument({
      url,
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
        if (cancelled) return;
        setLoadError(cause instanceof Error ? cause.message : 'Không tải được PDF.');
        setLoading(false);
      });

    return () => {
      cancelled = true;
      void loadingTask.destroy();
    };
  }, [url]);

  useEffect(() => {
    if (!pdfDoc || !canvasRef.current) return;

    let cancelled = false;

    void (async () => {
      try {
        const page = await pdfDoc.getPage(pageNumber);
        if (cancelled) return;

        const viewport = page.getViewport({ scale: 1.5 });
        const canvas = canvasRef.current!;
        const context = canvas.getContext('2d');
        if (!context) return;

        canvas.width = viewport.width;
        canvas.height = viewport.height;

        await page.render({ canvasContext: context, viewport }).promise;
      } catch (cause: unknown) {
        if (!cancelled) {
          setLoadError(cause instanceof Error ? cause.message : 'Không hiển thị được trang PDF.');
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [pdfDoc, pageNumber]);

  function goPrev() {
    setPageNumber((current) => Math.max(1, current - 1));
  }

  function goNext() {
    setPageNumber((current) => Math.min(pageCount, current + 1));
  }

  if (loading) {
    return <div className="pdf-viewer pdf-viewer-loading">Đang tải PDF…</div>;
  }

  if (loadError) {
    return <div className="error-banner">{loadError}</div>;
  }

  return (
    <div className="pdf-viewer">
      <div className="pdf-viewer-toolbar">
        <button type="button" onClick={goPrev} disabled={pageNumber <= 1}>
          Trước
        </button>
        <span className="pdf-viewer-page-info">
          Trang {pageNumber} / {pageCount}
        </span>
        <button type="button" onClick={goNext} disabled={pageNumber >= pageCount}>
          Sau
        </button>
      </div>
      <div className="pdf-viewer-canvas-wrap">
        <canvas ref={canvasRef} className="pdf-viewer-canvas" />
      </div>
    </div>
  );
}
