# 12 — Session Chat (WebSocket + History)

## 1. Goal

Thêm **chat trong cuộc gọi** giữa Agent Web và Customer/Mobile:

- Gửi/nhận **text**
- Gửi **file** (PDF, DOC, Excel, ảnh — theo cấu hình) — hiển thị trong timeline chat
- Gửi **yêu cầu Document Collab** ngay trong chat (kèm file đã upload)
- **Realtime** qua **WebSocket** (Spring Boot) — **KHÔNG** dùng LiveKit Data Channel cho chat
- **Lịch sử** lưu PostgreSQL; client **không** gọi API để “lưu tin nhắn” — chỉ gửi/nhận event WS; **backend tự persist** rồi fan-out
- Khi **mất kết nối / vào lại room**: client gọi **REST** để **load lại history**

UI Agent Web tham chiếu mock (sidebar phải màn call):

```text
+-- Chat panel --------------------------------------------------+
|  Hôm nay, 09:38                          (date separator)      |
|  [TU] TuanNT10 · 09:38                                       |
|       ┌─────────────────────────────────────┐                  |
|       │ Chào bạn! Tôi có thể hỗ trợ...      │  (incoming)      |
|       └─────────────────────────────────────┘                  |
|       ┌ PDF card: filename, size, type, time ┐                  |
|       └─────────────────────────────────────┘                  |
|                    ┌──────────────────────────┐                  |
|                    │ Tin nhắn của Agent (You) │  (outgoing)    |
|                    └──────────────────────────┘                  |
|  ┌─ Collab card (blue border) ─────────────────────────────┐   |
|  │ 📺 Đã gửi yêu cầu Collab · Chờ KH xác nhận              │   |
|  │ Phiên Document Collab                                    │   |
|  │ [embedded PDF card]                                      │   |
|  └──────────────────────────────────────────────────────────┘   |
|  [+] [ Nhập tin nhắn...        ] [📎] [➤]                       |
+----------------------------------------------------------------+
```

Doc Collab **điều khiển PDF** (page/pointer/highlight) vẫn dùng LiveKit Data Channel topic `doc-collab` như `docs/06_DOCUMENT_COLLAB_SPEC.md`. Chat chỉ **thông báo / mời / timeline**.

---

## 2. Architecture

```text
Agent Web / Mobile
    |
    |  WebSocket  /api/v1/ws/sessions/{sessionId}/chat
    |  (send/receive chat events only)
    v
Virtual Branch Backend
    |
    +-- ChatWebSocketHandler / SessionChatHub
    |       on inbound event -> validate -> persist -> broadcast
    |
    +-- PostgreSQL (vb_chat_message)
    |
    +-- Object Storage (file binary — upload REST, không qua WS)
    |
    +-- DocCollabService (khi event COLLAB_REQUEST / status sync)

REST (history & binary only):
    GET  /sessions/{sessionId}/chat/messages     <- load history on (re)connect
    POST /sessions/{sessionId}/documents         <- upload file (existing)
    POST /sessions/{sessionId}/doc-collabs     <- optional; prefer WS COLLAB_REQUEST
    POST /doc-collabs/{id}/consent               <- Mobile consent (existing)
```

### Nguyên tắc

| Việc | Kênh |
|------|------|
| Text / file metadata / collab request trong chat | **WebSocket** |
| Lưu lịch sử chat | **Backend tự lưu** khi nhận WS inbound |
| Load lịch sử | **REST GET** (reconnect, refresh) |
| Upload binary file | **REST multipart** (giữ như hiện tại) |
| Consent Doc Collab | **REST** (Mobile) — backend sau đó **push** `COLLAB_STATUS` qua WS |
| Pointer/page/highlight PDF | **LiveKit Data Channel** `doc-collab` (không đổi) |

### Quyền theo role (chat)

| Hành động | AGENT | CUSTOMER |
|-----------|:-----:|:--------:|
| Gửi TEXT | ✓ | ✓ |
| Upload file + gửi FILE trong chat | ✓ | ✓ |
| Gửi COLLAB_REQUEST | ✓ | ✗ |
| Gửi COLLAB_CANCEL | ✓ | ✗ |
| Consent Doc Collab (REST) | — | ✓ |
| Điều khiển PDF (Data Channel) | ✓ | ✗ (chỉ xem) |

Customer **có thể gửi file** (PDF/DOC/Excel/ảnh trong chat) trong timeline chat; **không** được gửi yêu cầu Document Collab — chỉ Agent khởi tạo collab.

---

## 3. WebSocket contract

### 3.1 Endpoint

```text
ws(s)://{backend-host}/api/v1/ws/sessions/{sessionId}/chat
```

Query params (POC — chưa SSO):

| Param | Bắt buộc | Mô tả |
|-------|-----------|--------|
| `identity` | yes | LiveKit participant identity (`agent-…` / customer identity) |
| `role` | yes | `AGENT` \| `CUSTOMER` |
| `name` | no | Display name trong bubble |

Backend validate:

- `sessionId` tồn tại, status `ACTIVE`
- `identity` + `role` khớp session (`agentIdentity` / `customerIdentity`)
- Session `ENDED` → đóng WS với code policy

### 3.2 Envelope (mọi frame JSON)

**Client → Server**

```json
{
  "version": 1,
  "type": "CHAT_SEND",
  "clientMessageId": "cli-uuid",
  "payload": { }
}
```

**Server → Client**

```json
{
  "version": 1,
  "type": "CHAT_MESSAGE",
  "messageId": "MSG-uuid",
  "sessionId": "SES-…",
  "sentAt": "2026-08-28T09:38:00+07:00",
  "senderRole": "AGENT",
  "senderIdentity": "agent-001",
  "senderName": "You",
  "messageType": "TEXT",
  "payload": { },
  "clientMessageId": "cli-uuid"
}
```

`clientMessageId` (optional): idempotency / hiển thị “đang gửi” trên Agent; server echo lại khi persist xong.

**Server → Client (lỗi)**

```json
{
  "version": 1,
  "type": "CHAT_ERROR",
  "code": "SESSION_ENDED",
  "message": "Session has ended",
  "clientMessageId": "cli-uuid"
}
```

Mã lỗi WS thường gặp: `FORBIDDEN`, `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`, `SESSION_ENDED`, `INVALID_REQUEST`.

### 3.3 Event types

#### `CHAT_SEND` — client gửi

`payload` theo `messageType`:

**TEXT**

```json
{
  "messageType": "TEXT",
  "text": "Mình đang xem khoản vay 150tr..."
}
```

**FILE** (file đã upload REST trước đó — **Agent và Customer**)

```json
{
  "messageType": "FILE",
  "documentId": "DOC-uuid"
}
```

Backend kiểm tra `document.sessionId == sessionId` và `document` do đúng `identity` upload (hoặc cùng session). Persist, broadcast.

```json
{
  "documentId": "DOC-uuid",
  "fileName": "Checklist_ho_so_vay.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 98304,
  "readUrl": null
}
```

`readUrl` **không** đưa vào WS broadcast (tránh leak signed URL vào log). Client hiển thị card; khi user mở/tải → `GET /documents/{id}/url` hoặc collab URL.

**COLLAB_REQUEST** (Agent only)

```json
{
  "messageType": "COLLAB_REQUEST",
  "documentId": "DOC-uuid"
}
```

Backend:

1. Validate `role == AGENT`; nếu không → `CHAT_ERROR` `FORBIDDEN`
2. Validate `documentId` là **PDF** (`application/pdf` / `.pdf`); DOC/Excel/ảnh → `FILE_TYPE_NOT_ALLOWED` cho collab
3. Gọi `DocCollabService.startCollab(sessionId, documentId)` (hoặc tương đương)
4. Persist chat row `messageType=COLLAB_REQUEST`
5. Broadcast `CHAT_MESSAGE` với `collabId`, `collabStatus=REQUESTED`, embedded file metadata

**COLLAB_CANCEL** (Agent only, optional phase 1.1)

```json
{
  "messageType": "COLLAB_CANCEL",
  "collabId": "COL-uuid"
}
```

Gọi `endCollab` nếu status `REQUESTED`.

#### `CHAT_MESSAGE` — server fan-out

Đã persist; mọi subscriber cùng `sessionId` nhận (gồm sender).

**COLLAB_STATUS** — server push (không do client chat gửi)

Khi Mobile gọi REST consent / Agent end collab, backend inject:

```json
{
  "type": "CHAT_MESSAGE",
  "messageType": "COLLAB_STATUS",
  "payload": {
    "collabId": "COL-uuid",
    "collabStatus": "ACTIVE",
    "documentId": "DOC-uuid"
  }
}
```

Map status: `REQUESTED` | `ACTIVE` | `REJECTED` | `ENDED` | `EXPIRED`.

#### `CHAT_ACK` (optional)

Server ack ngay khi nhận `CHAT_SEND` (trước persist) — phase 2 nếu cần UX “sending…”.

### 3.4 Giới hạn POC

| Giới hạn | Giá trị |
|----------|---------|
| Max text length | 4 000 ký tự |
| Max WS frame | 8 KB |
| Rate limit | 30 msg / phút / identity |
| **Định dạng file (chat)** | **PDF, DOC, Excel, ảnh** mặc định — **cấu hình**, không hard-code |
| **Max file size (chat)** | **&lt; 3 MB** mặc định — **cấu hình**, không hard-code trong code |

### 3.5 Giới hạn kích thước file (chat)

File gửi trong chat (Agent hoặc Customer) phải **nhỏ hơn** ngưỡng cấu hình. Mặc định POC: **3 MB** (`3 * 1024 * 1024` bytes).

**Cấu hình** (`application.yml` — không hard-code trong service):

```yaml
virtual-branch:
  chat:
    max-file-size-bytes: ${VB_CHAT_MAX_FILE_SIZE_BYTES:3145728}  # 3 MB
    allowed-content-types: ${VB_CHAT_ALLOWED_CONTENT_TYPES:application/pdf,application/msword,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,image/jpeg,image/png,image/gif,image/webp}
    allowed-extensions: ${VB_CHAT_ALLOWED_EXTENSIONS:pdf,doc,xls,xlsx,jpg,jpeg,png,gif,webp}
```

| Env | Mô tả |
|-----|--------|
| `VB_CHAT_MAX_FILE_SIZE_BYTES` | Override bytes (VD `2097152` = 2 MB) |
| `VB_CHAT_ALLOWED_CONTENT_TYPES` | MIME cho phép, phân tách bằng dấu phẩy |
| `VB_CHAT_ALLOWED_EXTENSIONS` | Extension (không dấu chấm), phân tách bằng dấu phẩy |

**Mặc định POC:**

| Extension | MIME |
|-----------|------|
| `.pdf` | `application/pdf` |
| `.doc` | `application/msword` |
| `.xls` | `application/vnd.ms-excel` |
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `.jpg` / `.jpeg` | `image/jpeg` |
| `.png` | `image/png` |
| `.gif` | `image/gif` |
| `.webp` | `image/webp` |

Backend đọc qua `@ConfigurationProperties` (`ChatProperties`). `DocumentService` (hoặc `ChatFileValidator`) dùng cùng config khi validate `POST /documents`.

Validate **cả** `Content-Type` **và** extension (lowercase). Object key lưu đúng extension upload, không ép `.pdf` cho mọi file.

`spring.servlet.multipart.max-file-size` giữ **≥** `chat.max-file-size-bytes` (VD `4MB`) để Spring nhận request trước khi app trả lỗi nghiệp vụ rõ ràng.

**Khi vượt ngưỡng:**

1. **Client (khuyến nghị):** trước `POST /documents`, so `file.size` với limit lấy từ `GET /api/v1/chat/settings` (hoặc constant build-time sync env) → hiện toast/banner, **không** gọi upload.
2. **REST upload** — HTTP **400**:

```json
{
  "code": "FILE_TOO_LARGE",
  "message": "File vượt quá giới hạn 3 MB cho chat",
  "timestamp": "..."
}
```

`message` có thể format từ config (hiển thị MB làm tròn). Không log tên file/PII ở DEBUG.

3. **WS `CHAT_SEND` FILE** với `documentId` đã tồn tại nhưng `file_size` &gt; limit (dữ liệu cũ / race): `CHAT_ERROR` `FILE_TOO_LARGE`.

### 3.6 Định dạng file được phép (chat)

Chỉ chấp nhận loại trong `chat.allowed-content-types` / `chat.allowed-extensions` (mặc định **PDF + DOC + Excel + ảnh**). Mở rộng sau bằng config, không sửa code.

**Doc Collab:** chỉ **PDF** — DOC / Excel / **ảnh** **không** có nút “Yêu cầu xem cùng” / `COLLAB_REQUEST` (đính kèm / tải xem / preview ảnh trong bubble).

Có thể cấu hình riêng (không bắt buộc phase 1):

```yaml
virtual-branch:
  chat:
    collab-allowed-extensions: ${VB_CHAT_COLLAB_EXTENSIONS:pdf}
    collab-allowed-content-types: ${VB_CHAT_COLLAB_CONTENT_TYPES:application/pdf}
```

Mặc định collab ⊆ allow-list chat; chỉ PDF.

**Khi sai định dạng:**

1. **Client:** `input accept` theo `GET /chat/settings`; kiểm tra extension/MIME trước upload.
2. **REST** — HTTP **400**:

```json
{
  "code": "FILE_TYPE_NOT_ALLOWED",
  "message": "Chỉ chấp nhận file PDF, DOC, Excel hoặc ảnh (JPG, PNG, GIF, WEBP)",
  "timestamp": "..."
}
```

3. **WS:** `CHAT_ERROR` `FILE_TYPE_NOT_ALLOWED`.

**`GET /api/v1/chat/settings`** (size + types):

```http
GET /api/v1/chat/settings
```

```json
{
  "maxFileSizeBytes": 3145728,
  "maxFileSizeLabel": "3 MB",
  "allowedContentTypes": [
    "application/pdf",
    "application/msword",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp"
  ],
  "allowedExtensions": ["pdf", "doc", "xls", "xlsx", "jpg", "jpeg", "png", "gif", "webp"],
  "allowedExtensionsLabel": "PDF, DOC, Excel, ảnh (JPG, PNG, GIF, WEBP)"
}
```

Agent Web / Mobile: `accept` build từ settings. Tin **FILE** loại ảnh có thể hiển thị **thumbnail** trong bubble (gọi `GET /documents/{id}/url` khi render — không gửi URL qua WS).

---

## 4. REST — History only

Client **không** POST để lưu chat. Chỉ GET history.

### 4.1 List messages

```http
GET /api/v1/sessions/{sessionId}/chat/messages
```

Query:

| Param | Mô tả |
|-------|--------|
| `after` | `messageId` hoặc ISO timestamp — pagination |
| `limit` | default 50, max 200 |

Response:

```json
{
  "sessionId": "SES-…",
  "messages": [
    {
      "messageId": "MSG-…",
      "sentAt": "2026-08-28T09:38:00+07:00",
      "senderRole": "CUSTOMER",
      "senderIdentity": "cust-1",
      "senderName": "TuanNT10",
      "messageType": "TEXT",
      "text": "Chào bạn!...",
      "document": null,
      "collab": null
    },
    {
      "messageId": "MSG-…",
      "messageType": "FILE",
      "document": {
        "documentId": "DOC-…",
        "fileName": "Checklist_ho_so_vay.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 98304
      }
    },
    {
      "messageId": "MSG-…",
      "messageType": "COLLAB_REQUEST",
      "collab": {
        "collabId": "COL-…",
        "status": "REQUESTED",
        "documentId": "DOC-…"
      },
      "document": { "fileName": "...", "sizeBytes": 98304 }
    }
  ],
  "hasMore": false
}
```

Gọi khi:

- Agent/Mobile **vào call** (sau khi session `ACTIVE`)
- **Reconnect** WebSocket
- App resume từ background (Mobile)

Thứ tự: `GET history` → render → `connect WebSocket` → nhận tin mới.

### 4.2 Upload file (giữ API hiện tại)

```http
POST /api/v1/sessions/{sessionId}/documents
Content-Type: multipart/form-data
```

**Validation:** `file.size < chat.max-file-size-bytes` và extension/MIME thuộc allow-list. Vượt size → `FILE_TOO_LARGE`; sai loại → `FILE_TYPE_NOT_ALLOWED` (§3.5–3.6).

Sau upload thành công, client gửi WS `CHAT_SEND` `messageType=FILE` với `documentId`.

**Luồng gửi file trong chat (Agent hoặc Customer):**

```text
1. User chọn file (📎 hoặc +) — PDF/DOC/Excel/ảnh theo settings
2. Client kiểm tra extension/MIME + size < maxFileSizeBytes
   -> sai loại: "Chỉ chấp nhận PDF, DOC, Excel hoặc ảnh"
   -> quá size: "File vượt quá giới hạn X MB"
3. POST /documents  -> documentId (backend validate lại)
4. WS CHAT_SEND FILE { documentId }
5. Backend persist + broadcast -> cả 2 bên thấy card file
```

**Luồng Collab từ chat (chỉ Agent):**

```text
1. Đã có documentId **PDF** (upload hoặc tin FILE trước đó)
2. Agent bấm "Yêu cầu xem cùng" trên card PDF HOẶC gửi COLLAB_REQUEST
3. WS CHAT_SEND COLLAB_REQUEST { documentId }   (backend từ chối nếu role=CUSTOMER)
4. Backend start collab + persist + broadcast card collab
5. Mobile nhận WS -> hiện popup consent (vẫn REST consent)
6. Sau consent -> backend push COLLAB_STATUS ACTIVE qua WS
7. Doc sync vẫn qua LiveKit doc-collab
```

UI Agent: nút **Yêu cầu xem cùng** chỉ trên card **PDF** (DOC/Excel/ảnh chỉ tải/xem/preview). Mobile **không** có nút collab.

---

## 5. Database

### 5.1 Migration `V5__session_chat.sql`

```sql
CREATE TABLE vb_chat_message (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL REFERENCES vb_session(id),
    sender_role     VARCHAR(16) NOT NULL,
    sender_identity VARCHAR(128) NOT NULL,
    sender_name     VARCHAR(256),
    message_type    VARCHAR(32) NOT NULL,
    text_body       TEXT,
    document_id     VARCHAR(64),
    collab_id       VARCHAR(64),
    collab_status   VARCHAR(32),
    client_message_id VARCHAR(64),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_chat_message_type CHECK (
        message_type IN ('TEXT', 'FILE', 'COLLAB_REQUEST', 'COLLAB_STATUS', 'COLLAB_CANCEL')
    )
);

CREATE INDEX idx_chat_message_session_sent ON vb_chat_message(session_id, sent_at);
CREATE INDEX idx_chat_message_session_id ON vb_chat_message(session_id, id);
```

Không lưu `readUrl` / token trong DB.

### 5.2 Entity / service (backend)

```text
com.example.virtualbranch.chat/
  ChatProperties.java         # max-file-size-bytes from config
  ChatMessageEntity.java
  ChatMessageType.java
  ChatMessageRepository.java
  ChatService.java          # persist, list history, build DTO
  ChatWebSocketConfig.java
  SessionChatWebSocketHandler.java
  ChatWsMessageMapper.java
  dto/
    ChatMessageResponse.java
    ChatHistoryResponse.java
    ws/ ... (envelope records)
```

`ChatService.onInbound(sessionId, sender, ChatSendCommand)`:

1. Validate session + role rules
2. Side effects (upload ref, start collab)
3. `save(message)`
4. `broadcast(sessionId, ChatMessageResponse)`

---

## 6. Agent Web (implementation scope)

### 6.1 Structure

```text
agent-web/src/chat/
  types.ts              # WS + REST DTOs
  events.ts             # type constants
  useSessionChat.ts     # WS connect, send, onMessage, reconnect
  ChatPanel.tsx         # UI sidebar (extract từ AgentCallPage)
  ChatMessageList.tsx
  ChatMessageBubble.tsx # TEXT / FILE / COLLAB cards
  ChatComposer.tsx      # +, input, attach, send
  formatChatDate.ts     # "Hôm nay, HH:mm"
```

### 6.2 `useSessionChat` behavior

```text
on mount (session ACTIVE):
  GET /chat/messages -> setMessages
  open WebSocket
  on message CHAT_MESSAGE -> append (dedupe by messageId)
  on close -> exponential backoff reconnect -> on open GET messages(after=lastId)

sendText(text):
  WS CHAT_SEND TEXT

sendFile(file):
  validate extension/MIME + size vs GET /chat/settings
  POST /documents
  WS CHAT_SEND FILE

requestCollab(documentId):
  WS CHAT_SEND COLLAB_REQUEST
```

Optimistic UI (optional): hiển thị bubble pending với `clientMessageId`; replace khi nhận `CHAT_MESSAGE` cùng `clientMessageId`.

### 6.3 UI mapping (ảnh mock)

| Element | Class / component |
|---------|-------------------|
| Date separator | `vb-chat-date-sep` |
| Incoming row | avatar initials, name, time, white bubble |
| Outgoing row | "You", time right, blue bubble |
| File card | icon theo loại; **ảnh**: thumbnail inline; document: name + `size · type · time` |
| Collab card | blue border, badge trạng thái, title, nested file card |
| Composer | `+` menu (future), input, 📎, send |

Outgoing = `senderRole === AGENT` trên Agent Web.

### 6.4 AgentCallPage changes

- Thay block `vb-chat-panel` stub bằng `<ChatPanel sessionId=… identity role=AGENT />`
- Giữ upload/collab logic cũ có thể **delegate** vào `ChatPanel` (một entry point)
- Doc Collab viewer / Data Channel **không** đổi

---

## 7. Mobile (contract only — implement sau)

1. `GET /chat/messages` khi vào call
2. WebSocket cùng endpoint + query `identity`, `role=CUSTOMER`
3. Gửi text: `CHAT_SEND` TEXT
4. Gửi file: `POST /documents` → `CHAT_SEND` FILE (Customer được upload; **không** gửi `COLLAB_REQUEST`)
5. Nhận FILE / COLLAB_REQUEST từ Agent → UI tương tự; card collab không có nút “gửi collab”
6. Consent collab: REST hiện tại; listen `COLLAB_STATUS` trên WS
7. Cập nhật `docs/11_MOBILE_API.md` (bỏ hạn chế “không upload” cho chat file nếu còn ghi)

---

## 8. Security (POC)

- Không log nội dung WS có PII ở level DEBUG
- Không broadcast signed URL qua WS
- Validate `documentId` thuộc `sessionId`
- Validate file size theo `ChatProperties.maxFileSizeBytes` — không magic number trong code
- Validate MIME + extension theo `ChatProperties.allowed*` — mặc định PDF/DOC/Excel/ảnh; **collab chỉ PDF**
- `COLLAB_REQUEST` / `COLLAB_CANCEL`: **AGENT only** — Customer gửi → `CHAT_ERROR` code `FORBIDDEN`
- Customer: **TEXT + FILE**; không collab request
- Mở rộng `POST /documents` cho Customer trong session `ACTIVE` (hiện API không chặn role; cập nhật Mobile doc cho phù hợp)
- Thay `DocumentService` constant `ALLOWED_CONTENT_TYPES` / `50 MB` bằng inject `ChatProperties`

---

## 9. Implementation order

| Phase | Deliverable |
|-------|-------------|
| **12.1** | Flyway `V5`, entity, `ChatProperties`, `GET /chat/settings`, `GET /chat/messages`, `FILE_TOO_LARGE` trên upload |
| **12.2** | WebSocket handler, persist on inbound, broadcast |
| **12.3** | Agent Web `useSessionChat` + `ChatPanel` UI theo mock |
| **12.4** | FILE + COLLAB_REQUEST trong chat; sync COLLAB_STATUS từ `DocCollabService` |
| **12.5** | Mobile doc + Customer test page WS chat (optional POC) |
| **12.6** | Runbook + `docs/08_IMPLEMENTATION_CHECKLIST.md` tick |

**Definition of Done (chat):**

```text
Agent gửi text -> Mobile thấy realtime (WS)
Customer gửi text/file -> Agent thấy realtime (WS)
Agent upload PDF -> card FILE trong chat cả 2 bên
Customer upload file -> card FILE (không có nút collab phía Mobile)
Upload file > maxFileSizeBytes -> FILE_TOO_LARGE
Upload file sai định dạng -> FILE_TYPE_NOT_ALLOWED
Chỉ PDF mới COLLAB_REQUEST; DOC/Excel/ảnh trong chat không collab
Agent gửi COLLAB_REQUEST trong chat -> Mobile thấy card + consent REST vẫn hoạt động
Customer gửi COLLAB_REQUEST -> backend từ chối (FORBIDDEN)
Disconnect -> reconnect -> GET history khôi phục đủ timeline
Backend có rows trong vb_chat_message; client không gọi POST save chat
```

---

## 10. Out of scope (phase này)

- LiveKit Data Channel cho chat
- Typing indicator, read receipt, reaction
- Push notification khi app background
- Full-text search, export chat
- Enterprise SSO trên WebSocket
- Customer khởi tạo Document Collab (chỉ Agent)

---

## 11. Related docs

- `docs/06_DOCUMENT_COLLAB_SPEC.md` — PDF sync qua Data Channel
- `docs/11_MOBILE_API.md` — Mobile REST (bổ sung mục Chat sau phase 12.2)
- `docs/04_AGENT_WEB_SPEC.md` — layout call page
- `AGENTS.md` — không đưa secret/signed URL ra client
