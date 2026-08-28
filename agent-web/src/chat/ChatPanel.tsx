import { useEffect, useMemo, useRef, useState } from 'react';
import { initialsFromName } from '../agentIdentity';
import { proxyStorageUrl } from '../storageUrl';
import { getDocumentUrl, uploadDocument } from '../api/virtualBranchApi';
import type { ChatMessage, ChatSenderRole } from './types';
import { useSessionChat } from './useSessionChat';
import {
  buildFileAccept,
  buildLatestCollabStatusMap,
  decodeDisplayName,
  fileTypeLabel,
  formatBytes,
  formatChatTime,
  isImageDocument,
  isPdfDocument,
} from './utils';

export interface ChatPanelProps {
  sessionId: string | null;
  identity: string;
  name: string;
  role: ChatSenderRole;
  enabled?: boolean;
  compact?: boolean;
  demoPdf?: {
    fileName: string;
    sizeBytes: number;
    url: string;
    disabled?: boolean;
  };
  onCollabRequest?: (message: ChatMessage) => void;
  onCollabStatus?: (message: ChatMessage) => void;
  onError?: (message: string) => void;
}

function PdfDocIcon() {
  return (
    <svg className="vb-file-icon-svg" width="22" height="26" viewBox="0 0 22 26" fill="none" aria-hidden>
      <path
        d="M5 1h9l5 5v17a2 2 0 01-2 2H5a2 2 0 01-2-2V3a2 2 0 012-2z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <path d="M14 1v5h5" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M7 14h8M7 17h6" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );
}

function CollabCastIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <rect x="3" y="4" width="14" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.6" />
      <path d="M8 18h4M10 14v4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path
        d="M19 8c.8.8 1.2 1.7 1.2 2.5S19.8 12.2 19 13M21 6c1.6 1.6 2.4 3.2 2.4 4.5S22.6 13.4 21 15"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

function ViewDocIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"
        stroke="currentColor"
        strokeWidth="1.6"
      />
      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

function DownloadIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path d="M12 3v10M8 11l4 4 4-4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M4 19h16" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

function FileAttachmentCard({
  documentId,
  fileName,
  contentType,
  sizeBytes,
  sentAt,
  showTime = true,
  compact = false,
}: {
  documentId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sentAt?: string;
  showTime?: boolean;
  compact?: boolean;
}) {
  const typeLabel = fileTypeLabel(contentType, fileName);
  const metaParts = [formatBytes(sizeBytes), typeLabel];
  if (showTime && sentAt) {
    metaParts.push(formatChatTime(sentAt));
  }

  return (
    <div className={`vb-file-card ${compact ? 'vb-file-card--compact' : ''}`}>
      {isImageDocument(contentType) ? (
        <ImageThumb documentId={documentId} alt={fileName} />
      ) : (
        <div className="vb-file-icon" aria-hidden>
          <PdfDocIcon />
          {!compact && <span className="vb-file-icon-label">{typeLabel}</span>}
        </div>
      )}
      <div className="vb-file-info">
        <span className="vb-file-name">{fileName}</span>
        <span className="vb-file-meta">{metaParts.join(' · ')}</span>
      </div>
    </div>
  );
}

function ImageThumb({ documentId, alt }: { documentId: string; alt: string }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getDocumentUrl(documentId)
      .then((response) => {
        if (!cancelled) setUrl(proxyStorageUrl(response.readUrl));
      })
      .catch(() => {
        if (!cancelled) setUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [documentId]);

  if (!url) {
    return <div className="vb-chat-image-placeholder">Ảnh</div>;
  }
  return <img className="vb-chat-image-thumb" src={url} alt={alt} />;
}

function InteractiveFileMessage({
  documentId,
  fileName,
  contentType,
  sizeBytes,
  sentAt,
  showCollabAction,
  collabBusy,
  onRequestCollab,
  onError,
}: {
  documentId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sentAt: string;
  showCollabAction?: boolean;
  collabBusy?: boolean;
  onRequestCollab?: (documentId: string) => void;
  onError?: (message: string) => void;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);

  useEffect(() => {
    if (!menuOpen) return;
    function handlePointerDown(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, [menuOpen]);

  async function resolveReadUrl(): Promise<string> {
    const response = await getDocumentUrl(documentId);
    return proxyStorageUrl(response.readUrl);
  }

  async function handleView() {
    setActionBusy(true);
    try {
      const url = await resolveReadUrl();
      window.open(url, '_blank', 'noopener,noreferrer');
      setMenuOpen(false);
    } catch (cause) {
      onError?.(cause instanceof Error ? cause.message : 'Không mở được tài liệu.');
    } finally {
      setActionBusy(false);
    }
  }

  async function handleDownload() {
    setActionBusy(true);
    try {
      const url = await resolveReadUrl();
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`Tải xuống thất bại (${response.status})`);
      }
      const blob = await response.blob();
      const blobUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = blobUrl;
      anchor.download = fileName;
      anchor.rel = 'noopener';
      anchor.click();
      URL.revokeObjectURL(blobUrl);
      setMenuOpen(false);
    } catch (cause) {
      onError?.(cause instanceof Error ? cause.message : 'Không tải xuống được tài liệu.');
    } finally {
      setActionBusy(false);
    }
  }

  function handleCollabRequest() {
    onRequestCollab?.(documentId);
    setMenuOpen(false);
  }

  const busy = actionBusy || collabBusy;

  return (
    <div
      ref={rootRef}
      className={`vb-file-message-wrap ${menuOpen ? 'vb-file-message-wrap--open' : ''}`}
    >
      <div
        className="vb-file-message"
        onClick={() => setMenuOpen((open) => !open)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            setMenuOpen((open) => !open);
          }
          if (event.key === 'Escape') setMenuOpen(false);
        }}
        role="button"
        tabIndex={0}
        aria-expanded={menuOpen}
        aria-haspopup="menu"
        aria-label={`Tùy chọn tệp ${fileName}`}
      >
        <FileAttachmentCard
          documentId={documentId}
          fileName={fileName}
          contentType={contentType}
          sizeBytes={sizeBytes}
          sentAt={sentAt}
        />
      </div>
      <div className="vb-file-actions-menu" role="menu" onClick={(event) => event.stopPropagation()}>
        <button
          type="button"
          className="vb-file-action-btn"
          role="menuitem"
          disabled={busy}
          onClick={() => void handleView()}
        >
          <ViewDocIcon />
          <span>Xem tài liệu</span>
        </button>
        <button
          type="button"
          className="vb-file-action-btn"
          role="menuitem"
          disabled={busy}
          onClick={() => void handleDownload()}
        >
          <DownloadIcon />
          <span>Tải xuống</span>
        </button>
        {showCollabAction && onRequestCollab && (
          <button
            type="button"
            className="vb-file-action-btn vb-file-action-btn--collab"
            role="menuitem"
            disabled={busy}
            onClick={handleCollabRequest}
          >
            <CollabCastIcon />
            <span>Yêu cầu xem cùng</span>
          </button>
        )}
      </div>
    </div>
  );
}

function MessageMeta({
  label,
  sentAt,
  align,
  showTime = true,
}: {
  label: string;
  sentAt: string;
  align: 'left' | 'right';
  showTime?: boolean;
}) {
  return (
    <div className={`vb-chat-meta vb-chat-meta--${align}`}>
      <span className="vb-chat-meta-name">{label}</span>
      {showTime && <span className="vb-chat-meta-time">{formatChatTime(sentAt)}</span>}
    </div>
  );
}

function ChatBubble({
  message,
  isOwn,
  role,
  compact,
  latestCollabStatusById,
  onRequestCollab,
  onCancelCollab,
  collabBusy,
  onFileError,
}: {
  message: ChatMessage;
  isOwn: boolean;
  role: ChatSenderRole;
  compact?: boolean;
  latestCollabStatusById: Map<string, string>;
  onRequestCollab?: (documentId: string) => void;
  onCancelCollab?: (collabId: string) => void;
  collabBusy?: boolean;
  onFileError?: (message: string) => void;
}) {
  const fallbackName = message.senderRole === 'AGENT' ? 'Tổng đài' : 'Khách hàng';
  const senderName = decodeDisplayName(message.senderName, fallbackName);
  const isSystemMessage = message.senderIdentity === 'system';
  const displayName = isSystemMessage ? 'Hệ thống' : senderName;
  const label = isOwn ? 'You' : displayName;
  const avatarInitials = isSystemMessage ? 'HT' : initialsFromName(isOwn ? 'You' : displayName);
  const align = isOwn ? 'right' : 'left';

  const body = (() => {
    if (message.messageType === 'TEXT') {
      return (
        <div className={`vb-chat-bubble ${isOwn ? 'vb-chat-bubble--own' : ''}`}>{message.text}</div>
      );
    }

    if (message.messageType === 'FILE' && message.document) {
      const doc = message.document;
      return (
        <div className="vb-chat-file-block">
          <InteractiveFileMessage
            documentId={doc.documentId}
            fileName={doc.fileName}
            contentType={doc.contentType}
            sizeBytes={doc.sizeBytes}
            sentAt={message.sentAt}
            showCollabAction={role === 'AGENT' && isPdfDocument(doc.contentType, doc.fileName)}
            collabBusy={collabBusy}
            onRequestCollab={onRequestCollab}
            onError={onFileError}
          />
        </div>
      );
    }

    if (
      (message.messageType === 'COLLAB_REQUEST' ||
        message.messageType === 'COLLAB_STATUS' ||
        message.messageType === 'COLLAB_CANCEL') &&
      message.collab
    ) {
      const collabId = message.collab.collabId;
      const status = message.collab.status;
      const latestStatus = latestCollabStatusById.get(collabId) ?? status;
      const doc = message.document;
      const isRequested = status === 'REQUESTED';
      const canCancelRequest =
        message.messageType === 'COLLAB_REQUEST' && latestStatus === 'REQUESTED';
      const statusBadge =
        status === 'REQUESTED'
          ? compact
            ? 'Chờ KH xác nhận'
            : '• Đã gửi yêu cầu Collab · Chờ KH xác nhận'
          : status === 'ACTIVE'
            ? compact
              ? 'Đang chia sẻ'
              : '• Đang chia sẻ tài liệu'
            : status === 'REJECTED'
              ? compact
                ? 'KH từ chối'
                : '• Khách từ chối chia sẻ'
              : status === 'ENDED'
                ? compact
                  ? 'Đã kết thúc'
                  : '• Đã kết thúc chia sẻ'
                : `• ${status}`;
      const statusDesc = isRequested
        ? compact
          ? 'Chờ KH xác nhận để xem cùng.'
          : 'Đã gửi lời mời đến KH. Chờ KH xác nhận để bắt đầu xem cùng.'
        : '';
      const showCollabFile = doc && (!compact || isRequested || status === 'ENDED');

      return (
        <div
          className={`vb-collab-card ${compact ? 'vb-collab-card--compact' : ''} ${isRequested ? 'vb-collab-card--request' : ''} vb-collab-card--${status.toLowerCase()}`}
        >
          <div className="vb-collab-card-header">
            <div className="vb-collab-card-icon">
              <CollabCastIcon />
            </div>
            <span className="vb-collab-status-badge">{statusBadge}</span>
          </div>
          {!compact && <h4 className="vb-collab-card-title">Phiên Document Collab</h4>}
          {statusDesc && <p className="vb-collab-card-desc">{statusDesc}</p>}
          {showCollabFile && (
            <FileAttachmentCard
              documentId={doc.documentId}
              fileName={doc.fileName}
              contentType={doc.contentType}
              sizeBytes={doc.sizeBytes}
              sentAt={message.sentAt}
              showTime={isRequested && !compact}
              compact={compact}
            />
          )}
          {role === 'AGENT' && canCancelRequest && onCancelCollab && (
              <button
                type="button"
                className="vb-collab-request-btn vb-collab-request-btn--muted"
                onClick={() => onCancelCollab(message.collab!.collabId)}
                disabled={collabBusy}
              >
                Hủy yêu cầu
              </button>
            )}
        </div>
      );
    }

    return null;
  })();

  if (!body) return null;

  const isCollabMessage =
    message.messageType === 'COLLAB_REQUEST' ||
    message.messageType === 'COLLAB_STATUS' ||
    message.messageType === 'COLLAB_CANCEL';
  const hideCollabTime =
    compact && isCollabMessage && message.messageType !== 'COLLAB_REQUEST';

  return (
    <div
      className={`vb-chat-message ${isOwn ? 'vb-chat-message--own' : 'vb-chat-message--peer'} ${isCollabMessage ? 'vb-chat-message--collab' : ''}`}
    >
      {!isOwn && <span className="vb-chat-avatar">{avatarInitials}</span>}
      <div className="vb-chat-message-body">
        <MessageMeta
          label={label}
          sentAt={message.sentAt}
          align={align}
          showTime={!hideCollabTime}
        />
        {body}
      </div>
    </div>
  );
}

export function ChatPanel({
  sessionId,
  identity,
  name,
  role,
  enabled = true,
  compact = false,
  demoPdf,
  onCollabRequest,
  onCollabStatus,
  onError,
}: ChatPanelProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const composingRef = useRef(false);
  const [draft, setDraft] = useState('');
  const [uploadBusy, setUploadBusy] = useState(false);
  const [collabBusy, setCollabBusy] = useState(false);
  const [demoPdfBusy, setDemoPdfBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  const {
    messages,
    settings,
    connectionState,
    sendText,
    sendFileMessage,
    sendCollabRequest,
    sendCollabCancel,
  } = useSessionChat({
    sessionId,
    identity,
    name,
    role,
    enabled,
    onCollabRequest,
    onCollabStatus,
    onError: (message) => {
      setLocalError(message);
      onError?.(message);
    },
  });

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, [messages.length]);

  async function handleSendText() {
    const text = draft.trim();
    if (!text || composingRef.current) return;
    setLocalError(null);
    setDraft('');
    try {
      await sendText(text);
    } catch (cause) {
      setDraft(text);
      setLocalError(cause instanceof Error ? cause.message : 'Không gửi được tin nhắn.');
    }
  }

  async function handleUpload(file: File) {
    if (!sessionId || !settings) return;
    setLocalError(null);

    if (file.size > settings.maxFileSizeBytes) {
      setLocalError(`File vượt quá giới hạn ${settings.maxFileSizeLabel}`);
      return;
    }

    const ext = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() : '';
    if (ext && !settings.allowedExtensions.includes(ext)) {
      setLocalError(`Chỉ chấp nhận file ${settings.allowedExtensionsLabel}`);
      return;
    }

    setUploadBusy(true);
    try {
      const uploaded = await uploadDocument(sessionId, file);
      await sendFileMessage(uploaded.documentId);
    } catch (cause) {
      setLocalError(cause instanceof Error ? cause.message : 'Không tải lên được file.');
    } finally {
      setUploadBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function handleRequestCollab(documentId: string) {
    setCollabBusy(true);
    setLocalError(null);
    try {
      await sendCollabRequest(documentId);
    } catch (cause) {
      setLocalError(cause instanceof Error ? cause.message : 'Không gửi được yêu cầu collab.');
    } finally {
      setCollabBusy(false);
    }
  }

  async function handleCancelCollab(collabId: string) {
    setCollabBusy(true);
    setLocalError(null);
    try {
      await sendCollabCancel(collabId);
    } catch (cause) {
      setLocalError(cause instanceof Error ? cause.message : 'Không hủy được yêu cầu.');
    } finally {
      setCollabBusy(false);
    }
  }

  async function handleSendDemoPdf() {
    if (!sessionId || !demoPdf) return;
    setDemoPdfBusy(true);
    setLocalError(null);
    try {
      const response = await fetch(demoPdf.url);
      if (!response.ok) {
        throw new Error('Không tải được file PDF mẫu.');
      }
      const blob = await response.blob();
      const file = new File([blob], demoPdf.fileName, { type: 'application/pdf' });
      const uploaded = await uploadDocument(sessionId, file);
      await sendFileMessage(uploaded.documentId);
    } catch (cause) {
      setLocalError(cause instanceof Error ? cause.message : 'Không gửi được PDF mẫu.');
      onError?.(cause instanceof Error ? cause.message : 'Không gửi được PDF mẫu.');
    } finally {
      setDemoPdfBusy(false);
    }
  }

  const accept = settings ? buildFileAccept(settings) : undefined;
  const chatDisabled = !enabled || connectionState !== 'connected';
  const demoBusy = demoPdfBusy || uploadBusy;
  const latestCollabStatusById = useMemo(
    () => buildLatestCollabStatusMap(messages),
    [messages],
  );

  return (
    <aside className={`vb-chat-panel ${compact ? 'vb-chat-panel--compact' : ''}`}>
      <div className="vb-chat-scroll" ref={scrollRef}>
        {demoPdf && role === 'AGENT' && (
          <div className="vb-pdf-message">
            <div className="vb-pdf-bubble vb-pdf-bubble--demo">
              <div className="vb-pdf-icon">PDF</div>
              <div className="vb-pdf-bubble-body">
                <strong>{demoPdf.fileName}</strong>
                <p className="muted">
                  {formatBytes(demoPdf.sizeBytes)} · Mẫu sẵn
                </p>
              </div>
              <button
                type="button"
                className="vb-pdf-send-btn"
                onClick={() => void handleSendDemoPdf()}
                disabled={demoBusy || demoPdf.disabled || chatDisabled}
                title="Gửi PDF mẫu qua chat"
              >
                {demoPdfBusy ? 'Đang gửi…' : 'Gửi'}
              </button>
            </div>
          </div>
        )}

        {messages.length === 0 && !demoPdf && (
          <div className="vb-chat-empty">
            <p>Chưa có tin nhắn.</p>
            <p className="muted">Gửi tin nhắn hoặc đính kèm file PDF, DOC, Excel, ảnh.</p>
          </div>
        )}

        {messages.map((message) => (
          <ChatBubble
            key={message.messageId}
            message={message}
            isOwn={message.senderIdentity === identity}
            role={role}
            compact={compact}
            latestCollabStatusById={latestCollabStatusById}
            onRequestCollab={role === 'AGENT' ? handleRequestCollab : undefined}
            onCancelCollab={role === 'AGENT' ? handleCancelCollab : undefined}
            collabBusy={collabBusy}
            onFileError={setLocalError}
          />
        ))}
      </div>

      <div className="vb-chat-composer">
        <button
          type="button"
          className="vb-chat-plus-btn"
          title="Đính kèm file"
          disabled={uploadBusy || chatDisabled}
          onClick={() => fileInputRef.current?.click()}
        >
          +
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept={accept}
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void handleUpload(file);
          }}
        />
        <div className="vb-chat-input-wrap">
          <input
            type="text"
            className="vb-chat-input"
            placeholder="Nhập tin nhắn…"
            value={draft}
            disabled={chatDisabled}
            onChange={(event) => setDraft(event.target.value)}
            onCompositionStart={() => {
              composingRef.current = true;
            }}
            onCompositionEnd={() => {
              composingRef.current = false;
            }}
            onKeyDown={(event) => {
              if (event.key !== 'Enter' || event.shiftKey) return;
              // IME (Vietnamese, etc.) fires a second Enter after composition ends.
              if (event.nativeEvent.isComposing || composingRef.current || event.keyCode === 229) {
                return;
              }
              event.preventDefault();
              void handleSendText();
            }}
          />
          <button
            type="button"
            className="vb-chat-inline-attach"
            title="Đính kèm file"
            disabled={uploadBusy || chatDisabled}
            onClick={() => fileInputRef.current?.click()}
            aria-label="Đính kèm file"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
              <path
                d="M16.5 6.5L8.8 14.2a3 3 0 104.2 4.2l8.4-8.4a5 5 0 10-7.1 7.1L5.6 19.1"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </div>
        <button
          type="button"
          className="vb-send-btn"
          disabled={chatDisabled || !draft.trim()}
          onClick={() => void handleSendText()}
          aria-label="Gửi"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
            <path
              d="M4 12L20 4l-3.5 8L20 20 4 12z"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </div>

      {uploadBusy && <p className="vb-upload-hint">Đang tải file lên…</p>}
      {localError && <p className="vb-upload-hint vb-upload-hint--error">{localError}</p>}
      {connectionState !== 'connected' && enabled && (
        <p className="vb-upload-hint">
          {connectionState === 'connecting' ? 'Đang kết nối chat…' : 'Mất kết nối chat, đang thử lại…'}
        </p>
      )}
    </aside>
  );
}
