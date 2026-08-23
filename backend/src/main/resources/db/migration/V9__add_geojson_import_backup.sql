CREATE TABLE geojson_import_backup (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    actor_id BIGINT REFERENCES app_user(id),
    target_fingerprint VARCHAR(64) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    conflict_policy VARCHAR(24) NOT NULL CHECK (conflict_policy IN ('KEEP_TARGET', 'OVERWRITE')),
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_geojson_backup_dataset_created
    ON geojson_import_backup(dataset_id, created_at DESC);
