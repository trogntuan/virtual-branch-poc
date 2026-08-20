# Virtual Branch POC

Local-first proof of concept: Agent Web + Customer mock join a LiveKit room for voice/video, recording, PDF upload, and Doc Collab over LiveKit Data Channel.

## Architecture

```text
Agent Web ---- REST ----> Virtual Branch Backend ----> PostgreSQL
 |                              |
 |                              +----> MinIO (S3-compatible)
 |
 +---- WebRTC + DataChannel ----> LiveKit OSS <---- Customer/Mobile
                                      |
                                      +---- Egress ----> MinIO (MP4)
```

## Prerequisites

| Tool | Version / notes |
|------|----------------|
| Docker Desktop | PostgreSQL, Redis, MinIO, Egress |
| Java | 17+ |
| Node.js | 18+ |
| LiveKit Server | `brew install livekit` |
| LiveKit CLI | optional, for infra smoke test |

## Startup sequence (exact order)

### Terminal 1 — Docker infra

```bash
cd infra
cp -n .env.example .env   # first time only
docker compose up -d postgres redis minio minio-init
```

### Terminal 2 — LiveKit (host)

```bash
cd infra
livekit-server --config livekit.yaml
```

### Terminal 3 — Egress

```bash
cd infra
docker compose up -d egress
```

### Terminal 4 — Backend

```bash
cd virtual-branch-backend
export LIVEKIT_API_SECRET=virtual_branch_poc_dev_secret_2026
./mvnw spring-boot:run
```

Verify: `curl -s http://localhost:8080/api/v1/health`

### Terminal 5 — Agent Web

```bash
cd agent-web
npm install
npm run dev
```

| Service | URL |
|---------|-----|
| Agent Web | http://localhost:5173/agent |
| Customer mock | http://localhost:5173/customer-test |
| Backend API | http://localhost:8080/api/v1 |
| LiveKit | ws://localhost:7880 |
| MinIO Console | http://localhost:9001 |

## End-to-end demo

See [docs/07_RUNBOOK.md](docs/07_RUNBOOK.md) for the full demo script and troubleshooting.

Quick flow:

1. Agent → Start Session
2. Customer → Join with Session ID
3. Verify voice/video two-way
4. Agent → Start Recording → Stop → wait for MP4 playback URL
5. Agent → Upload PDF
6. Agent → Request Doc Collab
7. Customer → Accept consent
8. Verify page/pointer/highlight sync
9. Agent → End Doc Collab → End Session

## Project layout

```text
virtual-branch-poc/
├── infra/                    # Docker Compose, LiveKit/Egress config
├── virtual-branch-backend/   # Spring Boot REST API
├── agent-web/                # React Agent + Customer mock
└── docs/                     # Specs and runbook
```

## Security (POC)

- LiveKit API secret and MinIO credentials stay on backend/infra only — never in browser.
- Temporary presigned URLs are generated on demand, never stored in PostgreSQL.
- PDF/MP4 binaries live in object storage only, not in the database.

## Build & test

```bash
# Backend
cd virtual-branch-backend && ./mvnw test

# Frontend
cd agent-web && npm run typecheck && npm run build
```

## Documentation

- [AGENTS.md](AGENTS.md) — mission and strict phase order
- [docs/07_RUNBOOK.md](docs/07_RUNBOOK.md) — demo script + troubleshooting
- [infra/README.md](infra/README.md) — infrastructure details
- [virtual-branch-backend/README.md](virtual-branch-backend/README.md) — API examples
- [agent-web/README.md](agent-web/README.md) — two-browser test steps
