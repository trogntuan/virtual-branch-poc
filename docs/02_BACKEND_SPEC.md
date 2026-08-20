# 02 — Virtual Branch Backend Specification

## Goal

Spring Boot backend tối thiểu cho:
- session lifecycle
- LiveKit token
- recording control/status
- PDF upload/storage
- Doc Collab metadata
- public API cho Mobile tích hợp sau

Backend không proxy media.

## Stack

```text
Java 21
Spring Boot 3.x
Maven
Spring Web
Spring Validation
Spring Data JPA
PostgreSQL
Flyway
LiveKit JVM Server SDK
S3-compatible client
```

## Package structure

```text
com.example.virtualbranch
├── config
├── common
├── session
├── livekit
├── recording
├── document
└── collab
```

Mỗi feature nên có controller/service/repository/entity/dto khi cần.

## Session lifecycle

```text
CREATED -> ACTIVE -> ENDED
```

## Token role

Agent:
```text
roomJoin=true
canPublish=true
canSubscribe=true
canPublishData=true
```

Customer:
```text
roomJoin=true
canPublish=true
canSubscribe=true
canPublishData=false
```

Customer vẫn publish camera/mic; chỉ bị chặn publish Data Channel.

## Config target

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/virtual_branch
    username: virtual_branch
    password: virtual_branch
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

virtual-branch:
  livekit:
    api-url: http://localhost:7880
    ws-url: ws://localhost:7880
    api-key: ${LIVEKIT_API_KEY:devkey}
    api-secret: ${LIVEKIT_API_SECRET:secret}
  storage:
    endpoint: http://localhost:9000
    bucket: virtual-branch
    access-key: ${STORAGE_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_SECRET_KEY:minioadmin123}
```

## Error response

```json
{
  "code": "SESSION_NOT_FOUND",
  "message": "Session not found",
  "timestamp": "2026-08-19T12:00:00+07:00"
}
```

Codes tối thiểu:
```text
SESSION_NOT_FOUND
SESSION_ENDED
INVALID_ROLE
TOKEN_GENERATION_FAILED
RECORDING_NOT_FOUND
RECORDING_START_FAILED
RECORDING_STOP_FAILED
DOCUMENT_NOT_FOUND
INVALID_DOCUMENT
STORAGE_UPLOAD_FAILED
COLLAB_NOT_FOUND
```

## CORS

Allow local:
```text
http://localhost:5173
```

## Minimum tests

- session service
- Agent token has `canPublishData=true`
- Customer token has `canPublishData=false`
- PDF validation
- recording state mapping

## Acceptance criteria

- app boots với PostgreSQL
- Flyway chạy thành công
- create session persist được
- Agent token join LiveKit room được
- Customer token join same room được
- secret không bao giờ trả về client
