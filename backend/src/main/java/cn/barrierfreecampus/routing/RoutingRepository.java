package cn.barrierfreecampus.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class RoutingRepository {
    // GCJ-02 以 SRID 0 保存；0.00025 度约为校园尺度 25 米邻近范围。
    private static final double ROUTE_ASSOCIATION_TOLERANCE_DEGREES = 0.00025;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RoutingRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    RouteGraph.GraphData loadGraph(UUID datasetId) {
        requireEnabledGcj02Dataset(datasetId);
        List<RouteGraph.Node> nodes = jdbc.query(
                """
                SELECT id,external_id,name,active,confidence_level,ST_X(geom) lng,ST_Y(geom) lat
                FROM route_node WHERE dataset_id=? ORDER BY external_id
                """,
                (rs, row) -> new RouteGraph.Node(
                        rs.getObject("id", UUID.class),
                        rs.getString("external_id"),
                        rs.getString("name"),
                        rs.getBoolean("active"),
                        rs.getString("confidence_level"),
                        new RouteGraph.Point(rs.getDouble("lng"), rs.getDouble("lat"))),
                datasetId);

        Map<UUID, List<RouteGraph.Facility>> facilitiesByEdge = loadFacilitiesByEdge(datasetId);
        Map<UUID, List<RouteGraph.Barrier>> barriersByEdge = loadBarriersByEdge(datasetId);
        List<RouteGraph.Edge> edges = jdbc.query(
                """
                SELECT id,external_id,name,from_node_id,to_node_id,distance_m,slope_level,has_stairs,
                       stairs_count,width_level,surface_type,lighting_level,bidirectional,status,risk_level,
                       confidence_level,ST_AsGeoJSON(geom) geometry
                FROM route_edge WHERE dataset_id=? ORDER BY external_id
                """,
                (rs, row) -> {
                    UUID edgeId = rs.getObject("id", UUID.class);
                    return new RouteGraph.Edge(
                            edgeId,
                            rs.getString("external_id"),
                            rs.getString("name"),
                            rs.getObject("from_node_id", UUID.class),
                            rs.getObject("to_node_id", UUID.class),
                            rs.getDouble("distance_m"),
                            rs.getString("slope_level"),
                            rs.getBoolean("has_stairs"),
                            rs.getInt("stairs_count"),
                            rs.getString("width_level"),
                            rs.getString("surface_type"),
                            rs.getString("lighting_level"),
                            rs.getBoolean("bidirectional"),
                            rs.getString("status"),
                            rs.getString("risk_level"),
                            rs.getString("confidence_level"),
                            parseLineString(rs.getString("geometry")),
                            List.copyOf(facilitiesByEdge.getOrDefault(edgeId, List.of())),
                            List.copyOf(barriersByEdge.getOrDefault(edgeId, List.of())));
                },
                datasetId);
        return RouteGraph.GraphData.of(nodes, edges);
    }

    private Map<UUID, List<RouteGraph.Facility>> loadFacilitiesByEdge(UUID datasetId) {
        Map<UUID, List<RouteGraph.Facility>> result = new HashMap<>();
        jdbc.query(
                """
                SELECT e.id edge_id,f.id,f.name,f.facility_type,f.open_status,f.confidence_level,
                       ST_X(f.geom) lng,ST_Y(f.geom) lat
                FROM route_edge e
                JOIN accessible_facility f ON f.dataset_id=e.dataset_id
                  AND f.active=TRUE AND f.open_status <> 'CLOSED'
                  AND ST_DWithin(f.geom,e.geom,?)
                WHERE e.dataset_id=?
                ORDER BY e.external_id,f.external_id
                """,
                rs -> {
                    UUID edgeId = rs.getObject("edge_id", UUID.class);
                    result.computeIfAbsent(edgeId, ignored -> new ArrayList<>()).add(new RouteGraph.Facility(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("facility_type"),
                            rs.getString("open_status"),
                            rs.getString("confidence_level"),
                            new RouteGraph.Point(rs.getDouble("lng"), rs.getDouble("lat"))));
                },
                ROUTE_ASSOCIATION_TOLERANCE_DEGREES,
                datasetId);
        return result;
    }

    private Map<UUID, List<RouteGraph.Barrier>> loadBarriersByEdge(UUID datasetId) {
        Map<UUID, List<RouteGraph.Barrier>> result = new HashMap<>();
        jdbc.query(
                """
                SELECT e.id edge_id,b.id,b.title,b.barrier_type,b.confidence_level,b.active
                FROM route_edge e
                JOIN barrier_report b ON b.dataset_id=e.dataset_id
                  AND b.active=TRUE AND b.review_status='APPROVED'
                  AND (b.starts_at IS NULL OR b.starts_at <= CURRENT_TIMESTAMP)
                  AND (b.ends_at IS NULL OR b.ends_at >= CURRENT_TIMESTAMP)
                  AND ST_DWithin(b.geom,e.geom,?)
                WHERE e.dataset_id=?
                ORDER BY e.external_id,b.external_id
                """,
                rs -> {
                    UUID edgeId = rs.getObject("edge_id", UUID.class);
                    result.computeIfAbsent(edgeId, ignored -> new ArrayList<>()).add(new RouteGraph.Barrier(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            rs.getString("barrier_type"),
                            rs.getString("confidence_level"),
                            rs.getBoolean("active")));
                },
                ROUTE_ASSOCIATION_TOLERANCE_DEGREES,
                datasetId);
        return result;
    }

    private void requireEnabledGcj02Dataset(UUID datasetId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataset WHERE id=? AND enabled=TRUE AND coordinate_system='GCJ02'",
                Integer.class,
                datasetId);
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在、未启用或坐标系不受支持");
        }
    }

    private List<RouteGraph.Point> parseLineString(String json) {
        try {
            JsonNode coordinates = objectMapper.readTree(json).path("coordinates");
            List<RouteGraph.Point> result = new ArrayList<>();
            for (JsonNode coordinate : coordinates) {
                result.add(new RouteGraph.Point(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
            }
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("道路几何无法解析", exception);
        }
    }
}
