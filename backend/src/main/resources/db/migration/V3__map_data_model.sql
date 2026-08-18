CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE campus (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    center_lng DOUBLE PRECISION NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    coordinate_system VARCHAR(16) NOT NULL CHECK (coordinate_system IN ('GCJ02')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dataset (
    id UUID PRIMARY KEY,
    campus_id UUID NOT NULL REFERENCES campus(id),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    dataset_type VARCHAR(16) NOT NULL CHECK (dataset_type IN ('DEMO', 'FORMAL')),
    coordinate_system VARCHAR(16) NOT NULL CHECK (coordinate_system IN ('GCJ02')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    seed BIGINT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((dataset_type = 'DEMO' AND is_demo) OR (dataset_type = 'FORMAL' AND NOT is_demo))
);

CREATE TABLE building (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    address VARCHAR(255),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    data_source VARCHAR(32) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    geom geometry(Polygon, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dataset_id, external_id),
    UNIQUE (id, dataset_id),
    CHECK (data_source IN ('DEMO_GENERATED', 'PUBLIC_SOURCE', 'MANUAL_ESTIMATE', 'USER_REPORT', 'FIELD_VERIFIED', 'UNVERIFIED')),
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE TABLE building_entrance (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    building_id UUID NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    accessible BOOLEAN NOT NULL DEFAULT FALSE,
    entrance_type VARCHAR(32) NOT NULL DEFAULT 'MAIN',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    data_source VARCHAR(32) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    geom geometry(Point, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dataset_id, external_id),
    FOREIGN KEY (building_id, dataset_id) REFERENCES building(id, dataset_id),
    CHECK (status IN ('OPEN', 'CLOSED', 'UNKNOWN')),
    CHECK (data_source IN ('DEMO_GENERATED', 'PUBLIC_SOURCE', 'MANUAL_ESTIMATE', 'USER_REPORT', 'FIELD_VERIFIED', 'UNVERIFIED')),
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE TABLE route_node (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(128),
    node_type VARCHAR(32) NOT NULL DEFAULT 'INTERSECTION',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    data_source VARCHAR(32) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    geom geometry(Point, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dataset_id, external_id),
    UNIQUE (id, dataset_id),
    CHECK (node_type IN ('INTERSECTION', 'ENTRANCE', 'WAYPOINT', 'FACILITY_CONNECTOR')),
    CHECK (data_source IN ('DEMO_GENERATED', 'PUBLIC_SOURCE', 'MANUAL_ESTIMATE', 'USER_REPORT', 'FIELD_VERIFIED', 'UNVERIFIED')),
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE TABLE route_edge (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(128),
    from_node_id UUID NOT NULL,
    to_node_id UUID NOT NULL,
    distance_m NUMERIC(10, 2) NOT NULL CHECK (distance_m > 0),
    slope_level VARCHAR(16) NOT NULL,
    has_stairs BOOLEAN NOT NULL DEFAULT FALSE,
    stairs_count INTEGER NOT NULL DEFAULT 0 CHECK (stairs_count >= 0),
    width_level VARCHAR(16) NOT NULL,
    surface_type VARCHAR(16) NOT NULL,
    lighting_level VARCHAR(16) NOT NULL,
    bidirectional BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    risk_level VARCHAR(16) NOT NULL,
    data_source VARCHAR(32) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    geom geometry(LineString, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dataset_id, external_id),
    FOREIGN KEY (from_node_id, dataset_id) REFERENCES route_node(id, dataset_id),
    FOREIGN KEY (to_node_id, dataset_id) REFERENCES route_node(id, dataset_id),
    CHECK (from_node_id <> to_node_id),
    CHECK (slope_level IN ('FLAT', 'GENTLE', 'MODERATE', 'STEEP', 'UNKNOWN')),
    CHECK (width_level IN ('NARROW', 'STANDARD', 'WIDE', 'UNKNOWN')),
    CHECK (surface_type IN ('ASPHALT', 'CONCRETE', 'BRICK', 'GRAVEL', 'DIRT', 'UNKNOWN')),
    CHECK (lighting_level IN ('NONE', 'LOW', 'MEDIUM', 'HIGH', 'UNKNOWN')),
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
    CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN')),
    CHECK (data_source IN ('DEMO_GENERATED', 'PUBLIC_SOURCE', 'MANUAL_ESTIMATE', 'USER_REPORT', 'FIELD_VERIFIED', 'UNVERIFIED')),
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE TABLE accessible_facility (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    building_id UUID,
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    facility_type VARCHAR(40) NOT NULL,
    floor_label VARCHAR(32),
    open_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    description TEXT,
    photo_url VARCHAR(500),
    last_verified_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    data_source VARCHAR(32) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    geom geometry(Point, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dataset_id, external_id),
    UNIQUE (id, dataset_id),
    FOREIGN KEY (building_id, dataset_id) REFERENCES building(id, dataset_id),
    CHECK (facility_type IN ('ACCESSIBLE_ENTRANCE', 'RAMP', 'ELEVATOR', 'ACCESSIBLE_TOILET', 'REST_AREA', 'ACCESSIBLE_PARKING', 'DROP_OFF_POINT', 'TRANSIT_BOARDING_POINT')),
    CHECK (open_status IN ('OPEN', 'CLOSED', 'UNKNOWN')),
    CHECK (data_source IN ('DEMO_GENERATED', 'PUBLIC_SOURCE', 'MANUAL_ESTIMATE', 'USER_REPORT', 'FIELD_VERIFIED', 'UNVERIFIED')),
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE TABLE barrier_report (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    reporter_id BIGINT REFERENCES app_user(id),
    external_id VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    barrier_type VARCHAR(32) NOT NULL,
    description TEXT,
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    active BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    data_source VARCHAR(32) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    geom geometry(Geometry, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dataset_id, external_id),
    UNIQUE (id, dataset_id),
    CHECK (barrier_type IN ('STAIRS', 'CONSTRUCTION', 'TEMPORARY_CLOSURE', 'DAMAGED_SURFACE', 'NARROW_PATH', 'VEHICLE_BLOCKING', 'STEEP_SLOPE', 'ELEVATOR_OUTAGE', 'ENTRANCE_CLOSED', 'WATERLOGGING')),
    CHECK (review_status IN ('PENDING', 'NEEDS_VERIFICATION', 'APPROVED', 'REJECTED')),
    CHECK (data_source IN ('DEMO_GENERATED', 'PUBLIC_SOURCE', 'MANUAL_ESTIMATE', 'USER_REPORT', 'FIELD_VERIFIED', 'UNVERIFIED')),
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE TABLE facility_rating (
    id BIGSERIAL PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    facility_id UUID NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (facility_id, user_id),
    FOREIGN KEY (facility_id, dataset_id) REFERENCES accessible_facility(id, dataset_id)
);

CREATE TABLE facility_comment (
    id BIGSERIAL PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    facility_id UUID NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (facility_id, dataset_id) REFERENCES accessible_facility(id, dataset_id)
);

CREATE TABLE facility_suggestion (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    facility_id UUID,
    user_id BIGINT REFERENCES app_user(id),
    suggestion_type VARCHAR(32) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (facility_id, dataset_id) REFERENCES accessible_facility(id, dataset_id)
);

CREATE INDEX idx_dataset_campus ON dataset(campus_id, enabled);
CREATE INDEX idx_building_dataset ON building(dataset_id, active);
CREATE INDEX idx_entrance_dataset ON building_entrance(dataset_id, active);
CREATE INDEX idx_node_dataset ON route_node(dataset_id, active);
CREATE INDEX idx_edge_dataset ON route_edge(dataset_id, status);
CREATE INDEX idx_facility_dataset ON accessible_facility(dataset_id, active);
CREATE INDEX idx_barrier_dataset ON barrier_report(dataset_id, active, review_status);
CREATE INDEX idx_building_geom ON building USING GIST(geom);
CREATE INDEX idx_entrance_geom ON building_entrance USING GIST(geom);
CREATE INDEX idx_node_geom ON route_node USING GIST(geom);
CREATE INDEX idx_edge_geom ON route_edge USING GIST(geom);
CREATE INDEX idx_facility_geom ON accessible_facility USING GIST(geom);
CREATE INDEX idx_barrier_geom ON barrier_report USING GIST(geom);
