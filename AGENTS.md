# AGENTS.md — Virtual Branch POC

## Mission

Xây dựng POC local-first gồm:
- Agent Web và Customer/Mobile join cùng LiveKit room.
- Voice/video hai chiều.
- Start/stop recording.
- Egress ghi MP4 vào S3-compatible storage.
- Agent upload PDF.
- Backend lưu PDF và cấp temporary URL.
- Agent gửi sự kiện Doc Collab qua LiveKit Data Channel.
- Customer chỉ xem; Agent là controller.
- Backend public API để Mobile tích hợp sau.

## Components

```text
virtual-branch-poc/
├── infra/
├── virtual-branch-backend/
├── agent-web/
└── docs/
```

Local runtime:

```text
PostgreSQL / Redis / MinIO / Egress -> Docker Compose
LiveKit Server                     -> host machine
Spring Boot Backend                -> host machine
Agent Web                          -> host machine
```

## Architecture

```text
Agent Web ---- REST ----> Virtual Branch Backend ----> PostgreSQL
   |                              |
   |                              +----> Object Storage
   |
   | WebRTC + DataChannel
   v
LiveKit OSS <---------------------- Customer/Mobile
   |
   +---- Redis
   |
   +---- LiveKit Egress ----> Object Storage
```

## Strict implementation order

1. Local infrastructure.
2. Backend foundation + DB migration + session/token API.
3. Agent + Customer Mock voice/video.
4. Recording end-to-end.
5. PDF upload/storage.
6. Doc Collab over Data Channel.
7. Demo hardening/runbook.

Không làm phase sau nếu phase hiện tại chưa chạy được.

## Non-goals

Không làm nếu chưa được yêu cầu:
- efast-mobile
- login/onboarding/biometric
- queue/routing
- Kafka
- Kubernetes
- production HA
- multi-agent
- remote desktop
- full mobile screen share
- Word/Excel collaboration
- CRDT/OT
- enterprise SSO

## Technical decisions

Backend:
- Java 21
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway
- LiveKit JVM Server SDK
- S3-compatible client

Frontend:
- React + TypeScript + Vite
- livekit-client
- PDF.js

Infra:
- PostgreSQL
- Redis
- MinIO local
- LiveKit OSS
- LiveKit Egress

Agent phải kiểm tra version stable/compatible hiện tại trước khi pin dependency.

## Security rules

- Không expose `LIVEKIT_API_SECRET` ra browser/mobile.
- Token LiveKit chỉ sinh ở backend.
- Không expose S3/MinIO secret ra client.
- PDF dùng temporary/presigned URL.
- Không đưa PII vào object key.
- Agent token: `canPublishData=true`.
- Customer token: `canPublishData=false`.
- Audio/video không đi qua Spring Boot.
- PDF binary không gửi qua Data Channel.
- Không log token, secret, signed URL.

## Coding rules

Backend:
- controller -> service -> repository/integration.
- DTO tách khỏi entity.
- constructor injection.
- enum cho lifecycle state.
- Flyway cho schema.
- không dùng `ddl-auto=update`.

Frontend:
- TypeScript strict.
- LiveKit state tách hook/module.
- Doc Collab state tách hook/module.
- Data Channel payload có type rõ ràng.
- Không nhồi toàn bộ logic vào một component.

## Definition of Done

```text
Agent + Customer join same room
-> voice/video works
-> Agent starts recording
-> Egress writes MP4
-> Agent uploads PDF
-> Customer receives DOC_OPEN
-> page/pointer/highlight follow Agent
-> Agent stops recording
-> metadata persisted
```
