# Local Infrastructure

Phase 1 stack for Virtual Branch POC.

## Topology

```text
HOST
├── LiveKit :7880/:7881/:7882
├── Backend :8080        (Phase 2+)
├── Web     :5173        (Phase 3+)
└── Docker
    ├── PostgreSQL :5432
    ├── Redis      :6379
    ├── MinIO      :9000/:9001
    └── Egress
```

LiveKit Server runs on the **host**, not in Docker.

## Files

```text
infra/
├── docker-compose.yml
├── livekit.yaml
├── egress.yaml
├── .env.example
├── .env                 # copy from .env.example, not committed
└── README.md
```

## Prerequisites

- Docker Desktop
- [LiveKit Server](https://docs.livekit.io/home/self-hosting/local/) on host
- [LiveKit CLI](https://docs.livekit.io/home/cli/) (`lk`) for room test (optional)

Install LiveKit Server (macOS):

```bash
brew install livekit
```

Install LiveKit CLI:

```bash
brew install livekit-cli
```

## Setup

```bash
cd infra
cp .env.example .env
```

Ensure `LIVEKIT_API_SECRET` in `.env` matches:
- `keys.devkey` in `livekit.yaml`
- `api_secret` in `egress.yaml`

Secret must be **at least 32 characters** (LiveKit requirement).

## Boot order

### 1. Docker services

```bash
docker compose up -d postgres redis minio minio-init
```

Wait until healthy:

```bash
docker compose ps
```

### 2. LiveKit on host

```bash
livekit-server --config livekit.yaml
```

Keep this terminal open.

### 3. Egress

In another terminal:

```bash
docker compose up -d egress
```

## Verify

### PostgreSQL

```bash
docker exec -it vb-postgres psql -U virtual_branch -d virtual_branch -c "SELECT now();"
```

### Redis

```bash
docker exec -it vb-redis redis-cli ping
# Expected: PONG
```

### MinIO

Console: http://localhost:9001

Login:
- User: `minioadmin`
- Password: `minioadmin123`

Verify bucket:

```bash
docker run --rm --network infra_default \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin123 \
  quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z \
  /bin/sh -c 'mc alias set local http://minio:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD && mc ls local/'
```

CORS for Agent Web PDF loading (`http://localhost:5173`) is set via `MINIO_API_CORS_ALLOW_ORIGIN` on the MinIO service (OSS community edition). Restart MinIO after changing:

```bash
docker compose up -d minio
```

### LiveKit + Redis

LiveKit log should show:

```text
connecting to redis {"addr": "localhost:6379"}
starting LiveKit server {"portHttp": 7880, ...}
```

No error about secret being too short.

### LiveKit CLI room test

Generate token and join a test room:

```bash
lk token create \
  --api-key devkey \
  --api-secret virtual_branch_poc_dev_secret_2026 \
  --join --room test-room --identity test-user \
  --valid-for 24h
```

Join with demo media:

```bash
lk room join \
  --url ws://localhost:7880 \
  --api-key devkey \
  --api-secret virtual_branch_poc_dev_secret_2026 \
  --room test-room \
  --identity test-user \
  --publish-demo
```

### Egress

```bash
docker compose logs -f egress
```

Healthy startup should show Redis connection and no repeated restart/errors reaching LiveKit at `host.docker.internal:7880`.

Check no restart loop:

```bash
docker compose ps
```

## Credentials (local dev only)

| Service    | Key / User        | Value                                      |
|------------|-------------------|--------------------------------------------|
| PostgreSQL | user / password   | `virtual_branch` / `virtual_branch`        |
| MinIO      | user / password   | `minioadmin` / `minioadmin123`             |
| LiveKit    | api_key / secret  | `devkey` / `virtual_branch_poc_dev_secret_2026` |

## Stop

```bash
# Stop Egress + Docker stack
docker compose down

# Stop LiveKit manually (Ctrl+C in its terminal)
```

## Troubleshooting

### MinIO image not found

Use a valid tag from https://quay.io/repository/minio/minio — current pin: `RELEASE.2025-09-07T16-13-09Z`.

### Egress cannot reach LiveKit

- LiveKit must run on host before starting egress.
- macOS/Windows: egress uses `ws://host.docker.internal:7880`.
- LiveKit and Egress must share the same Redis (`localhost:6379` vs `redis:6379`).

### Recording FAILED — "Start signal not received"

LiveKit must advertise a **Docker-reachable** IP for WebRTC, not your public WAN IP.

Symptom in LiveKit log: `nodeIP: 116.x.x.x` (public IP from STUN).

Fix in `livekit.yaml` + `egress.yaml`:

```bash
# Mac: detect your LAN IP (not Docker gateway IP)
./scripts/detect-host-ip.sh   # e.g. 192.168.21.136
```

```yaml
# livekit.yaml
rtc:
  use_external_ip: false
  use_ice_lite: true
  udp_port: 7882-7892          # UDP mux (do not use 50000-60000 on Mac Docker)
  node_ip: <LAN_IP from above>  # NOT 192.168.65.254 unless that is your LAN IP

# egress.yaml
ws_url: ws://<LAN_IP>:7880     # same IP as node_ip
```

Then **restart LiveKit + egress** and start a **new session** before recording again.
Both Agent + Customer must have **camera/mic ON** and see remote video before Start Recording.

### Secret too short

LiveKit rejects secrets under 32 chars. Update `.env`, `livekit.yaml`, and `egress.yaml` together.
