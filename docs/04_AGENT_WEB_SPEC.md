# 04 — Agent Web and Customer Mock

## Goal

React app tối giản với:

```text
/agent
/customer-test
```

Customer page chỉ dùng để test trước khi Mobile thật tích hợp.

## Stack

```text
React
TypeScript
Vite
livekit-client
@livekit/components-react (optional)
pdfjs-dist
axios or fetch
```

## `/agent`

Required UI:

```text
+------------------------------------------------------+
| Session / Room                                      |
+--------------------------+---------------------------+
| Customer video           | Agent local video         |
+--------------------------+---------------------------+
| Mic | Camera | Start Rec | Stop Rec | End Session   |
+------------------------------------------------------+
| Upload PDF | Start Doc Collab                       |
+------------------------------------------------------+
| PDF Viewer + pointer/highlight overlay              |
+------------------------------------------------------+
```

## `/customer-test`

```text
+------------------------------------------------------+
| Room                                                 |
+--------------------------+---------------------------+
| Agent video              | Customer local video      |
+--------------------------+---------------------------+
| Mic | Camera                                          |
+------------------------------------------------------+
| Read-only PDF Viewer + remote overlay                |
+------------------------------------------------------+
```

Customer không có nút điều khiển Doc Collab.

## Connection flow

Agent:
```text
POST /sessions
-> POST /sessions/{id}/token role=AGENT
-> room.connect()
-> enable mic/camera
```

Customer:
```text
same sessionId
-> POST /sessions/{id}/token role=CUSTOMER
-> room.connect()
-> enable mic/camera
```

## Suggested frontend structure

```text
src/
├── api/
│   └── virtualBranchApi.ts
├── livekit/
│   ├── useLiveKitRoom.ts
│   └── types.ts
├── collab/
│   ├── useDocCollab.ts
│   ├── DocCollabViewer.tsx
│   └── events.ts
├── recording/
│   └── useRecording.ts
├── pages/
│   ├── AgentPage.tsx
│   └── CustomerTestPage.tsx
└── App.tsx
```

## Media behavior

Both sides:
- publish mic
- publish camera
- subscribe remote tracks
- attach remote audio/video
- mic toggle
- camera toggle
- show connection state

States:
```text
DISCONNECTED
CONNECTING
CONNECTED
RECONNECTING
```

## Error handling

Display errors for:
- backend unavailable
- token failure
- LiveKit connection failure
- camera/mic permission denied
- recording failure
- document upload/load failure

## Acceptance criteria

- Agent and Customer join same room.
- Both see/hear each other.
- Mic/camera toggle works.
- Reload/reconnect does not permanently break flow.
