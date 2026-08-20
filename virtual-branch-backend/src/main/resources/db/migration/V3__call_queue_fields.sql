ALTER TABLE vb_session
    ADD COLUMN customer_identity VARCHAR(255),
    ADD COLUMN customer_name VARCHAR(255),
    ADD COLUMN agent_identity VARCHAR(255),
    ADD COLUMN agent_name VARCHAR(255),
    ADD COLUMN accepted_at TIMESTAMPTZ;

CREATE INDEX idx_vb_session_status_created ON vb_session (status, created_at);
