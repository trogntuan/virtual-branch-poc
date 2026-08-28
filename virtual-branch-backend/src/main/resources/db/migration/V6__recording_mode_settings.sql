-- ROOM_COMPOSITE = one merged grid video; DUAL_PARTICIPANT = one MP4 per side
INSERT INTO vb_app_setting (setting_key, setting_value)
VALUES ('recording.mode', 'ROOM_COMPOSITE')
ON CONFLICT (setting_key) DO NOTHING;

ALTER TABLE vb_recording
    ADD COLUMN IF NOT EXISTS group_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS side VARCHAR(32),
    ADD COLUMN IF NOT EXISTS mode VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_recording_group ON vb_recording(group_id);
