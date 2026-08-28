CREATE TABLE vb_chat_message (
    id                  VARCHAR(64) PRIMARY KEY,
    session_id          VARCHAR(64) NOT NULL,
    sender_role         VARCHAR(16) NOT NULL,
    sender_identity     VARCHAR(128) NOT NULL,
    sender_name         VARCHAR(256),
    message_type        VARCHAR(32) NOT NULL,
    text_body           TEXT,
    document_id         VARCHAR(64),
    collab_id           VARCHAR(64),
    collab_status       VARCHAR(32),
    client_message_id   VARCHAR(64),
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES vb_session(id)
);

CREATE INDEX idx_chat_message_session_sent ON vb_chat_message(session_id, sent_at);
CREATE INDEX idx_chat_message_session_id ON vb_chat_message(session_id, id);
