package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.BarrierRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.BarrierView;
import static cn.barrierfreecampus.mapdata.MapDtos.BuildingRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.BuildingView;
import static cn.barrierfreecampus.mapdata.MapDtos.Coordinate;
import static cn.barrierfreecampus.mapdata.MapDtos.DatasetView;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeView;
import static cn.barrierfreecampus.mapdata.MapDtos.EntranceRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.EntranceView;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityView;
import static cn.barrierfreecampus.mapdata.MapDtos.ImportResult;
import static cn.barrierfreecampus.mapdata.MapDtos.MapSnapshot;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MapDataService {
    private static final String MANUAL = "MANUAL_ESTIMATE";
    private static final String UNKNOWN = "UNKNOWN";
    private static final List<String> IMPORT_ORDER = List.of(
            "BUILDING", "NODE", "ENTRANCE", "FACILITY", "EDGE", "BARRIER");
    private static final Set<String> IMPORT_TYPES = Set.copyOf(IMPORT_ORDER);

    private final JdbcTemplate jdbc;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;

    public MapDataService(JdbcTemplate jdbc, DatasetMapper datasetMapper, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.datasetMapper = datasetMapper;
        this.objectMapper = objectMapper;
    }

    public List<DatasetView> listDatasets(boolean includeDisabled) {
        String condition = includeDisabled ? "" : " WHERE d.enabled = TRUE";
        return jdbc.query(
                """
                SELECT d.id, d.code, d.name, d.dataset_type, d.coordinate_system, d.enabled, d.is_demo,
                       d.seed, d.description, c.center_lng, c.center_lat
                FROM dataset d JOIN campus c ON c.id = d.campus_id
                """ + condition + " ORDER BY d.enabled DESC, d.created_at DESC, d.name",
                (rs, row) -> new DatasetView(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("dataset_type"), rs.getString("coordinate_system"),
                        rs.getBoolean("enabled"), rs.getBoolean("is_demo"),
                        rs.getObject("seed", Long.class), rs.getString("description"),
                        rs.getDouble("center_lng"), rs.getDouble("center_lat")));
    }

    public MapSnapshot snapshot(UUID datasetId, String bbox, boolean includeDisabled) {
        DatasetView dataset = requireDataset(datasetId, includeDisabled);
        Bounds bounds = parseBounds(bbox, dataset);
        Object[] spatial = {datasetId, bounds.minLng(), bounds.minLat(), bounds.maxLng(), bounds.maxLat()};

        List<BuildingView> buildings = jdbc.query(
                """
                SELECT id, external_id, name, category, active, data_source, confidence_level,
                       ST_AsGeoJSON(geom) AS geometry
                FROM building WHERE dataset_id = ? AND geom && ST_MakeEnvelope(?, ?, ?, ?, 0)
                ORDER BY external_id
                """,
                (rs, row) -> new BuildingView(
                        rs.getObject("id", UUID.class), rs.getString("external_id"), rs.getString("name"),
                        rs.getString("category"), rs.getBoolean("active"), rs.getString("data_source"),
                        rs.getString("confidence_level"), parseJson(rs.getString("geometry"))), spatial);

        List<EntranceView> entrances = jdbc.query(
                """
                SELECT id, building_id, external_id, name, accessible, entrance_type, status, active,
                       ST_X(geom) AS lng, ST_Y(geom) AS lat
                FROM building_entrance WHERE dataset_id = ? AND geom && ST_MakeEnvelope(?, ?, ?, ?, 0)
                ORDER BY external_id
                """,
                (rs, row) -> new EntranceView(
                        rs.getObject("id", UUID.class), rs.getObject("building_id", UUID.class),
                        rs.getString("external_id"), rs.getString("name"), rs.getBoolean("accessible"),
                        rs.getString("entrance_type"), rs.getString("status"), rs.getBoolean("active"),
                        rs.getDouble("lng"), rs.getDouble("lat")), spatial);

        List<NodeView> nodes = jdbc.query(
                """
                SELECT id, external_id, name, node_type, active, data_source, confidence_level,
                       ST_X(geom) AS lng, ST_Y(geom) AS lat
                FROM route_node WHERE dataset_id = ? AND geom && ST_MakeEnvelope(?, ?, ?, ?, 0)
                ORDER BY external_id
                """,
                (rs, row) -> new NodeView(
                        rs.getObject("id", UUID.class), rs.getString("external_id"), rs.getString("name"),
                        rs.getString("node_type"), rs.getBoolean("active"), rs.getString("data_source"),
                        rs.getString("confidence_level"), rs.getDouble("lng"), rs.getDouble("lat")), spatial);

        List<EdgeView> edges = jdbc.query(
                """
                SELECT id, external_id, name, from_node_id, to_node_id, distance_m, slope_level,
                       has_stairs, stairs_count, width_level, surface_type, lighting_level, bidirectional,
                       status, risk_level, data_source, confidence_level, ST_AsGeoJSON(geom) AS geometry
                FROM route_edge WHERE dataset_id = ? AND geom && ST_MakeEnvelope(?, ?, ?, ?, 0)
                ORDER BY external_id
                """,
                (rs, row) -> new EdgeView(
                        rs.getObject("id", UUID.class), rs.getString("external_id"), rs.getString("name"),
                        rs.getObject("from_node_id", UUID.class), rs.getObject("to_node_id", UUID.class),
                        rs.getBigDecimal("distance_m"), rs.getString("slope_level"), rs.getBoolean("has_stairs"),
                        rs.getInt("stairs_count"), rs.getString("width_level"), rs.getString("surface_type"),
                        rs.getString("lighting_level"), rs.getBoolean("bidirectional"), rs.getString("status"),
                        rs.getString("risk_level"), rs.getString("data_source"), rs.getString("confidence_level"),
                        parseJson(rs.getString("geometry"))), spatial);

        List<FacilityView> facilities = jdbc.query(
                """
                SELECT id, building_id, external_id, name, facility_type, floor_label, open_status, description,
                       active, data_source, confidence_level, ST_X(geom) AS lng, ST_Y(geom) AS lat
                FROM accessible_facility WHERE dataset_id = ? AND geom && ST_MakeEnvelope(?, ?, ?, ?, 0)
                ORDER BY external_id
                """,
                (rs, row) -> new FacilityView(
                        rs.getObject("id", UUID.class), rs.getObject("building_id", UUID.class),
                        rs.getString("external_id"), rs.getString("name"), rs.getString("facility_type"),
                        rs.getString("floor_label"), rs.getString("open_status"), rs.getString("description"),
                        rs.getBoolean("active"), rs.getString("data_source"), rs.getString("confidence_level"),
                        rs.getDouble("lng"), rs.getDouble("lat")), spatial);

        List<BarrierView> barriers = jdbc.query(
                """
                SELECT id, external_id, title, barrier_type, description, review_status, active, data_source,
                       confidence_level, ST_AsGeoJSON(geom) AS geometry
                FROM barrier_report WHERE dataset_id = ? AND geom && ST_MakeEnvelope(?, ?, ?, ?, 0)
                ORDER BY external_id
                """,
                (rs, row) -> new BarrierView(
                        rs.getObject("id", UUID.class), rs.getString("external_id"), rs.getString("title"),
                        rs.getString("barrier_type"), rs.getString("description"), rs.getString("review_status"),
                        rs.getBoolean("active"), rs.getString("data_source"), rs.getString("confidence_level"),
                        parseJson(rs.getString("geometry"))), spatial);

        return new MapSnapshot(dataset, buildings, entrances, nodes, edges, facilities, barriers);
    }

    @Transactional
    public DatasetView setDatasetEnabled(UUID datasetId, boolean enabled, String actor) {
        DatasetEntity entity = datasetMapper.selectById(datasetId);
        if (entity == null) {
            throw notFound("数据集不存在");
        }
        if (datasetMapper.updateEnabled(datasetId, enabled) != 1) {
            throw notFound("数据集不存在");
        }
        audit(actor, "DATASET_STATUS_CHANGE", "DATASET", datasetId.toString());
        return requireDataset(datasetId, true);
    }

    @Transactional
    public UUID saveNode(UUID datasetId, UUID id, NodeRequest request, String actor) {
        requireDataset(datasetId, true);
        UUID saved;
        if (id == null) {
            saved = jdbc.queryForObject(
                    """
                    INSERT INTO route_node(id,dataset_id,external_id,name,node_type,active,data_source,confidence_level,geom)
                    VALUES (?,?,?,?,?,?,?, ?, ST_SetSRID(ST_MakePoint(?,?),0))
                    ON CONFLICT (dataset_id, external_id) DO UPDATE SET name=EXCLUDED.name,
                        node_type=EXCLUDED.node_type, active=EXCLUDED.active, geom=EXCLUDED.geom,
                        updated_at=CURRENT_TIMESTAMP
                    RETURNING id
                    """,
                    UUID.class, UUID.randomUUID(), datasetId, request.externalId(), request.name(), request.nodeType(),
                    request.active(), MANUAL, UNKNOWN, request.coordinate().lng(), request.coordinate().lat());
        } else {
            int updated = jdbc.update(
                    """
                    UPDATE route_node SET external_id=?, name=?, node_type=?, active=?,
                        geom=ST_SetSRID(ST_MakePoint(?,?),0), updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND dataset_id=?
                    """,
                    request.externalId(), request.name(), request.nodeType(), request.active(),
                    request.coordinate().lng(), request.coordinate().lat(), id, datasetId);
            if (updated != 1) throw notFound("道路节点不存在");
            saved = id;
        }
        audit(actor, id == null ? "NODE_CREATE" : "NODE_UPDATE", "ROUTE_NODE", saved.toString());
        return saved;
    }

    @Transactional
    public UUID saveEdge(UUID datasetId, UUID id, EdgeRequest request, String actor) {
        requireDataset(datasetId, true);
        if (request.fromNodeId().equals(request.toNodeId())) {
            throw badRequest("道路起点和终点不能相同");
        }
        if (request.hasStairs() && request.stairsCount() < 1) {
            throw badRequest("包含楼梯时，楼梯级数必须大于 0");
        }
        Coordinate start = requireNodeCoordinate(datasetId, request.fromNodeId());
        Coordinate end = requireNodeCoordinate(datasetId, request.toNodeId());
        List<Coordinate> line = new ArrayList<>();
        line.add(start);
        if (request.intermediatePoints() != null) line.addAll(request.intermediatePoints());
        line.add(end);
        String wkt = lineStringWkt(line);
        BigDecimal distanceM = lineDistanceMeters(line);
        int stairs = request.hasStairs() ? request.stairsCount() : 0;
        UUID saved;
        Object[] values = {
                request.externalId(), request.name(), request.fromNodeId(), request.toNodeId(), distanceM,
                request.slopeLevel(), request.hasStairs(), stairs, request.widthLevel(), request.surfaceType(),
                request.lightingLevel(), request.bidirectional(), request.status(), request.riskLevel(), wkt
        };
        if (id == null) {
            saved = jdbc.queryForObject(
                    """
                    INSERT INTO route_edge(id,dataset_id,external_id,name,from_node_id,to_node_id,distance_m,
                        slope_level,has_stairs,stairs_count,width_level,surface_type,lighting_level,bidirectional,
                        status,risk_level,data_source,confidence_level,geom)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,ST_GeomFromText(?,0)) RETURNING id
                    """,
                    UUID.class, UUID.randomUUID(), datasetId,
                    values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                    values[8], values[9], values[10], values[11], values[12], values[13], MANUAL, UNKNOWN, values[14]);
        } else {
            int updated = jdbc.update(
                    """
                    UPDATE route_edge SET external_id=?,name=?,from_node_id=?,to_node_id=?,distance_m=?,
                        slope_level=?,has_stairs=?,stairs_count=?,width_level=?,surface_type=?,lighting_level=?,
                        bidirectional=?,status=?,risk_level=?,geom=ST_GeomFromText(?,0),updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND dataset_id=?
                    """,
                    values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                    values[8], values[9], values[10], values[11], values[12], values[13], values[14], id, datasetId);
            if (updated != 1) throw notFound("道路不存在");
            saved = id;
        }
        audit(actor, id == null ? "EDGE_CREATE" : "EDGE_UPDATE", "ROUTE_EDGE", saved.toString());
        return saved;
    }

    @Transactional
    public UUID createBuilding(UUID datasetId, BuildingRequest request, String actor) {
        requireDataset(datasetId, true);
        double lng = request.center().lng();
        double lat = request.center().lat();
        double dx = 0.00016;
        double dy = 0.00011;
        String wkt = String.format(Locale.ROOT,
                "POLYGON((%.8f %.8f,%.8f %.8f,%.8f %.8f,%.8f %.8f,%.8f %.8f))",
                lng - dx, lat - dy, lng + dx, lat - dy, lng + dx, lat + dy,
                lng - dx, lat + dy, lng - dx, lat - dy);
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO building(id,dataset_id,external_id,name,category,description,active,data_source,
                    confidence_level,geom) VALUES (?,?,?,?,?,?,?,?,?,ST_GeomFromText(?,0)) RETURNING id
                """,
                UUID.class, UUID.randomUUID(), datasetId, request.externalId(), request.name(), request.category(),
                request.description(), request.active(), MANUAL, UNKNOWN, wkt);
        audit(actor, "BUILDING_CREATE", "BUILDING", id.toString());
        return id;
    }

    @Transactional
    public UUID createEntrance(UUID datasetId, EntranceRequest request, String actor) {
        requireBuilding(datasetId, request.buildingId());
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO building_entrance(id,dataset_id,building_id,external_id,name,accessible,entrance_type,
                    status,active,data_source,confidence_level,geom)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0)) RETURNING id
                """,
                UUID.class, UUID.randomUUID(), datasetId, request.buildingId(), request.externalId(), request.name(),
                request.accessible(), request.entranceType(), request.status(), request.active(), MANUAL, UNKNOWN,
                request.coordinate().lng(), request.coordinate().lat());
        audit(actor, "ENTRANCE_CREATE", "BUILDING_ENTRANCE", id.toString());
        return id;
    }

    @Transactional
    public UUID createFacility(UUID datasetId, FacilityRequest request, String actor) {
        requireDataset(datasetId, true);
        if (request.buildingId() != null) requireBuilding(datasetId, request.buildingId());
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO accessible_facility(id,dataset_id,building_id,external_id,name,facility_type,floor_label,
                    open_status,description,active,data_source,confidence_level,geom)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0)) RETURNING id
                """,
                UUID.class, UUID.randomUUID(), datasetId, request.buildingId(), request.externalId(), request.name(),
                request.facilityType(), request.floorLabel(), request.openStatus(), request.description(), request.active(),
                MANUAL, UNKNOWN, request.coordinate().lng(), request.coordinate().lat());
        audit(actor, "FACILITY_CREATE", "ACCESSIBLE_FACILITY", id.toString());
        return id;
    }

    @Transactional
    public UUID createBarrier(UUID datasetId, BarrierRequest request, String actor) {
        requireDataset(datasetId, true);
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO barrier_report(id,dataset_id,external_id,title,barrier_type,description,review_status,
                    active,data_source,confidence_level,geom)
                VALUES (?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0)) RETURNING id
                """,
                UUID.class, UUID.randomUUID(), datasetId, request.externalId(), request.title(), request.barrierType(),
                request.description(), request.reviewStatus(), request.active(), MANUAL, UNKNOWN,
                request.coordinate().lng(), request.coordinate().lat());
        audit(actor, "BARRIER_CREATE", "BARRIER_REPORT", id.toString());
        return id;
    }

    public JsonNode exportGeoJson(UUID datasetId) {
        MapSnapshot snapshot = snapshot(datasetId, null, true);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "FeatureCollection");
        root.put("schemaVersion", 2);
        root.put("datasetId", datasetId.toString());
        root.put("datasetCode", snapshot.dataset().code());
        root.put("coordinateSystem", snapshot.dataset().coordinateSystem());
        root.put("exportedAt", Instant.now().toString());
        ArrayNode features = root.putArray("features");
        for (BuildingView building : snapshot.buildings()) {
            ObjectNode feature = feature(features, building.geometry(), "BUILDING", building.externalId(), building.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("category", building.category());
            properties.put("active", building.active());
            properties.put("dataSource", building.dataSource());
            properties.put("confidenceLevel", building.confidenceLevel());
        }
        for (EntranceView entrance : snapshot.entrances()) {
            ObjectNode feature = feature(features, point(entrance.lng(), entrance.lat()), "ENTRANCE",
                    entrance.externalId(), entrance.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("buildingExternalId", externalBuildingId(snapshot.buildings(), entrance.buildingId()));
            properties.put("accessible", entrance.accessible());
            properties.put("entranceType", entrance.entranceType());
            properties.put("status", entrance.status());
            properties.put("active", entrance.active());
        }
        for (NodeView node : snapshot.nodes()) {
            ObjectNode feature = feature(features, point(node.lng(), node.lat()), "NODE", node.externalId(), node.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("nodeType", node.nodeType());
            properties.put("active", node.active());
            properties.put("dataSource", node.dataSource());
            properties.put("confidenceLevel", node.confidenceLevel());
        }
        for (EdgeView edge : snapshot.edges()) {
            ObjectNode feature = feature(features, edge.geometry(), "EDGE", edge.externalId(), edge.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("fromNodeExternalId", externalNodeId(snapshot.nodes(), edge.fromNodeId()));
            properties.put("toNodeExternalId", externalNodeId(snapshot.nodes(), edge.toNodeId()));
            properties.put("distanceM", edge.distanceM());
            properties.put("slopeLevel", edge.slopeLevel());
            properties.put("hasStairs", edge.hasStairs());
            properties.put("stairsCount", edge.stairsCount());
            properties.put("widthLevel", edge.widthLevel());
            properties.put("surfaceType", edge.surfaceType());
            properties.put("lightingLevel", edge.lightingLevel());
            properties.put("bidirectional", edge.bidirectional());
            properties.put("status", edge.status());
            properties.put("riskLevel", edge.riskLevel());
            properties.put("dataSource", edge.dataSource());
            properties.put("confidenceLevel", edge.confidenceLevel());
        }
        for (FacilityView facility : snapshot.facilities()) {
            ObjectNode feature = feature(features, point(facility.lng(), facility.lat()), "FACILITY",
                    facility.externalId(), facility.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("facilityType", facility.facilityType());
            if (facility.buildingId() != null) {
                properties.put("buildingExternalId", externalBuildingId(snapshot.buildings(), facility.buildingId()));
            }
            properties.put("floorLabel", facility.floorLabel());
            properties.put("openStatus", facility.openStatus());
            properties.put("description", facility.description());
            properties.put("active", facility.active());
            properties.put("dataSource", facility.dataSource());
            properties.put("confidenceLevel", facility.confidenceLevel());
        }
        for (BarrierView barrier : snapshot.barriers()) {
            if ("USER_REPORT".equals(barrier.dataSource())) continue;
            ObjectNode feature = feature(features, barrier.geometry(), "BARRIER", barrier.externalId(), barrier.title());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("barrierType", barrier.barrierType());
            properties.put("description", barrier.description());
            properties.put("reviewStatus", barrier.reviewStatus());
            properties.put("active", barrier.active());
            properties.put("dataSource", barrier.dataSource());
            properties.put("confidenceLevel", barrier.confidenceLevel());
        }
        return root;
    }

    @Transactional
    public ImportResult importGeoJson(UUID datasetId, JsonNode root, String actor) {
        DatasetView dataset = requireDataset(datasetId, true);
        if (!dataset.demo()) throw badRequest("GeoJSON 安全导入仅允许 DEMO 数据集");
        if (!"FeatureCollection".equals(root.path("type").asText())) throw badRequest("必须是 FeatureCollection");
        if (!datasetId.toString().equals(root.path("datasetId").asText())) throw badRequest("datasetId 与目标数据集不一致");
        if (!dataset.coordinateSystem().equals(root.path("coordinateSystem").asText())) {
            throw badRequest("坐标系必须为 " + dataset.coordinateSystem());
        }
        JsonNode features = root.path("features");
        if (!features.isArray() || features.size() > 500) throw badRequest("features 必须是最多 500 项的数组");

        int nodes = 0;
        int facilities = 0;
        int edges = 0;
        for (JsonNode feature : features) {
            if ("NODE".equals(feature.path("properties").path("entityType").asText())) {
                JsonNode coordinate = requirePoint(feature);
                JsonNode props = feature.path("properties");
                saveNode(datasetId, null, new NodeRequest(
                        requiredText(props, "externalId"), nullableText(props, "name"),
                        props.path("nodeType").asText("INTERSECTION"), props.path("active").asBoolean(true),
                        new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble())), actor);
                nodes++;
            }
        }
        for (JsonNode feature : features) {
            if ("FACILITY".equals(feature.path("properties").path("entityType").asText())) {
                JsonNode coordinate = requirePoint(feature);
                JsonNode props = feature.path("properties");
                createOrUpdateImportedFacility(datasetId, props, coordinate, actor);
                facilities++;
            }
        }
        for (JsonNode feature : features) {
            if ("EDGE".equals(feature.path("properties").path("entityType").asText())) {
                JsonNode props = feature.path("properties");
                JsonNode coordinates = feature.path("geometry").path("coordinates");
                if (!"LineString".equals(feature.path("geometry").path("type").asText())
                        || !coordinates.isArray() || coordinates.size() < 2) {
                    throw badRequest("EDGE geometry 必须是至少两个坐标的 LineString");
                }
                UUID from = nodeIdByExternal(datasetId, requiredText(props, "fromNodeExternalId"));
                UUID to = nodeIdByExternal(datasetId, requiredText(props, "toNodeExternalId"));
                List<Coordinate> intermediate = new ArrayList<>();
                for (int index = 1; index < coordinates.size() - 1; index++) {
                    JsonNode coordinate = coordinates.get(index);
                    intermediate.add(new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
                }
                EdgeRequest request = new EdgeRequest(
                        requiredText(props, "externalId"), nullableText(props, "name"), from, to,
                        props.path("distanceM").decimalValue(), props.path("slopeLevel").asText("UNKNOWN"),
                        props.path("hasStairs").asBoolean(false), props.path("stairsCount").asInt(0),
                        props.path("widthLevel").asText("UNKNOWN"), props.path("surfaceType").asText("UNKNOWN"),
                        props.path("lightingLevel").asText("UNKNOWN"), props.path("bidirectional").asBoolean(true),
                        props.path("status").asText("ACTIVE"), props.path("riskLevel").asText("UNKNOWN"), intermediate);
                UUID existing = optionalEdgeId(datasetId, request.externalId());
                saveEdge(datasetId, existing, request, actor);
                edges++;
            }
        }
        audit(actor, "GEOJSON_IMPORT", "DATASET", datasetId.toString());
        return new ImportResult(nodes, edges, facilities);
    }

    public MapDtos.ImportPreview previewGeoJson(UUID datasetId, JsonNode root) {
        DatasetView dataset = requireDataset(datasetId, true);
        ImportAnalysis analysis = analyzeImport(dataset, root);
        return new MapDtos.ImportPreview(
                fingerprint(root), analysis.targetFingerprint(), analysis.summaries(),
                analysis.conflictSamples(), analysis.errors(), analysis.warnings());
    }

    @Transactional
    public MapDtos.ImportApplyResult applyGeoJson(
            UUID datasetId, MapDtos.ImportApplyRequest request, String actor) {
        DatasetView dataset = requireDataset(datasetId, true);
        ImportAnalysis analysis = analyzeImport(dataset, request.geoJson());
        if (!analysis.errors().isEmpty()) throw badRequest("GeoJSON 预检未通过：" + analysis.errors().getFirst());
        if (!fingerprint(request.geoJson()).equals(request.payloadFingerprint())) {
            throw badRequest("文件内容与预检时不一致，请重新预检");
        }
        if (!analysis.targetFingerprint().equals(request.targetFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标数据集已发生变化，请重新预检");
        }

        JsonNode backup = exportGeoJson(datasetId);
        UUID backupId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO geojson_import_backup(
                    id,dataset_id,actor_id,target_fingerprint,payload_fingerprint,conflict_policy,snapshot_json)
                SELECT ?,?,id,?,?,?,?::jsonb FROM app_user WHERE username=?
                """,
                backupId, datasetId, request.targetFingerprint(), request.payloadFingerprint(),
                request.conflictPolicy(), backup.toString(), actor);

        Map<String, JsonNode> target = featureIndex(backup.path("features"));
        Map<String, JsonNode> incoming = analysis.incoming();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int keptLocal = 0;
        for (String type : IMPORT_ORDER) {
            for (Map.Entry<String, JsonNode> entry : incoming.entrySet()) {
                if (!entry.getKey().startsWith(type + ":")) continue;
                JsonNode existing = target.get(entry.getKey());
                if (existing != null && existing.equals(entry.getValue())) {
                    unchanged++;
                    continue;
                }
                if (existing != null && "KEEP_TARGET".equals(request.conflictPolicy())) {
                    keptLocal++;
                    continue;
                }
                upsertImportedFeature(datasetId, entry.getValue(), actor);
                if (existing == null) created++;
                else updated++;
            }
        }
        audit(actor, "GEOJSON_FORMAL_IMPORT", "DATASET", datasetId.toString());
        return new MapDtos.ImportApplyResult(backupId, created, updated, unchanged, keptLocal);
    }

    private ImportAnalysis analyzeImport(DatasetView dataset, JsonNode root) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!"FeatureCollection".equals(root.path("type").asText())) errors.add("必须是 FeatureCollection");
        if (root.path("schemaVersion").asInt() != 2) errors.add("Formal 同步文件必须使用 schemaVersion=2");
        if (!dataset.code().equals(root.path("datasetCode").asText())) errors.add("datasetCode 与目标数据集不一致");
        if (!dataset.coordinateSystem().equals(root.path("coordinateSystem").asText())) {
            errors.add("坐标系必须为 " + dataset.coordinateSystem());
        }
        JsonNode features = root.path("features");
        if (!features.isArray()) errors.add("features 必须是数组");
        else if (features.size() > 2000) errors.add("features 最多允许 2000 项");

        JsonNode targetExport = exportGeoJson(dataset.id());
        Map<String, JsonNode> target = featureIndex(targetExport.path("features"));
        Map<String, JsonNode> incoming = new LinkedHashMap<>();
        if (features.isArray() && features.size() <= 2000) {
            int index = 0;
            for (JsonNode feature : features) {
                validateFeature(feature, index++, incoming, errors);
            }
        }
        validateReferences(incoming, target, errors);

        Map<String, MapDtos.ImportTypeSummary> summaries = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (String type : IMPORT_ORDER) {
            int creates = 0;
            int unchanged = 0;
            int changed = 0;
            for (Map.Entry<String, JsonNode> entry : incoming.entrySet()) {
                if (!entry.getKey().startsWith(type + ":")) continue;
                JsonNode existing = target.get(entry.getKey());
                if (existing == null) creates++;
                else if (existing.equals(entry.getValue())) unchanged++;
                else {
                    changed++;
                    if (conflicts.size() < 20) conflicts.add(entry.getKey());
                }
            }
            summaries.put(type, new MapDtos.ImportTypeSummary(creates, unchanged, changed));
        }
        warnings.add("本次为合并导入，文件中缺失的本地对象不会被删除");
        if (!conflicts.isEmpty()) warnings.add("存在同编号但内容不同的对象，应用时必须选择保留或覆盖");
        return new ImportAnalysis(fingerprint(targetExport), summaries, conflicts, errors, warnings, incoming);
    }

    private void validateFeature(
            JsonNode feature, int index, Map<String, JsonNode> incoming, List<String> errors) {
        JsonNode properties = feature.path("properties");
        String type = properties.path("entityType").asText();
        String externalId = properties.path("externalId").asText();
        String label = "features[" + index + "]";
        if (!IMPORT_TYPES.contains(type)) {
            errors.add(label + " entityType 不受支持");
            return;
        }
        if (externalId.isBlank() || externalId.length() > 64) {
            errors.add(label + " externalId 不能为空且最多 64 字符");
            return;
        }
        String key = type + ":" + externalId;
        if (incoming.putIfAbsent(key, feature) != null) errors.add("存在重复对象：" + key);
        String expectedGeometry = "BUILDING".equals(type) ? "Polygon" : "EDGE".equals(type) ? "LineString" : "Point";
        JsonNode geometry = feature.path("geometry");
        if (!expectedGeometry.equals(geometry.path("type").asText())
                || !validCoordinates(geometry.path("coordinates"))) {
            errors.add(key + " geometry 必须是有效的 " + expectedGeometry);
        }
        JsonNode coordinates = geometry.path("coordinates");
        if ("EDGE".equals(type) && (!coordinates.isArray() || coordinates.size() < 2)) {
            errors.add(key + " LineString 至少需要两个坐标");
        }
        if (!"NODE".equals(type) && !"EDGE".equals(type)
                && properties.path("name").asText().isBlank()) {
            errors.add(key + " name 不能为空");
        }
        validateProperties(type, key, properties, errors);
    }

    private void validateProperties(String type, String key, JsonNode properties, List<String> errors) {
        if (properties.has("dataSource")) validateAllowed(key, properties, "dataSource", errors,
                "DEMO_GENERATED", "PUBLIC_SOURCE", "MANUAL_ESTIMATE", "FIELD_VERIFIED", "UNVERIFIED");
        if (properties.has("confidenceLevel")) validateAllowed(key, properties, "confidenceLevel", errors,
                "HIGH", "MEDIUM", "LOW", "UNKNOWN");
        switch (type) {
            case "NODE" -> validateAllowed(key, properties, "nodeType", errors,
                    "INTERSECTION", "ENTRANCE", "WAYPOINT", "FACILITY_CONNECTOR");
            case "EDGE" -> {
                validateAllowed(key, properties, "slopeLevel", errors, "FLAT", "GENTLE", "MODERATE", "STEEP", "UNKNOWN");
                validateAllowed(key, properties, "widthLevel", errors, "NARROW", "STANDARD", "WIDE", "UNKNOWN");
                validateAllowed(key, properties, "surfaceType", errors, "ASPHALT", "CONCRETE", "BRICK", "GRAVEL", "DIRT", "UNKNOWN");
                validateAllowed(key, properties, "lightingLevel", errors, "NONE", "LOW", "MEDIUM", "HIGH", "UNKNOWN");
                validateAllowed(key, properties, "status", errors, "ACTIVE", "INACTIVE", "CLOSED", "BLOCKED");
                validateAllowed(key, properties, "riskLevel", errors, "LOW", "MEDIUM", "HIGH", "UNKNOWN");
                if (properties.path("fromNodeExternalId").asText().isBlank()
                        || properties.path("toNodeExternalId").asText().isBlank()) {
                    errors.add(key + " 必须提供道路起终点 externalId");
                }
            }
            case "ENTRANCE" -> validateAllowed(key, properties, "status", errors, "OPEN", "CLOSED", "UNKNOWN");
            case "FACILITY" -> {
                validateAllowed(key, properties, "facilityType", errors,
                        "ACCESSIBLE_ENTRANCE", "RAMP", "ELEVATOR", "ACCESSIBLE_TOILET", "REST_AREA",
                        "ACCESSIBLE_PARKING", "DROP_OFF_POINT", "TRANSIT_BOARDING_POINT");
                validateAllowed(key, properties, "openStatus", errors, "OPEN", "CLOSED", "UNKNOWN");
            }
            case "BARRIER" -> {
                validateAllowed(key, properties, "barrierType", errors,
                        "STAIRS", "CONSTRUCTION", "TEMPORARY_CLOSURE", "DAMAGED_SURFACE", "NARROW_PATH",
                        "VEHICLE_BLOCKING", "STEEP_SLOPE", "ELEVATOR_OUTAGE", "ENTRANCE_CLOSED", "WATERLOGGING");
                validateAllowed(key, properties, "reviewStatus", errors,
                        "PENDING", "NEEDS_VERIFICATION", "APPROVED", "REJECTED");
            }
            default -> {
                // BUILDING 只需要通用字段和 Polygon。
            }
        }
    }

    private void validateAllowed(
            String key, JsonNode properties, String field, List<String> errors, String... allowed) {
        String value = properties.path(field).asText();
        if (!Set.of(allowed).contains(value)) errors.add(key + " 的 " + field + " 不合法");
    }

    private void validateReferences(
            Map<String, JsonNode> incoming, Map<String, JsonNode> target, List<String> errors) {
        Set<String> nodes = referencedExternalIds("NODE", incoming, target);
        Set<String> buildings = referencedExternalIds("BUILDING", incoming, target);
        for (Map.Entry<String, JsonNode> entry : incoming.entrySet()) {
            JsonNode properties = entry.getValue().path("properties");
            if (entry.getKey().startsWith("EDGE:")) {
                for (String field : List.of("fromNodeExternalId", "toNodeExternalId")) {
                    String reference = properties.path(field).asText();
                    if (!nodes.contains(reference)) errors.add(entry.getKey() + " 引用了不存在的节点：" + reference);
                }
            }
            if (entry.getKey().startsWith("ENTRANCE:")
                    || entry.getKey().startsWith("FACILITY:") && properties.hasNonNull("buildingExternalId")) {
                String reference = properties.path("buildingExternalId").asText();
                if (!buildings.contains(reference)) errors.add(entry.getKey() + " 引用了不存在的建筑：" + reference);
            }
        }
    }

    private Set<String> referencedExternalIds(
            String type, Map<String, JsonNode> incoming, Map<String, JsonNode> target) {
        Set<String> values = new LinkedHashSet<>();
        for (String key : incoming.keySet()) if (key.startsWith(type + ":")) values.add(key.substring(type.length() + 1));
        for (String key : target.keySet()) if (key.startsWith(type + ":")) values.add(key.substring(type.length() + 1));
        return values;
    }

    private void upsertImportedFeature(UUID datasetId, JsonNode feature, String actor) {
        JsonNode properties = feature.path("properties");
        String type = properties.path("entityType").asText();
        String externalId = requiredText(properties, "externalId");
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        switch (type) {
            case "BUILDING" -> jdbc.update(
                    """
                    INSERT INTO building(id,dataset_id,external_id,name,category,active,data_source,confidence_level,geom)
                    VALUES (?,?,?,?,?,?,?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?),0))
                    ON CONFLICT(dataset_id,external_id) DO UPDATE SET name=EXCLUDED.name,
                      category=EXCLUDED.category,active=EXCLUDED.active,data_source=EXCLUDED.data_source,
                      confidence_level=EXCLUDED.confidence_level,geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                    """,
                    UUID.randomUUID(), datasetId, externalId, requiredText(properties, "name"),
                    properties.path("category").asText("OTHER"), properties.path("active").asBoolean(true),
                    importedDataSource(properties), importedConfidence(properties), feature.path("geometry").toString());
            case "NODE" -> {
                UUID nodeId = saveNode(datasetId, null, new NodeRequest(
                        externalId, nullableText(properties, "name"),
                        properties.path("nodeType").asText("INTERSECTION"),
                        properties.path("active").asBoolean(true), coordinate(coordinates)), actor);
                jdbc.update("UPDATE route_node SET data_source=?,confidence_level=? WHERE id=?",
                        importedDataSource(properties), importedConfidence(properties), nodeId);
            }
            case "ENTRANCE" -> {
                UUID buildingId = buildingIdByExternal(datasetId, requiredText(properties, "buildingExternalId"));
                Coordinate point = coordinate(coordinates);
                jdbc.update(
                        """
                        INSERT INTO building_entrance(id,dataset_id,building_id,external_id,name,accessible,
                          entrance_type,status,active,data_source,confidence_level,geom)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0))
                        ON CONFLICT(dataset_id,external_id) DO UPDATE SET building_id=EXCLUDED.building_id,
                          name=EXCLUDED.name,accessible=EXCLUDED.accessible,entrance_type=EXCLUDED.entrance_type,
                          status=EXCLUDED.status,active=EXCLUDED.active,geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                        """,
                        UUID.randomUUID(), datasetId, buildingId, externalId, requiredText(properties, "name"),
                        properties.path("accessible").asBoolean(false),
                        properties.path("entranceType").asText("MAIN"), properties.path("status").asText("UNKNOWN"),
                        properties.path("active").asBoolean(true), MANUAL, UNKNOWN, point.lng(), point.lat());
            }
            case "FACILITY" -> {
                UUID buildingId = properties.hasNonNull("buildingExternalId")
                        ? buildingIdByExternal(datasetId, properties.path("buildingExternalId").asText()) : null;
                Coordinate point = coordinate(coordinates);
                jdbc.update(
                        """
                        INSERT INTO accessible_facility(id,dataset_id,building_id,external_id,name,facility_type,
                          floor_label,open_status,description,active,data_source,confidence_level,geom)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0))
                        ON CONFLICT(dataset_id,external_id) DO UPDATE SET building_id=EXCLUDED.building_id,
                          name=EXCLUDED.name,facility_type=EXCLUDED.facility_type,floor_label=EXCLUDED.floor_label,
                          open_status=EXCLUDED.open_status,description=EXCLUDED.description,active=EXCLUDED.active,
                          data_source=EXCLUDED.data_source,confidence_level=EXCLUDED.confidence_level,
                          geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                        """,
                        UUID.randomUUID(), datasetId, buildingId, externalId, requiredText(properties, "name"),
                        requiredText(properties, "facilityType"), nullableText(properties, "floorLabel"),
                        properties.path("openStatus").asText("UNKNOWN"), nullableText(properties, "description"),
                        properties.path("active").asBoolean(true), importedDataSource(properties),
                        importedConfidence(properties), point.lng(), point.lat());
            }
            case "EDGE" -> {
                UUID from = nodeIdByExternal(datasetId, requiredText(properties, "fromNodeExternalId"));
                UUID to = nodeIdByExternal(datasetId, requiredText(properties, "toNodeExternalId"));
                List<Coordinate> intermediate = new ArrayList<>();
                for (int index = 1; index < coordinates.size() - 1; index++) {
                    intermediate.add(coordinate(coordinates.get(index)));
                }
                EdgeRequest edge = new EdgeRequest(
                        externalId, nullableText(properties, "name"), from, to, BigDecimal.ONE,
                        properties.path("slopeLevel").asText("UNKNOWN"),
                        properties.path("hasStairs").asBoolean(false), properties.path("stairsCount").asInt(0),
                        properties.path("widthLevel").asText("UNKNOWN"),
                        properties.path("surfaceType").asText("UNKNOWN"),
                        properties.path("lightingLevel").asText("UNKNOWN"),
                        properties.path("bidirectional").asBoolean(true),
                        properties.path("status").asText("ACTIVE"),
                        properties.path("riskLevel").asText("UNKNOWN"), intermediate);
                UUID edgeId = saveEdge(datasetId, optionalEdgeId(datasetId, externalId), edge, actor);
                jdbc.update("UPDATE route_edge SET data_source=?,confidence_level=? WHERE id=?",
                        importedDataSource(properties), importedConfidence(properties), edgeId);
            }
            case "BARRIER" -> {
                Coordinate point = coordinate(coordinates);
                jdbc.update(
                        """
                        INSERT INTO barrier_report(id,dataset_id,external_id,title,barrier_type,description,
                          review_status,active,data_source,confidence_level,geom)
                        VALUES (?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0))
                        ON CONFLICT(dataset_id,external_id) DO UPDATE SET title=EXCLUDED.title,
                          barrier_type=EXCLUDED.barrier_type,description=EXCLUDED.description,
                          review_status=EXCLUDED.review_status,active=EXCLUDED.active,
                          data_source=EXCLUDED.data_source,confidence_level=EXCLUDED.confidence_level,
                          geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                        """,
                        UUID.randomUUID(), datasetId, externalId, requiredText(properties, "name"),
                        requiredText(properties, "barrierType"), nullableText(properties, "description"),
                        properties.path("reviewStatus").asText("PENDING"), properties.path("active").asBoolean(false),
                        importedDataSource(properties), importedConfidence(properties), point.lng(), point.lat());
            }
            default -> throw badRequest("不支持的 entityType：" + type);
        }
    }

    private String importedDataSource(JsonNode properties) {
        String value = properties.path("dataSource").asText(MANUAL);
        return "USER_REPORT".equals(value) ? MANUAL : value;
    }

    private String importedConfidence(JsonNode properties) {
        return properties.path("confidenceLevel").asText(UNKNOWN);
    }

    private Coordinate coordinate(JsonNode coordinates) {
        return new Coordinate(coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
    }

    private UUID buildingIdByExternal(UUID datasetId, String externalId) {
        List<UUID> values = jdbc.query(
                "SELECT id FROM building WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        if (values.isEmpty()) throw badRequest("引用了不存在的建筑：" + externalId);
        return values.getFirst();
    }

    private Map<String, JsonNode> featureIndex(JsonNode features) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        if (!features.isArray()) return values;
        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            String type = properties.path("entityType").asText();
            String externalId = properties.path("externalId").asText();
            if (!type.isBlank() && !externalId.isBlank()) values.put(type + ":" + externalId, feature);
        }
        return values;
    }

    private boolean validCoordinates(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.isEmpty()) return false;
        if (coordinates.size() >= 2 && coordinates.get(0).isNumber() && coordinates.get(1).isNumber()) {
            double lng = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();
            return Double.isFinite(lng) && Double.isFinite(lat)
                    && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
        }
        for (JsonNode child : coordinates) if (!validCoordinates(child)) return false;
        return true;
    }

    private String fingerprint(JsonNode value) {
        JsonNode normalized = value.deepCopy();
        if (normalized instanceof ObjectNode object) object.remove("exportedAt");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(normalized)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("无法计算 GeoJSON 指纹", exception);
        }
    }

    private void createOrUpdateImportedFacility(UUID datasetId, JsonNode props, JsonNode coordinate, String actor) {
        String externalId = requiredText(props, "externalId");
        FacilityRequest request = new FacilityRequest(
                null, externalId, requiredText(props, "name"), props.path("facilityType").asText(),
                nullableText(props, "floorLabel"), props.path("openStatus").asText("UNKNOWN"),
                nullableText(props, "description"), props.path("active").asBoolean(true),
                new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
        List<UUID> existing = jdbc.query(
                "SELECT id FROM accessible_facility WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        if (existing.isEmpty()) {
            createFacility(datasetId, request, actor);
        } else {
            jdbc.update(
                    """
                    UPDATE accessible_facility SET name=?,facility_type=?,floor_label=?,open_status=?,description=?,
                        active=?,geom=ST_SetSRID(ST_MakePoint(?,?),0),updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND dataset_id=?
                    """,
                    request.name(), request.facilityType(), request.floorLabel(), request.openStatus(),
                    request.description(), request.active(), request.coordinate().lng(), request.coordinate().lat(),
                    existing.getFirst(), datasetId);
        }
    }

    private DatasetView requireDataset(UUID id, boolean includeDisabled) {
        return listDatasets(includeDisabled).stream().filter(dataset -> dataset.id().equals(id)).findFirst()
                .orElseThrow(() -> notFound("数据集不存在或未启用"));
    }

    private Coordinate requireNodeCoordinate(UUID datasetId, UUID nodeId) {
        List<Coordinate> points = jdbc.query(
                "SELECT ST_X(geom), ST_Y(geom) FROM route_node WHERE id=? AND dataset_id=?",
                (rs, row) -> new Coordinate(rs.getDouble(1), rs.getDouble(2)), nodeId, datasetId);
        if (points.isEmpty()) throw badRequest("道路节点不属于当前数据集");
        return points.getFirst();
    }

    private void requireBuilding(UUID datasetId, UUID buildingId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM building WHERE id=? AND dataset_id=?", Integer.class, buildingId, datasetId);
        if (count == null || count != 1) throw badRequest("建筑不属于当前数据集");
    }

    private UUID nodeIdByExternal(UUID datasetId, String externalId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM route_node WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        if (ids.isEmpty()) throw badRequest("道路引用了不存在的节点：" + externalId);
        return ids.getFirst();
    }

    private UUID optionalEdgeId(UUID datasetId, String externalId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM route_edge WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Bounds parseBounds(String bbox, DatasetView dataset) {
        if (bbox == null || bbox.isBlank()) {
            return new Bounds(dataset.centerLng() - 0.02, dataset.centerLat() - 0.02,
                    dataset.centerLng() + 0.02, dataset.centerLat() + 0.02);
        }
        try {
            String[] values = bbox.split(",");
            if (values.length != 4) throw new NumberFormatException();
            Bounds bounds = new Bounds(Double.parseDouble(values[0]), Double.parseDouble(values[1]),
                    Double.parseDouble(values[2]), Double.parseDouble(values[3]));
            if (bounds.minLng() >= bounds.maxLng() || bounds.minLat() >= bounds.maxLat()) {
                throw new NumberFormatException();
            }
            return bounds;
        } catch (NumberFormatException exception) {
            throw badRequest("bbox 格式应为 minLng,minLat,maxLng,maxLat");
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库空间数据无法转换为 GeoJSON", exception);
        }
    }

    private JsonNode point(double lng, double lat) {
        ObjectNode point = objectMapper.createObjectNode();
        point.put("type", "Point");
        point.putArray("coordinates").add(lng).add(lat);
        return point;
    }

    private ObjectNode feature(ArrayNode features, JsonNode geometry, String entityType, String externalId, String name) {
        ObjectNode feature = features.addObject();
        feature.put("type", "Feature");
        feature.set("geometry", geometry);
        ObjectNode properties = feature.putObject("properties");
        properties.put("entityType", entityType);
        properties.put("externalId", externalId);
        properties.put("name", name);
        return feature;
    }

    private JsonNode requirePoint(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        JsonNode coordinates = geometry.path("coordinates");
        if (!"Point".equals(geometry.path("type").asText()) || !coordinates.isArray()
                || coordinates.size() < 2 || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) {
            throw badRequest("NODE/FACILITY geometry 必须是 Point");
        }
        return coordinates;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw badRequest(field + " 不能为空");
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private String externalNodeId(List<NodeView> nodes, UUID id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst().map(NodeView::externalId)
                .orElseThrow(() -> new IllegalStateException("道路引用节点缺失"));
    }

    private String externalBuildingId(List<BuildingView> buildings, UUID id) {
        return buildings.stream().filter(building -> building.id().equals(id)).findFirst()
                .map(BuildingView::externalId)
                .orElseThrow(() -> new IllegalStateException("对象引用建筑缺失"));
    }

    private String lineStringWkt(List<Coordinate> points) {
        String coordinates = points.stream()
                .map(point -> String.format(Locale.ROOT, "%.8f %.8f", point.lng(), point.lat()))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        return "LINESTRING(" + coordinates + ")";
    }

    private BigDecimal lineDistanceMeters(List<Coordinate> points) {
        double meters = 0;
        for (int index = 1; index < points.size(); index++) {
            meters += haversineMeters(points.get(index - 1), points.get(index));
        }
        if (meters <= 0) throw badRequest("道路几何长度必须大于 0");
        return BigDecimal.valueOf(meters).setScale(2, RoundingMode.HALF_UP);
    }

    private double haversineMeters(Coordinate first, Coordinate second) {
        double earthRadius = 6_371_000;
        double firstLat = Math.toRadians(first.lat());
        double secondLat = Math.toRadians(second.lat());
        double deltaLat = secondLat - firstLat;
        double deltaLng = Math.toRadians(second.lng() - first.lng());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(firstLat) * Math.cos(secondLat)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void audit(String username, String action, String targetType, String targetId) {
        jdbc.update(
                """
                INSERT INTO audit_log(actor_id,action,target_type,target_id)
                SELECT id,?,?,? FROM app_user WHERE username=?
                """,
                action, targetType, targetId, username);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record Bounds(double minLng, double minLat, double maxLng, double maxLat) {
    }

    private record ImportAnalysis(
            String targetFingerprint,
            Map<String, MapDtos.ImportTypeSummary> summaries,
            List<String> conflictSamples,
            List<String> errors,
            List<String> warnings,
            Map<String, JsonNode> incoming) {
    }
}
