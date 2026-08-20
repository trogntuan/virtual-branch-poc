# 03 — API Contract and Database

Base path:

```text
/api/v1
```

## API contract

### Create session

```http
POST /api/v1/sessions
```

Response:

```json
{
  "sessionId": "SES-123",
  "roomName": "VB-ROOM-123",
  "status": "CREATED",
  "createdAt": "2026-08-19T12:00:00+07:00"
}
```

### Get session

```http
GET /api/v1/sessions/{sessionId}
```

### End session

```http
POST /api/v1/sessions/{sessionId}/end
```

### Get LiveKit token

```http
POST /api/v1/sessions/{sessionId}/token
```

Request:

```json
{
  "identity": "agent-001",
  "name": "Agent Demo",
  "role": "AGENT"
}
```

Role:
```text
AGENT
CUSTOMER
```

Response:

```json
{
  "serverUrl": "ws://localhost:7880",
  "roomName": "VB-ROOM-123",
  "participantToken": "..."
}
```

### Start recording

```http
POST /api/v1/sessions/{sessionId}/recordings
```

Response:

```json
{
  "recordingId": "REC-123",
  "egressId": "EG_123",
  "status": "STARTING"
}
```

### Get recording

```http
GET /api/v1/recordings/{recordingId}
```

### Stop recording

```http
POST /api/v1/recordings/{recordingId}/stop
```

### Upload document

```http
POST /api/v1/sessions/{sessionId}/documents
Content-Type: multipart/form-data
```

Form:
```text
file=<PDF>
```

Response:

```json
{
  "documentId": "DOC-123",
  "fileName": "contract.pdf",
  "contentType": "application/pdf",
  "size": 123456,
  "readUrl": "temporary-url"
}
```

### Get temporary document URL

```http
GET /api/v1/documents/{documentId}/url
```

Response:

```json
{
  "documentId": "DOC-123",
  "readUrl": "temporary-url",
  "expiresInSeconds": 600
}
```

### Start Doc Collab

```http
POST /api/v1/sessions/{sessionId}/doc-collabs
```

Request:

```json
{
  "documentId": "DOC-123"
}
```

Response:

```json
{
  "collabId": "COLLAB-123",
  "sessionId": "SES-123",
  "documentId": "DOC-123",
  "status": "ACTIVE"
}
```

### Get Doc Collab

```http
GET /api/v1/doc-collabs/{collabId}
```

### End Doc Collab

```http
POST /api/v1/doc-collabs/{collabId}/end
```

---

# Database

Use Flyway migration:

```text
src/main/resources/db/migration/V1__init_virtual_branch.sql
```

Suggested schema:

```sql
CREATE TABLE vb_session (
    id              VARCHAR(64) PRIMARY KEY,
    room_name       VARCHAR(128) NOT NULL UNIQUE,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ
);

CREATE TABLE vb_recording (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    egress_id       VARCHAR(128),
    status          VARCHAR(32) NOT NULL,
    object_key      VARCHAR(512),
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    error_message   VARCHAR(1000),
    CONSTRAINT fk_recording_session
        FOREIGN KEY (session_id) REFERENCES vb_session(id)
);

CREATE INDEX idx_recording_session ON vb_recording(session_id);

CREATE UNIQUE INDEX uq_recording_egress
    ON vb_recording(egress_id)
    WHERE egress_id IS NOT NULL;

CREATE TABLE vb_document (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(128) NOT NULL,
    file_size       BIGINT NOT NULL,
    object_key      VARCHAR(512) NOT NULL UNIQUE,
    checksum        VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_document_session
        FOREIGN KEY (session_id) REFERENCES vb_session(id)
);

CREATE INDEX idx_document_session ON vb_document(session_id);

CREATE TABLE vb_doc_collab (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    document_id     VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    current_page    INTEGER,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    CONSTRAINT fk_collab_session
        FOREIGN KEY (session_id) REFERENCES vb_session(id),
    CONSTRAINT fk_collab_document
        FOREIGN KEY (document_id) REFERENCES vb_document(id)
);

CREATE INDEX idx_collab_session ON vb_doc_collab(session_id);
CREATE INDEX idx_collab_document ON vb_doc_collab(document_id);
```

## Lifecycle

Session:
```text
CREATED -> ACTIVE -> ENDED
```

Recording:
```text
REQUESTED -> STARTING -> RECORDING -> STOPPING -> COMPLETED
                                           \-> FAILED
```

Doc Collab:
```text
CREATED -> ACTIVE -> ENDED
```

## ID format

```text
SES-<UUID>
REC-<UUID>
DOC-<UUID>
COLLAB-<UUID>
VB-<UUID>       // LiveKit room name
```

Không dùng PII trong ID hoặc object key.

## Do not store

- MP4 binary
- PDF binary
- LiveKit participant token
- LiveKit API secret
- S3 secret
- signed URL
- every pointer move
- every scroll event
