ALTER TABLE vb_session
    ADD COLUMN mobile_viewport_width INTEGER,
    ADD COLUMN mobile_viewport_height INTEGER,
    ADD COLUMN mobile_device_pixel_ratio DOUBLE PRECISION,
    ADD COLUMN mobile_orientation VARCHAR(16),
    ADD COLUMN mobile_display_updated_at TIMESTAMPTZ;

ALTER TABLE vb_doc_collab
    ADD COLUMN requested_at TIMESTAMPTZ,
    ADD COLUMN consent_decision VARCHAR(16),
    ADD COLUMN consent_at TIMESTAMPTZ,
    ADD COLUMN current_scroll_ratio DOUBLE PRECISION,
    ADD COLUMN view_mode VARCHAR(32),
    ADD COLUMN zoom_scale DOUBLE PRECISION,
    ADD COLUMN end_reason VARCHAR(64);

UPDATE vb_doc_collab SET requested_at = started_at WHERE requested_at IS NULL;
