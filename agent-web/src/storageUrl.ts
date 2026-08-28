/**
 * Route local MinIO presigned URLs through the Vite dev proxy so PDF.js / img fetch
 * stays same-origin. Proxy target must stay on localhost:9000 (same host as presign).
 */
export function proxyStorageUrl(readUrl: string): string {
  try {
    const parsed = new URL(readUrl);
    const isLocalMinio =
      (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1') && parsed.port === '9000';
    if (isLocalMinio) {
      return `/object-storage${parsed.pathname}${parsed.search}`;
    }
  } catch {
    // keep original URL
  }
  return readUrl;
}
