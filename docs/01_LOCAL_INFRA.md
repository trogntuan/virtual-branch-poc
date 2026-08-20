# 01 — Local Infrastructure

## Goal

Dựng local:
- PostgreSQL
- Redis
- MinIO
- LiveKit OSS
- LiveKit Egress

Topology:

```text
HOST
├── LiveKit :7880/:7881/:7882
├── Backend :8080
├── Web     :5173
└── Docker
    ├── PostgreSQL :5432
    ├── Redis      :6379
    ├── MinIO      :9000/:9001
    └── Egress
```

## Directory

```text
infra/
├── docker-compose.yml
├── livekit.yaml
├── egress.yaml
└── .env
```

## Environment variables

```dotenv
POSTGRES_DB=virtual_branch
POSTGRES_USER=virtual_branch
POSTGRES_PASSWORD=virtual_branch

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_BUCKET=virtual-branch

LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=secret
```

## docker-compose requirements

### postgres
- port `5432`
- named volume
- healthcheck

### redis
- port `6379`
- healthcheck `redis-cli ping`

### minio
- API `9000`
- console `9001`
- named volume
- credentials từ `.env`

### egress
- official LiveKit Egress image
- mount `egress.yaml` read-only
- add required Chrome/sandbox capability for chosen version
- connect LiveKit qua `host.docker.internal` on macOS/Windows
- connect Redis by Docker service name
- connect MinIO by Docker service name

Không đưa LiveKit Server vào Docker ở milestone local đầu tiên.

## livekit.yaml target

```yaml
port: 7880
log_level: debug

rtc:
  tcp_port: 7881
  udp_port: 7882
  use_external_ip: false

redis:
  address: localhost:6379

keys:
  devkey: secret
```

Local localhost-only chưa cần TURN.

## egress.yaml logical target

```yaml
api_key: devkey
api_secret: secret
ws_url: ws://host.docker.internal:7880
insecure: true

redis:
  address: redis:6379

storage:
  s3:
    access_key: minioadmin
    secret: minioadmin123
    region: us-east-1
    endpoint: http://minio:9000
    bucket: virtual-branch
```

Nếu schema version Egress hiện tại khác, điều chỉnh theo official schema nhưng giữ nguyên kiến trúc.

LiveKit và Egress phải dùng cùng Redis.

## Boot order

```bash
docker compose up -d postgres redis minio
```

Tạo bucket:
```text
virtual-branch
```

Start LiveKit host:
```bash
livekit-server --config livekit.yaml
```

Test room bằng LiveKit CLI.

Sau đó:
```bash
docker compose up -d egress
```

## Health check

PostgreSQL:
```bash
docker exec -it vb-postgres psql -U virtual_branch -d virtual_branch -c "select now();"
```

Redis:
```bash
docker exec -it vb-redis redis-cli ping
```

MinIO:
```text
http://localhost:9001
```

Egress:
```bash
docker compose logs -f egress
```

## Acceptance criteria

- PostgreSQL query OK.
- Redis PONG.
- MinIO console OK.
- bucket `virtual-branch` tồn tại.
- LiveKit start và dùng Redis.
- CLI join room/publish demo media được.
- Egress start và không lỗi Redis/LiveKit.
- Không service nào restart loop.
