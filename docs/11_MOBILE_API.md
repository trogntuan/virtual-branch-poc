# 11 — Danh sách API & Event cho Mobile (Customer)

Tài liệu tham chiếu cho client **Customer / Mobile**.

| | |
|--|--|
| Base URL | `https://vb-backend-215046327377.asia-southeast1.run.app` |
| Prefix | `/api/v1` |
| Content-Type | `application/json` |
| Auth POC | Không có |
| Vai trò | `CUSTOMER` — không gửi Data Channel, không upload PDF, không recording |

`serverUrl` LiveKit lấy từ API token, không hardcode secret.

---

## 0. Envelope lỗi (mọi REST 4xx/5xx)

**Response**

```json
{
  "code": "CALL_NOT_ACCEPTED",
  "message": "Call has not been accepted by an agent yet",
  "timestamp": "2026-08-20T04:50:55.462960878Z"
}
```

| `code` | HTTP | Ý nghĩa |
|--------|------|---------|
| `INVALID_REQUEST` | 400 | Thiếu/sai field |
| `INVALID_ROLE` | 400 | `role` không hợp lệ |
| `INVALID_CONSENT` | 400 | `decision` không phải `ACCEPT`/`REJECT` |
| `CALL_NOT_ACCEPTED` | 400 | Agent chưa nhận cuộc — chưa lấy token |
| `COLLAB_CONSENT_REQUIRED` | 403 | Chưa ACCEPT mà lấy PDF URL |
| `SESSION_NOT_FOUND` | 404 | Sai `sessionId` |
| `COLLAB_NOT_FOUND` | 404 | Sai `collabId` |
| `SESSION_ENDED` | 409 | Session đã kết thúc |
| `COLLAB_INVALID_STATE` | 409 | Collab không đúng trạng thái |
| `TOKEN_GENERATION_FAILED` | 500 | Không sinh được JWT LiveKit |
| `INTERNAL_ERROR` | 500 | Lỗi không mong đợi |

---

## 1. REST API

### 1.1 Health check

| | |
|--|--|
| **Chức năng** | Kiểm tra backend sống |
| **Method / Endpoint** | `GET /api/v1/health` |
| **Request** | Không body |
| **Response 200** | `{ "status": "UP" }` |

---

### 1.2 Yêu cầu cuộc gọi

| | |
|--|--|
| **Chức năng** | Tạo session, đưa khách vào hàng chờ GDV |
| **Method / Endpoint** | `POST /api/v1/calls` |

**Request**

```json
{
  "identity": "cust-001",
  "name": "Nguyễn Văn A",
  "viewportWidth": 390,
  "viewportHeight": 844,
  "devicePixelRatio": 3.0,
  "orientation": "PORTRAIT"
}
```

| Field | Type | Bắt buộc | Mô tả |
|-------|------|----------|-------|
| `identity` | string | ✓ | ID ổn định của user trên app |
| `name` | string | ✓ | Tên hiển thị cho GDV |
| `viewportWidth` | number | ✓ | Chiều rộng viewport (px), > 0 |
| `viewportHeight` | number | ✓ | Chiều cao viewport (px), > 0 |
| `devicePixelRatio` | number | ✓ | DPR, > 0 |
| `orientation` | string | ✓ | `PORTRAIT` hoặc `LANDSCAPE` |

**Response 200 — SessionResponse**

```json
{
  "sessionId": "SES-89d71ec8-4007-4009-9ab3-3b60937cf443",
  "roomName": "VB-89d71ec8-4007-4009-9ab3-3b60937cf443",
  "status": "WAITING",
  "createdAt": "2026-08-20T04:50:55.462960878Z",
  "endedAt": null,
  "customerIdentity": "cust-001",
  "customerName": "Nguyễn Văn A",
  "agentIdentity": null,
  "agentName": null,
  "acceptedAt": null
}
```

| Field | Mô tả |
|-------|-------|
| `sessionId` | Dùng cho mọi API session sau đó |
| `roomName` | Tên room LiveKit (tham khảo; join bằng token) |
| `status` | `WAITING` \| `ACTIVE` \| `ENDED` |
| `endedAt` | Thời điểm kết thúc, `null` nếu chưa |
| `acceptedAt` | Thời điểm GDV nhận, `null` nếu chưa |

Sau API này: poll **1.3** đến `ACTIVE`.

---

### 1.3 Lấy trạng thái session

| | |
|--|--|
| **Chức năng** | Poll xem GDV đã nhận cuộc chưa |
| **Method / Endpoint** | `GET /api/v1/sessions/{sessionId}` |
| **Path** | `sessionId` — từ 1.2 |
| **Request** | Không body |
| **Response 200** | Cùng `SessionResponse` (1.2) |

Poll mỗi 1–2 giây khi `WAITING`. Dừng khi `ACTIVE` hoặc `ENDED`.

---

### 1.4 Lấy token LiveKit

| | |
|--|--|
| **Chức năng** | Sinh JWT để mobile join room (audio/video) |
| **Method / Endpoint** | `POST /api/v1/sessions/{sessionId}/token` |
| **Điều kiện** | `status == ACTIVE` |

**Request**

```json
{
  "identity": "cust-001",
  "name": "Nguyễn Văn A",
  "role": "CUSTOMER"
}
```

| Field | Type | Bắt buộc | Mô tả |
|-------|------|----------|-------|
| `identity` | string | ✓ | Trùng `identity` lúc tạo cuộc gọi |
| `name` | string | | Tên hiển thị trên LiveKit |
| `role` | string | ✓ | Luôn `CUSTOMER` |

**Response 200**

```json
{
  "serverUrl": "wss://virtual-branch-burlw8j5.livekit.cloud",
  "roomName": "VB-89d71ec8-4007-4009-9ab3-3b60937cf443",
  "participantToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| Field | Mô tả |
|-------|-------|
| `serverUrl` | URL WebSocket LiveKit (`wss://…`) |
| `roomName` | Room cần join |
| `participantToken` | JWT, TTL 6 giờ. Không log |

Join: `connect(serverUrl, participantToken)` → publish mic/camera, subscribe track GDV, subscribe Data Channel topic `doc-collab`.

Grant token CUSTOMER: publish A/V = true, subscribe = true, **publish data = false**.

---

### 1.5 Cập nhật kích thước màn hình

| | |
|--|--|
| **Chức năng** | Báo viewport khi xoay máy / đổi orientation |
| **Method / Endpoint** | `PUT /api/v1/sessions/{sessionId}/mobile-display` |

**Request / Response 200** (cùng schema)

```json
{
  "viewportWidth": 844,
  "viewportHeight": 390,
  "devicePixelRatio": 3.0,
  "orientation": "LANDSCAPE"
}
```

---

### 1.6 Đọc kích thước màn hình đã lưu

| | |
|--|--|
| **Chức năng** | Lấy viewport hiện tại của session |
| **Method / Endpoint** | `GET /api/v1/sessions/{sessionId}/mobile-display` |
| **Request** | Không body |
| **Response 200** | Cùng schema 1.5; field có thể `null` nếu chưa gửi 1.5 |

---

### 1.7 Kết thúc cuộc gọi

| | |
|--|--|
| **Chức năng** | User/app kết thúc session |
| **Method / Endpoint** | `POST /api/v1/sessions/{sessionId}/end` |
| **Request** | Không body |
| **Response 200** | `SessionResponse` với `status: "ENDED"` |

Sau đó disconnect LiveKit, đóng PDF viewer.

---

### 1.8 Gửi consent xem tài liệu

| | |
|--|--|
| **Chức năng** | Đồng ý hoặc từ chối xem PDF sau khi nhận event `COLLAB_REQUEST` |
| **Method / Endpoint** | `POST /api/v1/doc-collabs/{collabId}/consent` |
| **Path** | `collabId` — từ event Data Channel |

**Request**

```json
{ "decision": "ACCEPT" }
```

| `decision` | Kết quả |
|------------|---------|
| `ACCEPT` | `status` → `ACTIVE`, được lấy URL PDF |
| `REJECT` | `status` → `REJECTED`, đóng dialog, tiếp tục video |

**Response 200**

```json
{
  "collabId": "COLLAB-abc-123",
  "sessionId": "SES-89d71ec8-4007-4009-9ab3-3b60937cf443",
  "documentId": "DOC-xyz-456",
  "status": "ACTIVE",
  "consentDecision": "ACCEPT"
}
```

| `status` | Ý nghĩa |
|----------|---------|
| `REQUESTED` | Chờ consent |
| `ACTIVE` | Được mở PDF |
| `REJECTED` | User từ chối |
| `ENDED` | GDV đã đóng collab |

---

### 1.9 Lấy trạng thái collab

| | |
|--|--|
| **Chức năng** | Poll collab nếu cần (thường không bắt buộc nếu REST 1.8 đủ) |
| **Method / Endpoint** | `GET /api/v1/doc-collabs/{collabId}` |
| **Request** | Không body |
| **Response 200** | Cùng schema 1.8 |

---

### 1.10 Lấy URL đọc PDF

| | |
|--|--|
| **Chức năng** | Presigned URL để tải/render PDF (read-only) |
| **Method / Endpoint** | `GET /api/v1/doc-collabs/{collabId}/document-url` |
| **Điều kiện** | Collab `ACTIVE` và `consentDecision == ACCEPT` |

**Request** — không body.

**Response 200**

```json
{
  "documentId": "DOC-xyz-456",
  "readUrl": "https://storage.googleapis.com/project-4cd8e655-vb-poc/documents/...?X-Goog-Signature=...",
  "expiresInSeconds": 600
}
```

| Field | Mô tả |
|-------|-------|
| `readUrl` | GET HTTP thuần, không kèm token app. Hết hạn 600s → gọi lại 1.10 |
| `expiresInSeconds` | Thời gian sống URL (10 phút) |

Không log full `readUrl`. Đóng viewer khi nhận event `COLLAB_END`.

---

## 2. LiveKit Data Channel events

Mobile **chỉ nhận**. Không `publishData`.

| | |
|--|--|
| Transport | LiveKit Data Channel |
| Topic | `doc-collab` |
| Encoding | UTF-8 JSON |
| Hướng | Agent → Customer |

### Envelope (mọi event)

```json
{
  "version": 1,
  "type": "COLLAB_REQUEST",
  "collabId": "COLLAB-abc-123",
  "sessionId": "SES-89d71ec8-4007-4009-9ab3-3b60937cf443",
  "documentId": "DOC-xyz-456",
  "sequence": 1,
  "timestamp": 1787112000000,
  "data": {}
}
```

| Field | Mô tả |
|-------|-------|
| `version` | Hiện = `1` |
| `type` | Tên event (bảng dưới) |
| `collabId` | ID collab — dùng cho REST 1.8–1.10 |
| `sessionId` | Phải khớp cuộc gọi hiện tại, nếu không thì bỏ |
| `documentId` | ID tài liệu |
| `sequence` | Tăng dần. Event reliable: bỏ qua sequence cũ |
| `timestamp` | Epoch millis |
| `data` | Payload theo `type` |

Toạ độ `x`, `y`, `width`, `height` ∈ **[0, 1] theo trang PDF** (không phải pixel màn hình).

```text
xPx = x * renderedPageWidth
yPx = y * renderedPageHeight
```

Chỉ vẽ pointer/highlight khi `page` trùng trang đang mở.

---

### 2.1 `COLLAB_REQUEST`

| | |
|--|--|
| **Chức năng** | GDV mời khách xem PDF → hiện dialog consent |
| **Reliable** | Có |

**`data`**

```json
{ "fileName": "hop-dong.pdf" }
```

Tiếp theo: gọi REST **1.8**.

---

### 2.2 `DOC_STATE`

| | |
|--|--|
| **Chức năng** | Đồng bộ toàn bộ trạng thái viewer (trang, zoom, pointer, highlight) |
| **Reliable** | Có |

**`data`**

```json
{
  "page": 3,
  "viewMode": "FIT_WIDTH",
  "zoomScale": 1.0,
  "scrollRatio": 0.42,
  "pointer": { "visible": true, "page": 3, "x": 0.45, "y": 0.31 },
  "highlight": {
    "visible": true,
    "page": 3,
    "x": 0.20,
    "y": 0.35,
    "width": 0.40,
    "height": 0.05
  }
}
```

`viewMode`: `FIT_WIDTH` | `FIT_PAGE` | `CUSTOM`

---

### 2.3 `PAGE_CHANGE`

| | |
|--|--|
| **Chức năng** | GDV đổi trang PDF |
| **Reliable** | Có |

**`data`**

```json
{ "page": 3 }
```

---

### 2.4 `VIEWPORT_CHANGE`

| | |
|--|--|
| **Chức năng** | GDV scroll / đổi fit / zoom (có thể mất gói) |
| **Reliable** | Không (lossy) |

**`data`**

```json
{
  "page": 3,
  "scrollRatio": 0.42,
  "viewMode": "FIT_WIDTH",
  "zoomScale": 1.0
}
```

---

### 2.5 `POINTER_MOVE`

| | |
|--|--|
| **Chức năng** | GDV di chuyển con trỏ trên trang |
| **Reliable** | Không (lossy) |

**`data`**

```json
{
  "page": 3,
  "visible": true,
  "x": 0.45,
  "y": 0.31
}
```

---

### 2.6 `POINTER_HIDE`

| | |
|--|--|
| **Chức năng** | Ẩn con trỏ GDV |
| **Reliable** | Không (lossy) |

**`data`:** `{}` hoặc không dùng. Set `pointer.visible = false`.

---

### 2.7 `HIGHLIGHT_SET`

| | |
|--|--|
| **Chức năng** | GDV khoanh vùng trên trang |
| **Reliable** | Có |

**`data`**

```json
{
  "page": 3,
  "x": 0.20,
  "y": 0.35,
  "width": 0.40,
  "height": 0.05
}
```

---

### 2.8 `HIGHLIGHT_CLEAR`

| | |
|--|--|
| **Chức năng** | Xóa highlight |
| **Reliable** | Có |

**`data`:** `{}`

---

### 2.9 `COLLAB_END`

| | |
|--|--|
| **Chức năng** | GDV kết thúc collab → đóng PDF viewer |
| **Reliable** | Có |

**`data`**

```json
{ "reason": "AGENT_ENDED" }
```

---

## 3. Thứ tự gọi (tóm tắt)

```text
1.2 POST /calls
1.3 GET /sessions/{id}          (loop đến ACTIVE)
1.4 POST /sessions/{id}/token
    → LiveKit connect + subscribe doc-collab
2.1 COLLAB_REQUEST
1.8 POST /doc-collabs/{id}/consent
1.10 GET /doc-collabs/{id}/document-url
    → render PDF + apply 2.2–2.8
2.9 COLLAB_END                  (đóng viewer)
1.7 POST /sessions/{id}/end     (user thoát)
```
