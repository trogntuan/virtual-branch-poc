CREATE TABLE vb_app_setting (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(512) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Default off to save LiveKit Cloud egress quota; toggle via API/UI without redeploy.
INSERT INTO vb_app_setting (setting_key, setting_value)
VALUES ('recording.enabled', 'false');
