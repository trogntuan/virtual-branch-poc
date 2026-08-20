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
