# 10 — Triển khai GCP + LiveKit Cloud (mobile thật)

Hướng dẫn đưa Virtual Branch POC lên Google Cloud, dùng **LiveKit Cloud** cho WebRTC (không tự host LiveKit/Egress/Redis).

## Kiến trúc

```text
Mobile app (iOS/Android)
  |  HTTPS
  v
Cloud Run — Backend API  ---- Cloud SQL (PostgreSQL)
  |                              GCS (PDF + MP4)
  |
  | sinh token, điều khiển egress
  v
LiveKit Cloud  <--- WebRTC wss ---> Mobile + Agent Web

Agent Web (Cloud Storage + Load Balancer / Firebase Hosting)
  | HTTPS /api (proxy hoặc trực tiếp)
  v
Backend
```

**Không cần:** GCE VM LiveKit, Redis local, container Egress, mở UDP firewall.

**Vẫn cần:** Backend public HTTPS, GCS, Cloud SQL, tài khoản LiveKit Cloud.

---

## Bước 1 — LiveKit Cloud

1. Tạo project tại [cloud.livekit.io](https://cloud.livekit.io).
2. Lấy credentials:
   - **API Key**
   - **API Secret**
   - **WebSocket URL** (dạng `wss://your-project.livekit.cloud`)
3. Bật **Egress** trên plan (recording cần egress cloud).
4. (Khuyến nghị) Cấu hình **default S3/GCS output** trên dashboard LiveKit Cloud **hoặc** truyền S3/GCS trong mỗi request egress (backend POC đã hỗ trợ truyền S3-compatible cho GCS).

Lưu vào Secret Manager — **không commit** secret.

---

## Bước 2 — Google Cloud

### 2.1 Tạo project & bật API

```bash
gcloud config set project YOUR_PROJECT_ID
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com \
  artifactregistry.googleapis.com
```

### 2.2 Cloud SQL (PostgreSQL)

```bash
gcloud sql instances create vb-poc-db \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region=asia-southeast1

gcloud sql databases create virtual_branch --instance=vb-poc-db

gcloud sql users create virtual_branch \
  --instance=vb-poc-db \
  --password='STRONG_PASSWORD_HERE'
```

Ghi nhận **connection name**: `PROJECT:asia-southeast1:vb-poc-db`.

### 2.3 GCS bucket

```bash
gsutil mb -l asia-southeast1 gs://YOUR_PROJECT-vb-poc
```

Tạo **HMAC key** cho S3-compatible (Interoperability) — dùng cho backend upload PDF và LiveKit egress ghi MP4:

1. Cloud Console → Cloud Storage → Settings → Interoperability → Create HMAC key (service account storage).
2. Lưu **Access key** + **Secret**.

CORS cho Agent Web tải PDF qua signed URL:

```json
[
  {
    "origin": ["https://agent.YOUR_DOMAIN.com"],
    "method": ["GET", "HEAD"],
    "responseHeader": ["Content-Type", "Content-Length", "Content-Range"],
    "maxAgeSeconds": 3600
  }
]
```

```bash
gsutil cors set infra/gcp/gcs-cors.json gs://YOUR_PROJECT-vb-poc
```

### 2.4 Secret Manager

```bash
echo -n 'STRONG_PASSWORD_HERE' | gcloud secrets create vb-db-password --data-file=-
echo -n 'LIVEKIT_API_SECRET' | gcloud secrets create vb-livekit-secret --data-file=-
echo -n 'GCS_HMAC_SECRET' | gcloud secrets create vb-storage-secret --data-file=-
```

---

## Bước 3 — Build & deploy Backend (Cloud Run)

### 3.1 Biến môi trường production

Copy mẫu `infra/gcp/.env.example` và điền:

| Biến | Ví dụ |
|------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql:///virtual_branch?cloudSqlInstance=PROJECT:asia-southeast1:vb-poc-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory` |
| `SPRING_DATASOURCE_USERNAME` | `virtual_branch` |
| `SPRING_DATASOURCE_PASSWORD` | từ Secret Manager |
| `LIVEKIT_API_KEY` | từ LiveKit Cloud |
| `LIVEKIT_API_SECRET` | từ Secret Manager |
| `VB_LIVEKIT_API_URL` | `https://your-project.livekit.cloud` |
| `VB_LIVEKIT_WS_URL` | `wss://your-project.livekit.cloud` |
| `STORAGE_ACCESS_KEY` | GCS HMAC access key |
| `STORAGE_SECRET_KEY` | GCS HMAC secret |
| `VB_STORAGE_ENDPOINT` | `https://storage.googleapis.com` |
| `VB_STORAGE_BUCKET` | `YOUR_PROJECT-vb-poc` |
| `VB_STORAGE_REGION` | `auto` hoặc để trống |
| `VB_CORS_ALLOWED_ORIGINS` | `https://agent.YOUR_DOMAIN.com` |

### 3.2 Build image

```bash
cd virtual-branch-backend
gcloud builds submit --tag asia-southeast1-docker.pkg.dev/YOUR_PROJECT/vb/backend:latest
```

(Trước đó tạo Artifact Registry repo `vb`.)

### 3.3 Deploy Cloud Run

```bash
gcloud run deploy vb-backend \
  --image asia-southeast1-docker.pkg.dev/YOUR_PROJECT/vb/backend:latest \
  --region asia-southeast1 \
  --allow-unauthenticated \
  --add-cloudsql-instances PROJECT:asia-southeast1:vb-poc-db \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod,... \
  --set-secrets SPRING_DATASOURCE_PASSWORD=vb-db-password:latest,LIVEKIT_API_SECRET=vb-livekit-secret:latest,STORAGE_SECRET_KEY=vb-storage-secret:latest \
  --memory 512Mi \
  --cpu 1
```

Ghi URL: `https://vb-backend-xxxxx.asia-southeast1.run.app`

Verify:

```bash
curl -s https://vb-backend-xxxxx.run.app/api/v1/health
```

---

## Bước 4 — Deploy Agent Web

Build static + nginx (proxy `/api` → Cloud Run):

```bash
cd agent-web
npm ci && npm run build
docker build -t vb-agent-web .
```

Upload lên Cloud Storage + HTTPS Load Balancer, hoặc deploy container Cloud Run.

**Quan trọng:** `nginx.conf` proxy `/api` tới backend URL. Agent Web và mobile **không** giữ LiveKit secret.

Domain đề xuất:
- `https://api.YOUR_DOMAIN.com` → Cloud Run backend
- `https://agent.YOUR_DOMAIN.com` → Agent Web

---

## Bước 5 — Mobile app tích hợp

Base URL backend: `https://api.YOUR_DOMAIN.com`

### Luồng cuộc gọi

```http
POST /api/v1/calls
Content-Type: application/json

{
  "customerIdentity": "device-uuid",
  "customerName": "Nguyễn Văn A",
  "mobileDisplay": {
    "viewportWidth": 390,
    "viewportHeight": 844,
    "devicePixelRatio": 3,
    "orientation": "PORTRAIT"
  }
}
```

Poll `GET /api/v1/sessions/{sessionId}` đến khi `status = ACTIVE`.

```http
POST /api/v1/sessions/{sessionId}/token
Content-Type: application/json

{
  "identity": "device-uuid",
  "displayName": "Nguyễn Văn A",
  "role": "CUSTOMER"
}
```

Response:

```json
{
  "serverUrl": "wss://your-project.livekit.cloud",
  "participantToken": "...",
  "roomName": "..."
}
```

Mobile dùng **LiveKit iOS/Android SDK**:

```text
room.connect(serverUrl, participantToken)
→ bật mic/camera
```

### Doc Collab

- Lắng nghe Data Channel topic `doc-collab`
- `POST /api/v1/doc-collabs/{collabId}/consent` với `ACCEPT` / `REJECT`
- Sau ACCEPT: `GET /api/v1/doc-collabs/{collabId}/document-url`
- Pointer/highlight: tọa độ **0..1 theo trang PDF** (xem `docs/06_DOCUMENT_COLLAB_SPEC.md`)

Chi tiết API: `docs/03_API_AND_DB.md`.

---

## Bước 6 — Agent vận hành

1. Mở `https://agent.YOUR_DOMAIN.com/agent`
2. Chờ cuộc gọi → **Nhận cuộc gọi**
3. Bật camera/mic → ghi hình / chia sẻ PDF như local runbook

---

## Recording trên LiveKit Cloud

Local POC dùng Egress container + MinIO. Trên cloud:

- Egress chạy trên **LiveKit Cloud**
- Backend gọi `startRoomCompositeEgress` qua LiveKit API URL
- Output MP4 ghi vào **GCS** (S3-compatible HMAC) — backend truyền block `s3` trong request egress

Điều kiện ghi thành công:
- Agent + Mobile đã publish video/audio
- GCS HMAC key đúng quyền `storage.objectCreator`
- Bucket tồn tại

---

## So sánh local vs cloud

| Thành phần | Local | GCP + LiveKit Cloud |
|------------|-------|---------------------|
| LiveKit | Host `:7880` | LiveKit Cloud `wss://` |
| Redis | Docker | Không cần (LiveKit Cloud) |
| Egress | Docker container | LiveKit Cloud egress |
| PostgreSQL | Docker | Cloud SQL |
| Object storage | MinIO | GCS |
| Backend | `:8080` host | Cloud Run |
| Agent Web | Vite `:5173` | GCS / Cloud Run + nginx |
| Firewall UDP | Cần cấu hình | Không cần |

---

## Checklist trước khi giao mobile

- [ ] `GET /api/v1/health` → UP (HTTPS public)
- [ ] Mobile `POST /calls` từ **4G** (không VPN)
- [ ] Agent nhận cuộc gọi → mobile `ACTIVE` → join LiveKit
- [ ] Audio/video 2 chiều ≥ 2 phút
- [ ] Recording → MP4 trong GCS → playback URL mở được
- [ ] PDF upload + doc collab + consent gate
- [ ] Không lộ `LIVEKIT_API_SECRET` / GCS secret trong app mobile
- [ ] CORS agent domain trên backend + GCS

---

## Chi phí ước lượng POC

| Dịch vụ | USD/tháng (ước lượng) |
|---------|------------------------|
| LiveKit Cloud | theo phút/participant (xem pricing) |
| Cloud Run | 5–30 |
| Cloud SQL db-f1-micro | 10–25 |
| GCS + egress | 5–15 |
| **Tổng GCP** | ~20–70 + LiveKit usage |

---

## Troubleshooting

### Mobile không có video (chỉ signaling OK)

- Kiểm tra `serverUrl` trả về là `wss://` LiveKit Cloud, không phải `ws://localhost`
- Token chưa hết hạn
- Quyền camera/mic trên mobile

### Recording FAILED

- LiveKit Cloud egress chưa bật
- **Local MinIO + LiveKit Cloud:** egress cloud không reach được `localhost:9000` → cấu hình GCS HMAC (`VB_STORAGE_ENDPOINT=https://storage.googleapis.com`, `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY`)
- Thiếu S3 block trong egress request → kiểm tra GCS HMAC env
- Chưa publish media trước khi Start Recording

### PDF không tải trên Agent/Mobile

- GCS CORS có origin agent domain
- Presigned URL chưa hết hạn
- Doc collab status = ACTIVE (customer đã consent)

### Backend không kết nối DB

- Cloud SQL instance connection name đúng trong JDBC URL
- Cloud Run có `--add-cloudsql-instances`
- Thêm dependency Cloud SQL socket factory (xem Dockerfile prod)

---

## File liên quan trong repo

```text
infra/gcp/.env.example          # mẫu biến môi trường
infra/gcp/gcs-cors.json         # CORS bucket GCS
virtual-branch-backend/Dockerfile
virtual-branch-backend/src/main/resources/application-prod.yml
agent-web/Dockerfile
agent-web/nginx.conf
```

Local dev vẫn dùng `infra/docker-compose.yml` + LiveKit host như `docs/07_RUNBOOK.md`.
