# 07 — Local Runbook

Full demo script for Virtual Branch POC on a clean machine.

## Prerequisites checklist

- [ ] Docker Desktop running
- [ ] Java 17+ (`java -version`)
- [ ] Node.js 18+ (`node -version`)
- [ ] LiveKit Server installed (`livekit-server --version`)
- [ ] Ports free: 5432, 6379, 7880, 8080, 5173, 9000, 9001

## LiveKit Cloud + GCS (no local MinIO)

Use this mode when WebRTC goes through LiveKit Cloud and files go to Google Cloud Storage.

### One-time GCS setup

```bash
cd infra/gcp
./setup-gcs-storage.sh
gcloud auth application-default login
```

Add to `infra/.env`:

```bash
VB_STORAGE_PROVIDER=gcs
VB_STORAGE_BUCKET=project-4cd8e655-vb-poc
VB_STORAGE_ENDPOINT=https://storage.googleapis.com
VB_STORAGE_FORCE_PATH_STYLE=true
VB_LIVEKIT_API_URL=https://your-project.livekit.cloud
VB_LIVEKIT_WS_URL=wss://your-project.livekit.cloud
LIVEKIT_API_KEY=...
LIVEKIT_API_SECRET=...
```

For **recording** (LiveKit Cloud egress), use **Cloudflare R2** — separate from GCS PDF storage:

1. [Cloudflare Dashboard](https://dash.cloudflare.com) → **R2** → create bucket (e.g. `virtual-branch-recordings`)
2. **Manage R2 API Tokens** → Create token with **Object Read & Write** on that bucket
3. Add to `infra/.env`:

```bash
VB_EGRESS_BUCKET=virtual-branch-recordings
VB_EGRESS_ENDPOINT=https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com
VB_EGRESS_ACCESS_KEY=...
VB_EGRESS_SECRET_KEY=...
VB_EGRESS_REGION=auto
VB_EGRESS_FORCE_PATH_STYLE=true
```

4. Verify: `infra/r2/verify-r2.sh`
5. Restart backend, start a **new call**, then test recording.

> GCS HMAC and SA keys are **not** used for LiveKit Cloud egress in this POC. PDFs stay on GCS; MP4 recordings go to R2.

Docker infra (MinIO not required):

```bash
cd infra
docker compose up -d postgres redis
```

Backend + Agent Web:

```bash
cd virtual-branch-backend
set -a && source ../infra/.env && set +a
SERVER_PORT=8081 ./mvnw spring-boot:run

cd agent-web
VB_BACKEND_URL=http://localhost:8081 npm run dev
```

---

## Start order

### 1. Docker infra

```bash
cd infra
cp -n .env.example .env
docker compose up -d postgres redis minio minio-init
docker compose ps    # postgres, redis, minio = healthy
```

### 2. LiveKit on host

```bash
cd infra
livekit-server --config livekit.yaml
```

Keep this terminal open. Log should show Redis connected on `:6379`.

### 3. Egress

```bash
cd infra
docker compose up -d egress
docker compose logs egress --tail 20
```

### 4. Backend

```bash
cd virtual-branch-backend
export LIVEKIT_API_SECRET=virtual_branch_poc_dev_secret_2026
./mvnw spring-boot:run
```

Verify:

```bash
curl -s http://localhost:8080/api/v1/health | jq
# {"status":"UP"}
```

### 5. Agent Web

```bash
cd agent-web
npm install
npm run dev
```

Open:
- Agent: http://localhost:5173/agent
- Customer: http://localhost:5173/customer-test

## Demo sequence

Use two browsers (or one normal + one incognito).

### A. Voice / video

1. **Agent** → `/agent` → **Start Session** → allow camera/mic.
2. Copy **Session ID**.
3. **Customer** → `/customer-test` → paste Session ID → **Join Room** → allow camera/mic.
4. Verify:
   - Both see remote video/audio
   - Connection badge = **Connected**
   - Mic/camera toggle works on both sides

### B. Recording

5. **Agent** → **Start Recording** → talk for ~10 seconds → **Stop Recording**.
6. Wait for status `COMPLETED` (poll every few seconds).
7. Click **Open MP4** playback link.
8. Verify MP4 in MinIO:

```bash
docker run --rm --network infra_default \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin123 \
  quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z \
  /bin/sh -c 'mc alias set local http://minio:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD && mc ls local/virtual-branch/recordings/ --recursive'
```

### C. PDF upload

9. **Agent** → choose a `.pdf` file → upload completes.
10. PDF renders in Mobile Preview Frame (Prev/Next works).

### D. Doc Collab

11. **Agent** → **Request Doc Collab**.
12. **Customer** sees consent popup → **Accept**.
13. Customer PDF viewer opens (read-only).
14. **Agent** actions → verify on Customer:
    - Change page (Prev/Next)
    - Move mouse over PDF (pointer dot)
    - Draw highlight (Highlight mode → drag)
15. **Agent** → **Clear Highlight** / **Resync State** if needed after reconnect.

### E. Consent gate (optional verify)

Before Customer accepts, this must return 403:

```bash
curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8080/api/v1/doc-collabs/COLLAB-<uuid>/document-url
```

### F. Cleanup

16. **Agent** → **End Doc Collab**.
17. **Agent** → **End Session**.
18. **Customer** → **Leave**.

## Clean-machine rehearsal

Run the full demo sequence above on a machine that has never run this POC before.

Pass criteria:

- [ ] All 5 terminals start without errors
- [ ] Health check returns UP
- [ ] Two-browser voice/video works
- [ ] MP4 appears in MinIO after recording
- [ ] PDF upload renders on Agent
- [ ] Customer cannot get PDF URL before consent
- [ ] Page/pointer/highlight sync after consent
- [ ] No secrets visible in browser DevTools → Network (only participant tokens, no API secret)

## Troubleshooting

### PostgreSQL

```bash
docker compose ps
docker compose logs postgres
docker exec -it vb-postgres psql -U virtual_branch -d virtual_branch -c "SELECT 1;"
```

### Redis

```bash
docker exec -it vb-redis redis-cli ping
# PONG
```

### Backend won't start

```bash
# Port 8080 in use?
lsof -i :8080
```

Flyway migration failed → check PostgreSQL is healthy and prior schema is compatible.

### Egress does not receive job

Verify:
- LiveKit running before Egress starts
- LiveKit and Egress share Redis
- API key/secret match across `.env`, `livekit.yaml`, `egress.yaml`, backend env
- Egress reaches `host.docker.internal:7880`

```bash
docker compose logs egress
```

### Voice/video fail

- Same Session ID on both browsers
- LiveKit running on `:7880`
- Camera/mic permissions granted
- Backend on `:8080` (Vite proxies `/api`)

### Recording stuck STARTING or FAILED "Start signal not received"

Egress logs show `EGRESS_ABORTED` / `Start signal not received` when:
- Agent + Customer chưa publish video/audio (bật camera/mic trước khi Record)
- LiveKit RTC không reachable từ Egress container

Fix:
1. Run `./infra/scripts/detect-host-ip.sh` — use that LAN IP (not Docker gateway `192.168.65.254` unless it matches).
2. Set `livekit.yaml` → `rtc.node_ip` and `egress.yaml` → `ws_url` to the same LAN IP; use `udp_port: 7882-7892` (UDP mux).
3. **Restart LiveKit and egress** after changing config:
   ```bash
   cd infra && livekit-server --config livekit.yaml
   ```
3. Both browsers connected with **camera ON** → wait 2-3s → Start Recording
4. Check egress: `docker compose logs egress --tail 30`

### Recording has no output

- Egress logs for S3 upload errors
- Bucket `virtual-branch` exists (`docker compose logs minio-init`)
- Wait 10–30s after stop for MP4 finalization

### Customer cannot load PDF

- MinIO CORS configured for `http://localhost:5173` (re-run `minio-init`)
- Presigned URL not expired (10 min TTL)
- Customer accepted consent (collab status = ACTIVE)
- Browser console for CORS/network errors

### Pointer/highlight offset

- Coordinates are normalized 0..1 relative to PDF page canvas
- Agent and Customer must be on the same page number
- Use **Resync State** on Agent after Customer reconnect

### Doc Collab consent not appearing

- Both in same LiveKit room
- Agent token has `canPublishData=true`
- Customer connected before Agent requests collab
- Check browser console for Data Channel errors

## Stop everything

```bash
cd infra
docker compose down
# Ctrl+C LiveKit terminal
# Ctrl+C backend and agent-web terminals
```

## Security reminders

| Rule | Status |
|------|--------|
| No LiveKit secret in browser | Backend only |
| No MinIO secret in browser | Backend only |
| No signed URL in DB | Only `object_key` stored |
| No PDF/MP4 blob in DB | Object storage only |
| Customer PDF URL after consent | `GET /doc-collabs/{id}/document-url` gated |
