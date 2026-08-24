package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.BuildingView;
import static cn.barrierfreecampus.mapdata.MapDtos.BarrierView;
import static cn.barrierfreecampus.mapdata.MapDtos.DatasetView;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeView;
import static cn.barrierfreecampus.mapdata.MapDtos.EntranceView;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityView;
import static cn.barrierfreecampus.mapdata.MapDtos.MapSnapshot;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeView;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据集地图快照查询。
 */
@Component
public class MapSnapshotService {
    private final JdbcTemplate jdbc;
    private final MapDataSupport support;

    public MapSnapshotService(JdbcTemplate jdbc, MapDataSupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    public MapSnapshot snapshot(UUID datasetId, String bbox, boolean includeDisabled) {
        DatasetView dataset = support.requireDataset(datasetId, includeDisabled);
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
                        rs.getString("confidence_level"), support.parseJson(rs.getString("geometry"))), spatial);

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
                        support.parseJson(rs.getString("geometry"))), spatial);

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
                        support.parseJson(rs.getString("geometry"))), spatial);

        return new MapSnapshot(dataset, buildings, entrances, nodes, edges, facilities, barriers);
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
            throw support.badRequest("bbox 格式应为 minLng,minLat,maxLng,maxLat");
        }
    }

    private record Bounds(double minLng, double minLat, double maxLng, double maxLat) {
    }
}
