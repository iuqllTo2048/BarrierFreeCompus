package cn.barrierfreecampus.analytics;

import static cn.barrierfreecampus.analytics.AnalyticsDtos.BuildingScore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 建筑无障碍评分。
 */
@Component
public class BuildingScoreService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsProperties properties;

    public BuildingScoreService(NamedParameterJdbcTemplate jdbc, AnalyticsProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<BuildingScore> buildingScores(MapSqlParameterSource params) {
        String sql = """
                SELECT b.id,b.name,ST_X(ST_Centroid(b.geom)) lng,ST_Y(ST_Centroid(b.geom)) lat,
                  (SELECT COUNT(*) FROM building_entrance i WHERE i.building_id=b.id AND i.active) entrance_total,
                  (SELECT COUNT(*) FROM building_entrance i WHERE i.building_id=b.id AND i.active AND i.accessible AND i.status='OPEN') entrance_accessible,
                  (SELECT COUNT(*) FROM accessible_facility f WHERE f.building_id=b.id AND f.active AND f.facility_type='ELEVATOR') elevator_total,
                  (SELECT COUNT(*) FROM accessible_facility f WHERE f.building_id=b.id AND f.active AND f.facility_type='ELEVATOR' AND f.open_status='OPEN') elevator_open,
                  (SELECT COUNT(*) FROM accessible_facility f WHERE f.building_id=b.id AND f.active AND f.facility_type='ACCESSIBLE_TOILET') toilet_total,
                  (SELECT COUNT(*) FROM accessible_facility f WHERE f.building_id=b.id AND f.active AND f.facility_type='ACCESSIBLE_TOILET' AND f.open_status='OPEN') toilet_open,
                  (SELECT COUNT(*) FROM route_edge e WHERE e.dataset_id=b.dataset_id AND e.status='ACTIVE' AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) road_total,
                  (SELECT COUNT(*) FROM route_edge e WHERE e.dataset_id=b.dataset_id AND e.status='ACTIVE' AND NOT e.has_stairs AND e.slope_level IN ('FLAT','GENTLE','MODERATE') AND e.width_level IN ('STANDARD','WIDE') AND e.risk_level IN ('LOW','MEDIUM') AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) road_accessible,
                  (SELECT COALESCE(SUM(CASE WHEN r.barrier_type IN ('TEMPORARY_CLOSURE','ENTRANCE_CLOSED','ELEVATOR_OUTAGE','STAIRS') THEN 3 ELSE 1 END),0) FROM barrier_report r WHERE r.dataset_id=b.dataset_id AND r.review_status='APPROVED' AND r.active AND (r.starts_at IS NULL OR r.starts_at<=CURRENT_TIMESTAMP) AND (r.ends_at IS NULL OR r.ends_at>CURRENT_TIMESTAMP) AND ST_DWithin(ST_SetSRID(ST_PointOnSurface(r.geom),4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) barrier_weight,
                  CASE WHEN b.category<>'OTHER' THEN 1 ELSE 0 END category_known,
                  CASE WHEN b.confidence_level<>'UNKNOWN' THEN 1 ELSE 0 END building_confidence_known,
                  (SELECT COUNT(*) FROM building_entrance i WHERE i.building_id=b.id AND i.active AND i.status<>'UNKNOWN') entrance_status_known,
                  (SELECT COUNT(*) FROM building_entrance i WHERE i.building_id=b.id AND i.active AND i.confidence_level<>'UNKNOWN') entrance_confidence_known,
                  (SELECT COUNT(*) FROM route_edge e WHERE e.dataset_id=b.dataset_id AND e.status='ACTIVE' AND e.slope_level<>'UNKNOWN' AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) slope_known,
                  (SELECT COUNT(*) FROM route_edge e WHERE e.dataset_id=b.dataset_id AND e.status='ACTIVE' AND e.width_level<>'UNKNOWN' AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) width_known,
                  (SELECT COUNT(*) FROM route_edge e WHERE e.dataset_id=b.dataset_id AND e.status='ACTIVE' AND e.surface_type<>'UNKNOWN' AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) surface_known,
                  (SELECT COUNT(*) FROM route_edge e WHERE e.dataset_id=b.dataset_id AND e.status='ACTIVE' AND e.confidence_level<>'UNKNOWN' AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)) road_confidence_known
                FROM building b
                WHERE b.dataset_id=:datasetId AND b.active
                  AND (:buildingId::uuid IS NULL OR b.id=:buildingId::uuid)
                  AND (CAST(:confidence AS varchar) IS NULL OR b.confidence_level=:confidence)
                """;
        List<BuildingScore> result = jdbc.query(sql, params, (rs, row) -> score(rs));
        return result.stream().sorted(Comparator.comparingDouble(BuildingScore::score).reversed()
                .thenComparing(BuildingScore::name)).toList();
    }

    private BuildingScore score(ResultSet rs) throws SQLException {
        long entrances = rs.getLong("entrance_total");
        long roads = rs.getLong("road_total");
        double entrance = ratio(rs.getLong("entrance_accessible"), entrances) * properties.getAccessibleEntrance();
        double elevator = ratio(rs.getLong("elevator_open"), Math.max(1, rs.getLong("elevator_total")))
                * properties.getElevator();
        double toilet = ratio(rs.getLong("toilet_open"), Math.max(1, rs.getLong("toilet_total")))
                * properties.getAccessibleToilet();
        double road = ratio(rs.getLong("road_accessible"), roads) * properties.getRoadAccessibility();
        double barrier = properties.getBarrierImpact()
                * (1 - Math.min(1, rs.getLong("barrier_weight") / 5.0));
        double facts = rs.getInt("category_known") + rs.getInt("building_confidence_known")
                + (entrances > 0 ? 1 : 0)
                + ratio(rs.getLong("entrance_status_known"), entrances)
                + ratio(rs.getLong("entrance_confidence_known"), entrances)
                + (roads > 0 ? 1 : 0)
                + ratio(rs.getLong("slope_known"), roads)
                + ratio(rs.getLong("width_known"), roads)
                + ratio(rs.getLong("surface_known"), roads)
                + ratio(rs.getLong("road_confidence_known"), roads);
        double completeness = facts / 10.0 * properties.getDataCompleteness();
        List<String> reasons = new ArrayList<>();
        if (entrances == 0) reasons.add("缺少入口数据");
        else if (rs.getLong("entrance_accessible") == 0) reasons.add("未记录开放的无障碍入口");
        if (rs.getLong("elevator_open") == 0) reasons.add("未记录开放电梯");
        if (rs.getLong("toilet_open") == 0) reasons.add("未记录开放的无障碍卫生间");
        if (roads == 0) reasons.add("周边道路数据不足");
        else if (rs.getLong("road_accessible") < roads) reasons.add("周边存在楼梯、陑坡、狭窄或高风险道路");
        if (rs.getLong("barrier_weight") > 0) reasons.add("周边存在已生效障碍");
        boolean sufficient = entrances > 0 && roads > 0 && completeness >= properties.getDataCompleteness() * 0.6;
        double total = entrance + elevator + toilet + road + barrier + completeness;
        return new BuildingScore(rs.getObject("id", UUID.class), rs.getString("name"), round(total),
                round(entrance), round(elevator), round(toilet), round(road), round(barrier),
                round(completeness), sufficient, List.copyOf(reasons), rs.getDouble("lng"), rs.getDouble("lat"));
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0 : Math.min(1, numerator / (double) denominator);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
