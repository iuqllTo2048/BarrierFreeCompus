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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                """ + condition + " ORDER BY d.is_demo DESC, d.name",
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
        int stairs = request.hasStairs() ? request.stairsCount() : 0;
        UUID saved;
        Object[] values = {
                request.externalId(), request.name(), request.fromNodeId(), request.toNodeId(), request.distanceM(),
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
        root.put("datasetId", datasetId.toString());
        root.put("coordinateSystem", snapshot.dataset().coordinateSystem());
        ArrayNode features = root.putArray("features");
        for (NodeView node : snapshot.nodes()) {
            ObjectNode feature = feature(features, point(node.lng(), node.lat()), "NODE", node.externalId(), node.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("nodeType", node.nodeType());
            properties.put("active", node.active());
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
        }
        for (FacilityView facility : snapshot.facilities()) {
            ObjectNode feature = feature(features, point(facility.lng(), facility.lat()), "FACILITY",
                    facility.externalId(), facility.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("facilityType", facility.facilityType());
            properties.put("floorLabel", facility.floorLabel());
            properties.put("openStatus", facility.openStatus());
            properties.put("description", facility.description());
            properties.put("active", facility.active());
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

    private String lineStringWkt(List<Coordinate> points) {
        String coordinates = points.stream()
                .map(point -> String.format(Locale.ROOT, "%.8f %.8f", point.lng(), point.lat()))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        return "LINESTRING(" + coordinates + ")";
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
}
