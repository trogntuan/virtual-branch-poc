# 05 — Recording with LiveKit Egress

## Goal

Agent start/stop recording current room.

Pipeline:

```text
LiveKit Room
-> RoomComposite Egress
-> MP4
-> S3-compatible Object Storage
```

Spring Boot chỉ control và lưu metadata.

## Start recording API

```http
POST /api/v1/sessions/{sessionId}/recordings
```

Behavior:
1. Validate session.
2. Generate `recordingId`.
3. Build object key.
4. Insert DB `REQUESTED`.
5. Call `StartRoomCompositeEgress`.
6. Save `egressId`.
7. Set `STARTING`.
8. Return immediately.

Object key:

```text
recordings/{sessionId}/{recordingId}.mp4
```

POC output:
```text
RoomComposite
MP4
H.264
AAC
720p
standard LiveKit layout
```

Không làm custom HTML layout trước khi standard recording chạy ổn.

## Stop API

```http
POST /api/v1/recordings/{recordingId}/stop
```

Behavior:
- load recording
- call StopEgress
- set `STOPPING`
- return immediately

Không giả định MP4 hoàn tất ngay sau stop.

## Status API

```http
GET /api/v1/recordings/{recordingId}
```

Response example:

```json
{
  "recordingId": "REC-...",
  "sessionId": "SES-...",
  "egressId": "EG_...",
  "status": "COMPLETED",
  "objectKey": "recordings/...",
  "playbackUrl": "temporary-url"
}
```

Polling được chấp nhận ở milestone đầu.
Webhook có thể thêm sau.

## Backend state

```text
REQUESTED
STARTING
RECORDING
STOPPING
COMPLETED
FAILED
```

Map raw Egress status vào enum backend.

## Failure handling

Handle:
- Egress unavailable
- room not found
- storage failure
- start fail
- stop fail
- finalize fail

Nếu recording fail:
- mark recording FAILED
- lưu short error
- không xóa session

## Acceptance criteria

- room có Agent + Customer video.
- Start Recording thành công.
- Egress active.
- Stop Recording thành công.
- MP4 xuất hiện trong storage.
- MP4 có media hai bên.
- DB có metadata recording.
