# 06 — Document Collaboration — Shared PDF + LiveKit Data Channel

## 1. Goal

Doc Collab sử dụng mô hình:

```text
Agent Web và Customer/Mobile cùng mở cùng một file PDF
+
Agent là controller
+
Customer chỉ xem
+
Các thao tác của Agent được đồng bộ sang Mobile qua LiveKit Data Channel
```

KHÔNG dùng Screen Share cho Doc Collab.

Voice/Video vẫn chạy bằng WebRTC qua LiveKit như bình thường.

Mục tiêu UX:

> Mobile nhìn thấy tài liệu gần giống trạng thái Agent đang nhìn: cùng file, cùng page, cùng vùng scroll, cùng highlight và cùng vị trí pointer.

---

## 2. Main flow

```text
1. Mobile request/join cuộc gọi
   + gửi viewportWidth / viewportHeight / devicePixelRatio / orientation
                     |
                     v
            Virtual Branch Backend
                     |
                     v
2. Agent Web nhận Mobile display profile
                     |
                     v
3. Agent mở PDF trong một preview frame có cùng aspect ratio/kích thước logic với Mobile
                     |
                     v
4. Agent chọn file PDF
                     |
                     v
5. Backend upload file -> Object Storage
                     |
                     +---- temporary URL cho Agent
                     |
                     +---- temporary URL cho Mobile sau khi consent ACCEPTED
                     |
                     v
6. Agent bấm "Request Doc Collab"
                     |
                     v
7. Mobile nhận COLLAB_REQUEST và hiển thị popup xin phép
                     |
            Reject --+-- Accept
                     |
                     v
8. Mobile gọi Backend consent ACCEPT
                     |
                     v
9. Backend chuyển Collab -> ACTIVE
                     |
                     v
10. Mobile lấy temporary URL và mở cùng PDF ở READ_ONLY
                     |
                     v
11. Agent gửi realtime events qua LiveKit Data Channel
    - DOC_STATE
    - PAGE_CHANGE
    - VIEWPORT_CHANGE
    - POINTER_MOVE
    - HIGHLIGHT_SET / HIGHLIGHT_CLEAR
                     |
                     v
12. Mobile apply event và render tương ứng
```

---

## 3. Architecture

```text
                         BUSINESS / CONTROL

Customer Mobile
    |
    | REST
    | - request call + mobile display profile
    | - accept/reject Doc Collab
    | - get document URL after consent
    v
Virtual Branch Backend
    |
    +---------------- PostgreSQL
    |
    +---------------- Object Storage
    |
    +---------------- LiveKit token/control


                         DOCUMENT STORAGE

Agent Web
    |
    | multipart upload
    v
Virtual Branch Backend
    |
    | S3 API
    v
Object Storage
    |
    +------ temporary URL ------> Agent Web PDF Viewer
    |
    +------ temporary URL ------> Mobile PDF Viewer
                                   only after consent ACCEPTED


                         REALTIME COLLAB

Agent Web
    |
    | LiveKit Data Channel
    | topic = doc-collab
    v
LiveKit OSS
    |
    | Data Channel
    v
Customer Mobile
    |
    v
Read-only PDF Viewer


                         MEDIA

Agent Web  <---- WebRTC ----> LiveKit OSS <---- WebRTC ----> Mobile
               voice/video
```

---

## 4. Important rule: both sides render the same PDF

Agent và Mobile đều tải PDF gốc từ Object Storage.

```text
Object Storage
      |
      +---- PDF ----> Agent PDF Viewer
      |
      +---- PDF ----> Mobile PDF Viewer
```

Data Channel KHÔNG mang file PDF.

Data Channel chỉ mang state/event nhỏ:

```text
page
scroll
pointer
highlight
zoom/view mode
collab lifecycle
```

---

## 5. Mobile screen/display profile

Khi Mobile request cuộc gọi, request phải gửi kèm thông tin màn hình.

Ví dụ:

```json
{
  "customerIdentity": "customer-001",
  "mobileDisplay": {
    "viewportWidth": 390,
    "viewportHeight": 844,
    "devicePixelRatio": 3,
    "orientation": "PORTRAIT"
  }
}
```

Backend lưu profile này theo Session.

Minimum fields:

```text
viewportWidth
viewportHeight
devicePixelRatio
orientation
updatedAt
```

Không dùng physical resolution để đồng bộ tọa độ.

`devicePixelRatio` chủ yếu dùng cho preview/debug/render quality.

Thông tin quan trọng nhất cho Agent preview:

```text
viewportWidth
viewportHeight
aspectRatio
orientation
```

---

## 6. Agent Mobile Preview Frame

Agent Web phải tạo một khung preview theo profile Mobile.

Ví dụ Mobile:

```text
390 x 844
PORTRAIT
```

Agent UI:

```text
+--------------------------------------------------+
| Customer Mobile Preview                         |
| 390 x 844 | Portrait                            |
|                                                |
|       +----------------------------+           |
|       |                            |           |
|       |         PDF VIEWER         |           |
|       |                            |           |
|       |   highlighted text         |           |
|       |                 ● pointer  |           |
|       |                            |           |
|       +----------------------------+           |
|                                                |
| Prev | Next | Zoom | Highlight | Request Collab|
+--------------------------------------------------+
```

Trong browser không bắt buộc render đúng 390 physical pixels.

Có thể scale preview lớn hơn để Agent dễ nhìn nhưng phải giữ đúng:

```text
aspect ratio
viewer layout rules
page fit mode
```

---

## 7. Why raw pixels are NOT enough

Mặc dù Agent preview theo kích thước Mobile, vẫn KHÔNG gửi raw pixel:

```json
{
  "x": 723,
  "y": 421
}
```

vì Agent Web và Mobile có thể khác:

```text
screen density
devicePixelRatio
PDF renderer
browser/native SDK
zoom
CSS scale
```

Tất cả position event phải dùng normalized coordinates hoặc ratio.

Ví dụ:

```json
{
  "x": 0.42,
  "y": 0.31
}
```

Trong đó:

```text
x = localX / renderedPageWidth
y = localY / renderedPageHeight
```

Mobile:

```text
actualX = x * mobileRenderedPageWidth
actualY = y * mobileRenderedPageHeight
```

---

## 8. Viewer behavior must be standardized

Để Agent và Mobile hiển thị gần giống nhau, cả hai viewer phải tuân cùng viewer state.

POC nên support:

```text
viewMode = FIT_WIDTH
page
zoomScale
scrollRatio
```

Recommended initial default:

```text
viewMode = FIT_WIDTH
zoomScale = 1.0
```

Nếu Mobile app sử dụng native PDF SDK khác PDF.js, Mobile adapter phải map state này sang API của SDK tương ứng.

Không dựa vào pixel scroll tuyệt đối.

---

## 9. PDF technology

### Agent Web

Recommended:

```text
PDF.js
```

Responsibilities:

```text
load PDF
render page
page navigation
zoom
obtain rendered page bounds
calculate normalized coordinates
```

### Mobile

Mobile có thể dùng:
- Android PDF viewer
- iOS PDFKit
- Flutter PDF package
- PDF.js nếu Mobile Web

Không bắt buộc cùng thư viện với Agent.

Nhưng Mobile phải implement adapter cho contract:

```text
openDocument(url)
goToPage(page)
setViewMode(mode)
setZoom(scale)
setScrollRatio(ratio)
showPointer(x,y)
showHighlight(rect)
clearHighlight()
```

---

## 10. Highlight technology on Agent

POC có 2 lựa chọn.

### Option A — HTML/CSS overlay

Recommended nếu chỉ cần:
- rectangle highlight
- pointer
- simple overlay

Structure:

```text
PDF page
  +
absolute-position overlay
  +
highlight div
  +
pointer div
```

### Option B — Fabric.js

Dùng nếu cần:
- kéo chọn vùng
- resize vùng highlight
- nhiều shape
- richer annotation

POC hiện tại chỉ cần highlight nên ưu tiên HTML/CSS overlay hoặc Canvas 2D đơn giản.

---

## 11. Document upload flow

Agent chọn PDF.

```text
Agent Web
   |
   | POST multipart/form-data
   v
Backend
   |
   | validate
   | upload
   v
Object Storage
```

API:

```http
POST /api/v1/sessions/{sessionId}/documents
Content-Type: multipart/form-data
```

Response cho Agent:

```json
{
  "documentId": "DOC-123",
  "fileName": "contract.pdf",
  "contentType": "application/pdf",
  "size": 123456,
  "agentReadUrl": "temporary-url"
}
```

Recommended object key:

```text
documents/{sessionId}/{documentId}.pdf
```

Không dùng original filename làm object key.

Không lưu signed URL vào DB.

---

## 12. Customer must NOT receive document URL before consent

Rule bắt buộc:

```text
Agent uploads file
        |
        v
Object Storage
        |
        X
        |
Mobile chưa được URL


Agent Request Collab
        |
        v
Mobile popup
        |
      ACCEPT
        |
        v
Backend confirms consent
        |
        v
Mobile may request temporary PDF URL
```

API:

```http
GET /api/v1/doc-collabs/{collabId}/document-url
```

Backend validate:

```text
collab.status == ACTIVE
consent.status == ACCEPTED
document belongs to collab/session
requester is the session customer
```

Response:

```json
{
  "documentId": "DOC-123",
  "readUrl": "temporary-url",
  "expiresInSeconds": 600
}
```

---

## 13. Doc Collab lifecycle

```text
CREATED
   |
   v
REQUESTED
   |
   +----> REJECTED
   |
   +----> EXPIRED
   |
   v
ACCEPTED
   |
   v
ACTIVE
   |
   v
ENDED
```

POC có thể gộp `ACCEPTED -> ACTIVE` trong cùng transaction sau khi Mobile accept.

---

## 14. Start/request Doc Collab

Agent gọi:

```http
POST /api/v1/sessions/{sessionId}/doc-collabs
```

Request:

```json
{
  "documentId": "DOC-123"
}
```

Response:

```json
{
  "collabId": "COLLAB-123",
  "status": "REQUESTED",
  "documentId": "DOC-123"
}
```

Agent gửi Data Channel event:

```json
{
  "version": 1,
  "type": "COLLAB_REQUEST",
  "collabId": "COLLAB-123",
  "sessionId": "SES-123",
  "documentId": "DOC-123",
  "sequence": 1,
  "timestamp": 1787112000000,
  "data": {
    "fileName": "contract.pdf"
  }
}
```

`COLLAB_REQUEST` KHÔNG chứa PDF URL.

---

## 15. Mobile consent

Mobile nhận `COLLAB_REQUEST` và hiển thị:

```text
+----------------------------------------+
| GDV muốn cùng xem tài liệu            |
|                                        |
| contract.pdf                           |
|                                        |
| Trong phiên cộng tác, GDV có thể:      |
| - chuyển trang                         |
| - cuộn tài liệu                        |
| - trỏ vào nội dung                     |
| - highlight vùng cần giải thích        |
|                                        |
| Bạn chỉ xem và không chỉnh sửa file.   |
|                                        |
| [Từ chối]                 [Đồng ý]     |
+----------------------------------------+
```

Mobile gửi consent bằng REST:

```http
POST /api/v1/doc-collabs/{collabId}/consent
```

Accept:

```json
{
  "decision": "ACCEPT"
}
```

Reject:

```json
{
  "decision": "REJECT"
}
```

Lý do dùng REST:
- backend persist consent
- backend mới cấp quyền lấy PDF URL
- Customer không cần `canPublishData=true`
- không tin client-only consent

---

## 16. Agent biết Mobile đã accept

POC dùng polling:

```http
GET /api/v1/doc-collabs/{collabId}
```

mỗi khoảng:

```text
1 second
```

cho tới:

```text
ACTIVE / REJECTED / EXPIRED
```

Phase sau có thể thay bằng Backend WebSocket/SSE.

---

## 17. Mobile mở document

Sau khi status `ACTIVE`:

```text
Mobile
  |
  | GET document-url
  v
Backend
  |
  | temporary/presigned URL
  v
Mobile
  |
  v
Read-only PDF Viewer
```

Sau khi Mobile mở xong, Agent gửi `DOC_STATE` hiện tại để đồng bộ view.

---

## 18. Data Channel topic

```text
doc-collab
```

Payload:

```text
UTF-8 JSON
```

Base envelope:

```json
{
  "version": 1,
  "type": "PAGE_CHANGE",
  "collabId": "COLLAB-123",
  "sessionId": "SES-123",
  "documentId": "DOC-123",
  "sequence": 10,
  "timestamp": 1787112000000,
  "data": {}
}
```

Mobile phải validate:

```text
sessionId == current session
collabId == ACTIVE collab
documentId == opened document
sender == AGENT
```

Unknown event -> ignore safely.

---

## 19. Required events

### 19.1 COLLAB_REQUEST

```text
Reliable
```

Không chứa URL.

### 19.2 DOC_STATE

Dùng:
- ngay sau consent
- reconnect
- resync

```text
Reliable
```

Example:

```json
{
  "type": "DOC_STATE",
  "data": {
    "page": 3,
    "viewMode": "FIT_WIDTH",
    "zoomScale": 1.0,
    "scrollRatio": 0.42,
    "pointer": {
      "visible": true,
      "x": 0.45,
      "y": 0.31
    },
    "highlight": {
      "visible": true,
      "x": 0.20,
      "y": 0.35,
      "width": 0.40,
      "height": 0.05
    }
  }
}
```

### 19.3 PAGE_CHANGE

```text
Reliable
```

```json
{
  "type": "PAGE_CHANGE",
  "data": {
    "page": 4
  }
}
```

### 19.4 VIEWPORT_CHANGE

Dùng cho scroll/view state.

```text
Lossy
```

```json
{
  "type": "VIEWPORT_CHANGE",
  "data": {
    "page": 4,
    "scrollRatio": 0.63,
    "viewMode": "FIT_WIDTH",
    "zoomScale": 1.0
  }
}
```

Throttle:

```text
50-100 ms
```

Khi Agent dừng scroll, có thể gửi thêm một `DOC_STATE` Reliable cuối cùng.

### 19.5 POINTER_MOVE

```text
Lossy
```

```json
{
  "type": "POINTER_MOVE",
  "data": {
    "page": 4,
    "visible": true,
    "x": 0.42,
    "y": 0.31
  }
}
```

Throttle:

```text
30-60 ms
```

### 19.6 POINTER_HIDE

```text
Lossy
```

### 19.7 HIGHLIGHT_SET

```text
Reliable
```

```json
{
  "type": "HIGHLIGHT_SET",
  "data": {
    "page": 4,
    "x": 0.20,
    "y": 0.35,
    "width": 0.40,
    "height": 0.05
  }
}
```

### 19.8 HIGHLIGHT_CLEAR

```text
Reliable
```

### 19.9 COLLAB_END

```text
Reliable
```

```json
{
  "type": "COLLAB_END",
  "data": {
    "reason": "AGENT_ENDED"
  }
}
```

Mobile:
- clear pointer/highlight
- close Doc Collab viewer
- return normal call UI

---

## 20. Reliable vs Lossy

| Event | Mode |
|---|---|
| COLLAB_REQUEST | Reliable |
| DOC_STATE | Reliable |
| PAGE_CHANGE | Reliable |
| VIEWPORT_CHANGE | Lossy + final snapshot |
| POINTER_MOVE | Lossy |
| POINTER_HIDE | Lossy |
| HIGHLIGHT_SET | Reliable |
| HIGHLIGHT_CLEAR | Reliable |
| COLLAB_END | Reliable |

---

## 21. Coordinate model

Pointer/highlight coordinate phải relative với PDF PAGE, không phải:
- browser window
- preview frame
- phone screen

Normalized:

```text
x      0..1
y      0..1
width  0..1
height 0..1
```

Agent:

```text
x = (mouseX - pageRect.left) / pageRect.width
y = (mouseY - pageRect.top) / pageRect.height
```

Mobile:

```text
xPx = x * mobilePageWidth
yPx = y * mobilePageHeight
```

Clamp tất cả về `[0,1]`.

---

## 22. Scroll synchronization

Không gửi:

```text
scrollTop = 1845px
```

Gửi:

```text
page
scrollRatio
```

Recommended POC:

```text
page-level scrollRatio
```

Cả Agent và Mobile phải dùng cùng định nghĩa.

---

## 23. Zoom synchronization

Use application-level values:

```text
FIT_WIDTH
FIT_PAGE
CUSTOM
```

POC recommendation:

```text
FIT_WIDTH only
```

Nếu không có business requirement zoom tự do, bỏ custom zoom ở version đầu để giảm mismatch Web/Mobile.

---

## 24. Sequence and ordering

Agent tăng:

```text
sequence = 1,2,3...
```

Mobile giữ:

```text
lastSequence
```

Rule:

```text
old/duplicate stateful event -> ignore
new stateful event -> apply
```

Lossy event có thể bị mất nên sequence gap của `POINTER_MOVE`/`VIEWPORT_CHANGE` không được coi là fatal.

Khi cần resync -> Agent gửi `DOC_STATE`.

---

## 25. Reconnect

Data Channel không phải durable event history.

Nếu Mobile reconnect:

```text
1. reconnect LiveKit
2. GET active Collab từ Backend
3. ensure PDF same documentId
4. Agent resend DOC_STATE
5. Mobile apply current state
```

Không replay từng scroll/pointer packet đã miss.

---

## 26. Permission

Agent:

```text
canPublish=true
canSubscribe=true
canPublishData=true
```

Customer:

```text
canPublish=true
canSubscribe=true
canPublishData=false
```

Customer vẫn publish camera/mic.

Customer consent đi REST nên không cần publish Data Channel.

---

## 27. Database

### VB_SESSION

```text
MOBILE_VIEWPORT_WIDTH
MOBILE_VIEWPORT_HEIGHT
MOBILE_DEVICE_PIXEL_RATIO
MOBILE_ORIENTATION
MOBILE_DISPLAY_UPDATED_AT
```

### VB_DOCUMENT

```text
ID
SESSION_ID
FILE_NAME
CONTENT_TYPE
FILE_SIZE
OBJECT_KEY
CHECKSUM
CREATED_AT
```

### VB_DOC_COLLAB

Recommended:

```text
ID
SESSION_ID
DOCUMENT_ID
STATUS

REQUESTED_AT
CONSENT_DECISION
CONSENT_AT

CURRENT_PAGE
CURRENT_SCROLL_RATIO
VIEW_MODE
ZOOM_SCALE

STARTED_AT
ENDED_AT
END_REASON
```

Không persist mọi pointer/scroll event.

---

## 28. Backend APIs

### Session/request call

Request phải support:

```json
{
  "mobileDisplay": {
    "viewportWidth": 390,
    "viewportHeight": 844,
    "devicePixelRatio": 3,
    "orientation": "PORTRAIT"
  }
}
```

Optional update:

```http
PUT /api/v1/sessions/{sessionId}/mobile-display
```

### Upload PDF

```http
POST /api/v1/sessions/{sessionId}/documents
```

### Request Collab

```http
POST /api/v1/sessions/{sessionId}/doc-collabs
```

### Consent

```http
POST /api/v1/doc-collabs/{collabId}/consent
```

### Get Collab

```http
GET /api/v1/doc-collabs/{collabId}
```

### Get Mobile-authorized PDF URL

```http
GET /api/v1/doc-collabs/{collabId}/document-url
```

Only allowed when:

```text
ACTIVE + ACCEPT
```

### End

```http
POST /api/v1/doc-collabs/{collabId}/end
```

---

## 29. Agent implementation state

Suggested:

```typescript
type DocCollabState = {
  collabId: string | null;
  documentId: string | null;
  status: 'IDLE' | 'REQUESTED' | 'ACTIVE' | 'ENDED';
  page: number;
  viewMode: 'FIT_WIDTH' | 'FIT_PAGE' | 'CUSTOM';
  zoomScale: number;
  scrollRatio: number;
  pointer?: {
    visible: boolean;
    x: number;
    y: number;
  };
  highlight?: {
    visible: boolean;
    x: number;
    y: number;
    width: number;
    height: number;
  };
  sequence: number;
};
```

Chỉ emit control events khi:

```text
status == ACTIVE
```

Trước khi Mobile accept:
- Agent được preview local
- không bắt đầu stream event

---

## 30. Mobile state

```text
COLLAB_REQUEST
      |
      v
show consent
      |
    ACCEPT
      |
      v
POST consent
      |
      v
GET document-url
      |
      v
open PDF read-only
      |
      v
receive DOC_STATE
      |
      v
follow Agent events
```

Customer không có:
- page controls trong active follow mode
- highlight controls
- annotation controls
- pointer controls

---

## 31. Storage URL rules

Không lưu signed URL trong DB.

Không expose storage secret.

Recommended TTL:

```text
5-15 minutes
```

Nếu URL hết hạn trong khi Collab ACTIVE:
- Mobile gọi Backend refresh URL
- Backend kiểm tra Collab vẫn ACTIVE

---

## 32. Recording interaction

Voice/Video recording vẫn:

```text
LiveKit Room
-> Egress
-> MP4
```

Doc Collab bằng Data Channel + PDF viewer không tự động xuất hiện trong RoomComposite recording.

Nếu sau này cần record đúng giao diện PDF mà Mobile đã xem, cần một giải pháp riêng:
- screen-share/composite
- custom web egress layout
- hoặc event replay/archive

Không nằm trong POC Doc Collab hiện tại.

---

## 33. Acceptance criteria

1. Mobile request/session gửi viewport width/height/DPR/orientation.
2. Backend persist display profile.
3. Agent preview giữ đúng aspect ratio Mobile.
4. Agent upload PDF lên Object Storage.
5. Mobile không lấy được URL trước consent.
6. Agent tạo Collab `REQUESTED`.
7. Mobile nhận `COLLAB_REQUEST`.
8. Mobile hiển thị consent.
9. Reject -> không mở PDF, state `REJECTED`.
10. Accept -> backend persist consent, state `ACTIVE`.
11. Mobile lấy được temporary PDF URL sau ACCEPT.
12. Hai bên mở cùng PDF.
13. Mobile viewer read-only.
14. Agent page change -> Mobile theo.
15. Agent scroll -> Mobile theo.
16. Agent pointer -> Mobile thấy đúng logical position.
17. Agent highlight -> Mobile thấy đúng logical region.
18. DPR/render size khác nhau vẫn đúng nhờ normalized coordinates.
19. Customer không publish Data Channel control event.
20. Reconnect resync bằng `DOC_STATE`.
21. End Collab dừng event stream và Mobile về call UI.

---

## 34. Out of scope

Không làm:
- Screen Share cho Doc Collab
- PDF binary qua Data Channel
- Customer edit PDF
- two-way document control
- CRDT/OT
- Word/Excel native collaboration
- multi-agent collaboration
- remote desktop
- permanent DB log cho mọi pointer/scroll packet
