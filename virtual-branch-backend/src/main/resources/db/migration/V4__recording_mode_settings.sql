CREATE TABLE vb_app_setting (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(512) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ROOM_COMPOSITE = one merged grid video; DUAL_PARTICIPANT = one MP4 per side
INSERT INTO vb_app_setting (setting_key, setting_value)
VALUES ('recording.mode', 'ROOM_COMPOSITE');

ALTER TABLE vb_recording
    ADD COLUMN IF NOT EXISTS group_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS side VARCHAR(32),
    ADD COLUMN IF NOT EXISTS mode VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_recording_group ON vb_recording(group_id);
