ALTER TABLE audit_log ADD COLUMN detail VARCHAR(1000);

ALTER TABLE barrier_report
    ADD COLUMN matched_report_id UUID REFERENCES barrier_report(id),
    ADD COLUMN reviewed_by BIGINT REFERENCES app_user(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ;

CREATE INDEX idx_barrier_match
    ON barrier_report(dataset_id, barrier_type, created_at, reporter_id);

CREATE TABLE user_profile (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id),
    display_name VARCHAR(64),
    default_mobility_mode VARCHAR(32) NOT NULL DEFAULT 'WALKING',
    avoid_stairs BOOLEAN NOT NULL DEFAULT FALSE,
    distance_weight NUMERIC(3,1) NOT NULL DEFAULT 1.0,
    slope_weight NUMERIC(3,1) NOT NULL DEFAULT 1.0,
    width_weight NUMERIC(3,1) NOT NULL DEFAULT 1.0,
    prefer_rest_area BOOLEAN NOT NULL DEFAULT FALSE,
    prefer_accessible_toilet BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (default_mobility_mode IN ('WHEELCHAIR','CRUTCH','TEMPORARY_INJURY','CART_LUGGAGE','WALKING')),
    CHECK (distance_weight BETWEEN 0.5 AND 2.0),
    CHECK (slope_weight BETWEEN 0.5 AND 2.0),
    CHECK (width_weight BETWEEN 0.5 AND 2.0)
);

INSERT INTO user_profile(user_id, display_name)
SELECT id, username FROM app_user ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE route_history (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    start_node_id UUID NOT NULL,
    end_node_id UUID NOT NULL,
    mobility_mode VARCHAR(32) NOT NULL,
    travel_period VARCHAR(16) NOT NULL,
    request_json JSONB NOT NULL,
    result_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (start_node_id, dataset_id) REFERENCES route_node(id, dataset_id),
    FOREIGN KEY (end_node_id, dataset_id) REFERENCES route_node(id, dataset_id)
);

CREATE INDEX idx_route_history_user_created ON route_history(user_id, created_at DESC);

CREATE TABLE route_favorite (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    history_id UUID NOT NULL REFERENCES route_history(id) ON DELETE CASCADE,
    route_profile VARCHAR(24) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, history_id, route_profile),
    CHECK (route_profile IN ('SHORTEST','ACCESSIBLE','BALANCED'))
);

CREATE INDEX idx_route_favorite_user_created ON route_favorite(user_id, created_at DESC);

CREATE TABLE system_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    description VARCHAR(300),
    updated_by BIGINT REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_setting(setting_key,setting_value,description) VALUES
('barrier.match.radius.meters','50','同类障碍上报空间匹配半径'),
('barrier.match.window.hours','24','同类障碍上报时间匹配窗口'),
('barrier.scheduler.enabled','true','是否启用临时障碍自动过期');
