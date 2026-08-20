# Agent Web

React + TypeScript + Vite frontend for Virtual Branch POC.

## Routes

| Path | Purpose |
|------|---------|
| `/agent` | Agent console |
| `/customer-test` | Customer mock for two-browser testing |

## Prerequisites

1. Docker infra: PostgreSQL, Redis, MinIO
2. LiveKit Server on host: `livekit-server --config ../infra/livekit.yaml`
3. Backend on port 8080: `./mvnw spring-boot:run`

## Run

```bash
cd agent-web
npm install
npm run dev
```

Open:
- Agent: http://localhost:5173/agent
- Customer: http://localhost:5173/customer-test

Vite proxies `/api` to `http://localhost:8080`.

## Two-browser test steps

1. Start infra, LiveKit, and backend (see repo runbook).
2. Open **Browser A** → http://localhost:5173/agent
3. Click **Start Session** and allow camera/mic when prompted.
4. Copy the **Session ID** shown on the Agent page.
5. Open **Browser B** (or incognito) → http://localhost:5173/customer-test
6. Paste the Session ID and click **Join Room**; allow camera/mic.
7. Verify voice/video two-way, mic/camera toggle, connection badge **Connected**.
8. On Agent: **Start Recording** → talk → **Stop Recording** → wait for `COMPLETED` → open playback URL.
9. On Agent: choose a PDF file → verify upload succeeds and PDF renders in viewer (Prev/Next pages).
10. On Agent, click **End Session** to finish.

## PDF upload test

1. Agent page connected to a session.
2. Use the file picker under **PDF Document** and select a `.pdf` file.
3. Confirm:
   - Upload completes without error
   - Document ID and filename appear in the info panel
   - PDF Viewer renders page 1
   - Prev/Next navigation works
4. Optional curl check:

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/documents \
  -F "file=@sample.pdf" | jq '.readUrl'
```

Open the returned `readUrl` in a browser to confirm the PDF loads from MinIO.

## Two-browser Doc Collab test

1. Start infra, LiveKit, backend, agent-web.
2. **Browser A** → `/agent` → Start Session → upload PDF.
3. **Browser B** → `/customer-test` → paste Session ID → Join Room.
   - Customer auto-sends mobile display profile (390×844 PORTRAIT).
4. **Agent** → **Request Doc Collab**.
5. **Customer** sees consent popup → **Accept**.
6. Verify:
   - Customer PDF viewer opens (read-only) — URL only after consent
   - Agent changes page → Customer follows
   - Agent moves mouse over PDF → Customer sees pointer
   - Agent draws highlight → Customer sees highlight
7. **Agent** → **End Doc Collab** → Customer viewer closes.

### Verify consent gate

Before Customer accepts, this must return 403:

```bash
curl -s http://localhost:8080/api/v1/doc-collabs/COLLAB-<uuid>/document-url
```

## Build

```bash
npm run typecheck
npm run build
```

## Implemented

- Session create + token join flow
- LiveKit room connect, mic/camera toggle
- Recording start/stop + status polling
- PDF upload + PDF.js viewer
- Doc Collab: mobile display profile, consent REST, Data Channel sync
- Agent controls page/pointer/highlight; Customer read-only
- Error messages for backend/token/LiveKit/permission/upload/collab failures

## Not implemented yet (Phase 7+)

- Demo hardening / runbook rehearsal
- Backend WebSocket for collab status (using polling for now)
