package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.BarrierRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.BuildingRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.Coordinate;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.EntranceRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 地图对象（节点、道路、建筑、入口、设施、障碍）的新建与编辑。
 */
@Component
public class MapObjectService {
    private final JdbcTemplate jdbc;
    private final MapDataSupport support;

    public MapObjectService(JdbcTemplate jdbc, MapDataSupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    @Transactional
    public UUID saveNode(UUID datasetId, UUID id, NodeRequest request, String actor) {
        support.requireDataset(datasetId, true);
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
                    request.active(), MapDataSupport.MANUAL, MapDataSupport.UNKNOWN,
                    request.coordinate().lng(), request.coordinate().lat());
        } else {
            int updated = jdbc.update(
                    """
                    UPDATE route_node SET external_id=?, name=?, node_type=?, active=?,
                        geom=ST_SetSRID(ST_MakePoint(?,?),0), updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND dataset_id=?
                    """,
                    request.externalId(), request.name(), request.nodeType(), request.active(),
                    request.coordinate().lng(), request.coordinate().lat(), id, datasetId);
            if (updated != 1) throw support.notFound("道路节点不存在");
            saved = id;
        }
        support.audit(actor, id == null ? "NODE_CREATE" : "NODE_UPDATE", "ROUTE_NODE", saved.toString());
        return saved;
    }

    @Transactional
    public UUID saveEdge(UUID datasetId, UUID id, EdgeRequest request, String actor) {
        support.requireDataset(datasetId, true);
        if (request.fromNodeId().equals(request.toNodeId())) {
            throw support.badRequest("道路起点和终点不能相同");
        }
        if (request.hasStairs() && request.stairsCount() < 1) {
            throw support.badRequest("包含楼梯时，楼梯级数必须大于 0");
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
        Object[] values = {
                request.externalId(), request.name(), request.fromNodeId(), request.toNodeId(), distanceM,
                request.slopeLevel(), request.hasStairs(), stairs, request.widthLevel(), request.surfaceType(),
                request.lightingLevel(), request.bidirectional(), request.status(), request.riskLevel(), wkt
        };
        UUID saved;
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
                    values[8], values[9], values[10], values[11], values[12], values[13],
                    MapDataSupport.MANUAL, MapDataSupport.UNKNOWN, values[14]);
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
            if (updated != 1) throw support.notFound("道路不存在");
            saved = id;
        }
        support.audit(actor, id == null ? "EDGE_CREATE" : "EDGE_UPDATE", "ROUTE_EDGE", saved.toString());
        return saved;
    }

    @Transactional
    public UUID createBuilding(UUID datasetId, BuildingRequest request, String actor) {
        support.requireDataset(datasetId, true);
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
                request.description(), request.active(), MapDataSupport.MANUAL, MapDataSupport.UNKNOWN, wkt);
        support.audit(actor, "BUILDING_CREATE", "BUILDING", id.toString());
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
                request.accessible(), request.entranceType(), request.status(), request.active(),
                MapDataSupport.MANUAL, MapDataSupport.UNKNOWN,
                request.coordinate().lng(), request.coordinate().lat());
        support.audit(actor, "ENTRANCE_CREATE", "BUILDING_ENTRANCE", id.toString());
        return id;
    }

    @Transactional
    public UUID createFacility(UUID datasetId, FacilityRequest request, String actor) {
        support.requireDataset(datasetId, true);
        if (request.buildingId() != null) requireBuilding(datasetId, request.buildingId());
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO accessible_facility(id,dataset_id,building_id,external_id,name,facility_type,floor_label,
                    open_status,description,active,data_source,confidence_level,geom)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0)) RETURNING id
                """,
                UUID.class, UUID.randomUUID(), datasetId, request.buildingId(), request.externalId(), request.name(),
                request.facilityType(), request.floorLabel(), request.openStatus(), request.description(),
                request.active(), MapDataSupport.MANUAL, MapDataSupport.UNKNOWN,
                request.coordinate().lng(), request.coordinate().lat());
        support.audit(actor, "FACILITY_CREATE", "ACCESSIBLE_FACILITY", id.toString());
        return id;
    }

    @Transactional
    public UUID createBarrier(UUID datasetId, BarrierRequest request, String actor) {
        support.requireDataset(datasetId, true);
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO barrier_report(id,dataset_id,external_id,title,barrier_type,description,review_status,
                    active,data_source,confidence_level,geom)
                VALUES (?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0)) RETURNING id
                """,
                UUID.class, UUID.randomUUID(), datasetId, request.externalId(), request.title(), request.barrierType(),
                request.description(), request.reviewStatus(), request.active(),
                MapDataSupport.MANUAL, MapDataSupport.UNKNOWN,
                request.coordinate().lng(), request.coordinate().lat());
        support.audit(actor, "BARRIER_CREATE", "BARRIER_REPORT", id.toString());
        return id;
    }

    @Transactional
    public void deleteMapObject(String type, UUID datasetId, UUID id, String actor) {
        support.requireDataset(datasetId, true);
        String action = switch (type) {
            case "nodes" -> {
                deleteNodeInternal(datasetId, id);
                yield "NODE_DELETE";
            }
            case "edges" -> {
                deleteEdgeInternal(datasetId, id);
                yield "EDGE_DELETE";
            }
            case "buildings" -> {
                deleteBuildingInternal(datasetId, id);
                yield "BUILDING_DELETE";
            }
            case "entrances" -> {
                deleteEntranceInternal(datasetId, id);
                yield "ENTRANCE_DELETE";
            }
            case "facilities" -> {
                deleteFacilityInternal(datasetId, id);
                yield "FACILITY_DELETE";
            }
            case "barriers" -> {
                deleteBarrierInternal(datasetId, id);
                yield "BARRIER_DELETE";
            }
            default -> throw support.badRequest("不支持的地图对象类型");
        };
        support.audit(actor, action, type.toUpperCase(), id.toString());
    }

    private void deleteNodeInternal(UUID datasetId, UUID id) {
        requireExists("route_node", datasetId, id, "道路节点不存在");
        // 删除引用该节点的路线历史（收藏随历史级联删除），避免外键阻塞
        jdbc.update(
                "DELETE FROM route_history WHERE dataset_id=? AND (start_node_id=? OR end_node_id=?)",
                datasetId, id, id);
        jdbc.update(
                "DELETE FROM route_edge WHERE dataset_id=? AND (from_node_id=? OR to_node_id=?)",
                datasetId, id, id);
        jdbc.update("DELETE FROM route_node WHERE id=? AND dataset_id=?", id, datasetId);
    }

    private void deleteEdgeInternal(UUID datasetId, UUID id) {
        requireExists("route_edge", datasetId, id, "道路不存在");
        jdbc.update("DELETE FROM route_edge WHERE id=? AND dataset_id=?", id, datasetId);
    }

    private void deleteEntranceInternal(UUID datasetId, UUID id) {
        requireExists("building_entrance", datasetId, id, "入口不存在");
        jdbc.update("DELETE FROM building_entrance WHERE id=? AND dataset_id=?", id, datasetId);
    }

    private void deleteFacilityInternal(UUID datasetId, UUID id) {
        requireExists("accessible_facility", datasetId, id, "设施不存在");
        jdbc.update("DELETE FROM facility_rating WHERE facility_id=?", id);
        jdbc.update("DELETE FROM facility_comment WHERE facility_id=?", id);
        jdbc.update("DELETE FROM facility_suggestion WHERE facility_id=?", id);
        jdbc.update("DELETE FROM accessible_facility WHERE id=? AND dataset_id=?", id, datasetId);
    }

    private void deleteBuildingInternal(UUID datasetId, UUID id) {
        requireExists("building", datasetId, id, "建筑不存在");
        List<UUID> facilityIds = jdbc.query(
                "SELECT id FROM accessible_facility WHERE building_id=? AND dataset_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), id, datasetId);
        for (UUID facilityId : facilityIds) {
            jdbc.update("DELETE FROM facility_rating WHERE facility_id=?", facilityId);
            jdbc.update("DELETE FROM facility_comment WHERE facility_id=?", facilityId);
            jdbc.update("DELETE FROM facility_suggestion WHERE facility_id=?", facilityId);
        }
        jdbc.update("DELETE FROM accessible_facility WHERE building_id=? AND dataset_id=?", id, datasetId);
        jdbc.update("DELETE FROM building_entrance WHERE building_id=? AND dataset_id=?", id, datasetId);
        jdbc.update("DELETE FROM building WHERE id=? AND dataset_id=?", id, datasetId);
    }

    private void deleteBarrierInternal(UUID datasetId, UUID id) {
        requireExists("barrier_report", datasetId, id, "障碍不存在");
        jdbc.update("UPDATE barrier_report SET matched_report_id=NULL WHERE matched_report_id=?", id);
        jdbc.update("DELETE FROM barrier_report WHERE id=? AND dataset_id=?", id, datasetId);
    }

    private void requireExists(String table, UUID datasetId, UUID id, String message) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id=? AND dataset_id=?",
                Integer.class, id, datasetId);
        if (count == null || count == 0) throw support.notFound(message);
    }

    private Coordinate requireNodeCoordinate(UUID datasetId, UUID nodeId) {
        List<Coordinate> points = jdbc.query(
                "SELECT ST_X(geom), ST_Y(geom) FROM route_node WHERE id=? AND dataset_id=?",
                (rs, row) -> new Coordinate(rs.getDouble(1), rs.getDouble(2)), nodeId, datasetId);
        if (points.isEmpty()) throw support.badRequest("道路节点不属于当前数据集");
        return points.getFirst();
    }

    private void requireBuilding(UUID datasetId, UUID buildingId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM building WHERE id=? AND dataset_id=?", Integer.class, buildingId, datasetId);
        if (count == null || count != 1) throw support.badRequest("建筑不属于当前数据集");
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
        if (meters <= 0) throw support.badRequest("道路几何长度必须大于 0");
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
}
