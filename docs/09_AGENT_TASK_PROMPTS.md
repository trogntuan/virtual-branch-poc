# 09 — Ready-to-use Coding Agent Prompts

Use these prompts one phase at a time.

## Prompt 1 — Infra

```text
Read AGENTS.md and 01_LOCAL_INFRA.md first.

Implement only Phase 1 local infrastructure.
Create/update files under infra/.
Do not start backend or frontend work.

Requirements:
- Docker Compose for PostgreSQL, Redis, MinIO, Egress.
- LiveKit Server runs on host, not Docker.
- Create livekit.yaml, egress.yaml, .env.example.
- Add healthchecks where practical.
- Use same Redis for LiveKit and Egress.
- Configure Egress to reach host LiveKit.
- Configure Egress output to MinIO bucket virtual-branch.
- Add exact startup/test commands to infra/README.md.

Before finishing:
- validate compose syntax.
- show all created/modified files.
- explain how to verify PostgreSQL, Redis, MinIO, LiveKit, Egress.
- do not continue to Phase 2.
```

## Prompt 2 — Backend foundation

```text
Read AGENTS.md, 02_BACKEND_SPEC.md and 03_API_AND_DB.md.

Implement only backend foundation:
- Java 21 / Spring Boot 3.x / Maven.
- PostgreSQL + JPA + Flyway.
- V1 Flyway schema.
- session entity/repository/service/controller.
- LiveKit configuration.
- token generation endpoint.
- AGENT and CUSTOMER role grants exactly as specified.
- standard API error response.
- CORS for localhost:5173.

Do not implement recording, document or frontend yet.

Before finishing:
- run tests/build.
- show API curl examples.
- verify Agent canPublishData=true and Customer=false.
```

## Prompt 3 — Voice/video frontend

```text
Read AGENTS.md and 04_AGENT_WEB_SPEC.md.

Implement React/TypeScript/Vite Agent Web with /agent and /customer-test.
Integrate with existing backend session/token APIs and LiveKit.

Scope:
- join same room.
- local/remote camera.
- local/remote microphone.
- mic toggle.
- camera toggle.
- connection state.
- useful errors.

Do not implement recording or Doc Collab yet.

Before finishing:
- run typecheck/build.
- document exact two-browser test steps.
```

## Prompt 4 — Recording

```text
Read AGENTS.md and 05_RECORDING_SPEC.md.

Implement recording only:
- DB recording entity/repository.
- start RoomComposite Egress API.
- stop API.
- status API.
- state mapping.
- S3-compatible object key.
- temporary playback/read URL after completed.
- Agent Web start/stop controls.

Do not implement custom recording layout.
Do not implement webhook unless needed after polling works.

Before finishing:
- run tests/build.
- provide end-to-end test procedure proving MP4 appears in MinIO.
```

## Prompt 5 — PDF upload

```text
Read AGENTS.md, 03_API_AND_DB.md and 06_DOCUMENT_COLLAB_SPEC.md.

Implement document storage foundation only:
- PDF upload API.
- session validation.
- PDF MIME/extension validation.
- max size config.
- generated document ID/object key.
- upload to MinIO/S3-compatible storage.
- persist metadata.
- temporary read URL.
- Agent PDF viewer.

Do not implement Data Channel events yet.

Before finishing:
- test upload.
- prove PDF opens from returned temporary URL.
```

## Prompt 6 — Doc Collab

```text
Read AGENTS.md and 06_DOCUMENT_COLLAB_SPEC.md.

Implement Shared PDF + LiveKit Data Channel Doc Collab.

Do NOT implement Screen Share.

Required architecture:
- Agent and Customer open the same PDF.
- PDF binary is stored in S3-compatible Object Storage.
- Mobile sends viewportWidth/viewportHeight/devicePixelRatio/orientation with the call/session request.
- Agent renders a Mobile Preview with the same aspect ratio.
- Agent is controller; Customer is read-only.

Consent:
- Agent creates Collab REQUESTED.
- Agent sends COLLAB_REQUEST through LiveKit Data Channel.
- Mobile shows Accept/Reject.
- Mobile posts consent via REST.
- Backend must not give Mobile PDF URL before ACCEPT.
- ACCEPT -> ACTIVE.
- Mobile then gets temporary PDF URL and opens it read-only.

Data Channel topic:
doc-collab

Events:
- COLLAB_REQUEST reliable
- DOC_STATE reliable
- PAGE_CHANGE reliable
- VIEWPORT_CHANGE lossy/throttled
- POINTER_MOVE lossy/throttled
- POINTER_HIDE lossy
- HIGHLIGHT_SET reliable
- HIGHLIGHT_CLEAR reliable
- COLLAB_END reliable

Rules:
- normalized coordinates 0..1 relative to the PDF page.
- scroll uses ratio, not raw scrollTop pixels.
- default viewer mode FIT_WIDTH.
- Agent canPublishData=true.
- Customer canPublishData=false.
- Customer consent is REST.
- do not persist every pointer/scroll event.
- reconnect uses current DOC_STATE snapshot, not full event replay.

Before finishing:
- backend tests/build.
- frontend typecheck/build.
- exact two-browser demo steps.
- verify Mobile cannot get PDF URL before consent.
- verify page/scroll/pointer/highlight match Agent.
```
