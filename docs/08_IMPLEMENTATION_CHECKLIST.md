# 08 — Implementation Checklist

## Phase 1 — Infra
- [ ] Docker Compose
- [ ] PostgreSQL healthy
- [ ] Redis PONG
- [ ] MinIO healthy
- [ ] `virtual-branch` bucket
- [ ] LiveKit runs on host
- [ ] LiveKit uses Redis
- [ ] CLI joins room
- [ ] demo media publish
- [ ] Egress starts
- [ ] Egress reaches LiveKit
- [ ] Egress reaches Redis
- [ ] Egress reaches MinIO

## Phase 2 — Backend
- [ ] Spring Boot project
- [ ] PostgreSQL config
- [ ] Flyway
- [ ] `vb_session`
- [ ] `vb_recording`
- [ ] `vb_document`
- [ ] `vb_doc_collab`
- [ ] create/get/end session API
- [ ] Agent token
- [ ] Customer token
- [ ] Agent `canPublishData=true`
- [ ] Customer `canPublishData=false`

## Phase 3 — Voice/video
- [ ] `/agent`
- [ ] `/customer-test`
- [ ] same room
- [ ] Agent mic/camera
- [ ] Customer mic/camera
- [ ] remote audio/video
- [ ] mic/camera toggle
- [ ] reconnect

## Phase 4 — Recording
- [ ] start API
- [ ] `recordingId`
- [ ] `egressId` persist
- [ ] RoomComposite starts
- [ ] stop API
- [ ] status API
- [ ] MP4 finalizes
- [ ] MP4 in storage
- [ ] DB `COMPLETED`
- [ ] temporary playback URL

## Phase 5 — Documents
- [ ] PDF upload
- [ ] MIME/type validation
- [ ] size validation
- [ ] technical object key
- [ ] storage upload
- [ ] metadata persist
- [ ] temporary read URL
- [ ] Agent PDF viewer

## Phase 6 — Doc Collab
- [ ] start collab API
- [ ] DOC_OPEN
- [ ] PAGE_CHANGE
- [ ] VIEWPORT_CHANGE
- [ ] POINTER_MOVE
- [ ] HIGHLIGHT
- [ ] normalized coordinates
- [ ] sequence
- [ ] Customer read-only
- [ ] reconnect snapshot
- [ ] COLLAB_END

## Phase 7 — Demo hardening
- [x] UI errors visible
- [x] standardized backend errors
- [x] no secrets in browser
- [x] no signed URL in DB
- [x] no PDF/MP4 blob in DB
- [x] useful logs
- [x] exact README startup sequence
- [x] clean-machine rehearsal
