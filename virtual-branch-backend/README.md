# Virtual Branch Backend

Spring Boot backend for session lifecycle and LiveKit token generation.

## Prerequisites

- Java 17+
- PostgreSQL running (`infra/docker compose up -d postgres`)
- LiveKit Server on host (`livekit-server --config ../infra/livekit.yaml`)

## Run

```bash
cd virtual-branch-backend

# Optional: load LiveKit secret from infra/.env
export LIVEKIT_API_SECRET=virtual_branch_poc_dev_secret_2026

./mvnw spring-boot:run
```

API base: `http://localhost:8080/api/v1`

## API examples

### Create session

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions | jq
```

### Get session

```bash
curl -s http://localhost:8080/api/v1/sessions/SES-<uuid> | jq
```

### Agent token

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/token \
  -H 'Content-Type: application/json' \
  -d '{
    "identity": "agent-001",
    "name": "Agent Demo",
    "role": "AGENT"
  }' | jq
```

### Customer token

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/token \
  -H 'Content-Type: application/json' \
  -d '{
    "identity": "customer-001",
    "name": "Customer Demo",
    "role": "CUSTOMER"
  }' | jq
```

### End session

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/end | jq
```

## Recording API

### Start recording

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/recordings | jq
```

### Get recording status

```bash
curl -s http://localhost:8080/api/v1/recordings/REC-<uuid> | jq
```

### Stop recording

```bash
curl -s -X POST http://localhost:8080/api/v1/recordings/REC-<uuid>/stop | jq
```

Recording states: `REQUESTED -> STARTING -> RECORDING -> STOPPING -> COMPLETED` (or `FAILED`).

When status is `COMPLETED`, `playbackUrl` contains a presigned MinIO URL (10 min expiry).

## Doc Collab API

### Update mobile display profile

```bash
curl -s -X PUT http://localhost:8080/api/v1/sessions/SES-<uuid>/mobile-display \
  -H 'Content-Type: application/json' \
  -d '{
    "viewportWidth": 390,
    "viewportHeight": 844,
    "devicePixelRatio": 3,
    "orientation": "PORTRAIT"
  }' | jq
```

### Start doc collab

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/doc-collabs \
  -H 'Content-Type: application/json' \
  -d '{"documentId":"DOC-<uuid>"}' | jq
```

### Customer consent

```bash
curl -s -X POST http://localhost:8080/api/v1/doc-collabs/COLLAB-<uuid>/consent \
  -H 'Content-Type: application/json' \
  -d '{"decision":"ACCEPT"}' | jq
```

### Get collab status

```bash
curl -s http://localhost:8080/api/v1/doc-collabs/COLLAB-<uuid> | jq
```

### Get PDF URL (Customer, after consent)

```bash
curl -s http://localhost:8080/api/v1/doc-collabs/COLLAB-<uuid>/document-url | jq
```

Returns 403 if collab is not `ACTIVE` or consent not `ACCEPT`.

### End doc collab

```bash
curl -s -X POST http://localhost:8080/api/v1/doc-collabs/COLLAB-<uuid>/end | jq
```

## Document API

### Upload PDF

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions/SES-<uuid>/documents \
  -F "file=@/path/to/contract.pdf" | jq
```

Response includes `readUrl` (presigned MinIO URL, 10 min expiry).

Validation:
- MIME: `application/pdf` only
- Extension: `.pdf`
- Max size: 50 MB

Object key format: `documents/{sessionId}/{documentId}.pdf`

### Get temporary document URL

```bash
curl -s http://localhost:8080/api/v1/documents/DOC-<uuid>/url | jq
```

## Token grants

| Role     | canPublish | canSubscribe | canPublishData |
|----------|------------|--------------|----------------|
| AGENT    | true       | true         | true           |
| CUSTOMER | true       | true         | false          |

LiveKit API secret is never returned to clients.

## Test

```bash
./mvnw test
```
