package cn.barrierfreecampus.analytics;

import static cn.barrierfreecampus.analytics.AnalyticsDtos.BarrierPoint;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.BarrierTrend;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.ConfidenceDistribution;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.DistributionItem;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.RouteRisk;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.SummaryView;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 治理统计查询：总览、设施分布、障碍空间、趋势、路线风险与可信度。
 */
@Component
public class AnalyticsQueryService {
    private static final ZoneId PROJECT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> FACILITY_TYPES = Set.of("ACCESSIBLE_ENTRANCE", "RAMP", "ELEVATOR",
            "ACCESSIBLE_TOILET", "REST_AREA", "ACCESSIBLE_PARKING", "DROP_OFF_POINT",
            "TRANSIT_BOARDING_POINT");
    private static final Set<String> BARRIER_TYPES = Set.of("STAIRS", "CONSTRUCTION", "TEMPORARY_CLOSURE",
            "DAMAGED_SURFACE", "NARROW_PATH", "VEHICLE_BLOCKING", "STEEP_SLOPE", "ELEVATOR_OUTAGE",
            "ENTRANCE_CLOSED", "WATERLOGGING");
    private static final Set<String> CONFIDENCE_LEVELS = Set.of("HIGH", "MEDIUM", "LOW", "UNKNOWN");

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsProperties properties;
    private final BuildingScoreService scoreService;

    public AnalyticsQueryService(NamedParameterJdbcTemplate jdbc, AnalyticsProperties properties,
                                 BuildingScoreService scoreService) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.scoreService = scoreService;
        if (Math.abs(properties.totalWeight() - 100.0) > 0.001) {
            throw new IllegalStateException("建筑无障碍评分权重之和必须为 100");
        }
    }

    public AnalyticsDtos.AnalyticsOverview overview(AnalyticsFilter filter) {
        validate(filter);
        MapSqlParameterSource params = parameters(filter);
        List<AnalyticsDtos.BuildingScore> scores = scoreService.buildingScores(params);
        List<DistributionItem> facilities = facilityDistribution(filter, params);
        List<BarrierPoint> barriers = barrierPoints(filter, params);
        List<BarrierTrend> trend = barrierTrend(filter, params);
        List<RouteRisk> risks = routeRisks(filter, params);
        List<ConfidenceDistribution> confidence = confidenceDistribution(filter, params);
        long facilityCount = facilities.stream().mapToLong(DistributionItem::count).sum();
        long routeCount = risks.stream().mapToLong(RouteRisk::sampleCount).sum();
        double averageScore = scores.stream().mapToDouble(AnalyticsDtos.BuildingScore::score).average().orElse(0);
        SummaryView summary = new SummaryView(scores.size(), facilityCount, barriers.size(), routeCount,
                round(averageScore));
        return new AnalyticsDtos.AnalyticsOverview(filter.view(), summary, scores, facilities, barriers, trend, risks,
                confidence, OffsetDateTime.now(PROJECT_ZONE));
    }

    public String filterContext(AnalyticsFilter filter) {
        return filterContext(filter.view());
    }

    private String filterContext(AnalyticsDtos.FilterView filter) {
        return "dataset=" + filter.datasetId() + ";building=" + value(filter.buildingId())
                + ";from=" + filter.from() + ";to=" + filter.to()
                + ";facility=" + value(filter.facilityType()) + ";barrier=" + value(filter.barrierType())
                + ";confidence=" + value(filter.confidenceLevel());
    }

    private List<DistributionItem> facilityDistribution(AnalyticsFilter filter, MapSqlParameterSource params) {
        String sql = """
                SELECT f.facility_type key,COUNT(*) count
                FROM accessible_facility f
                WHERE f.dataset_id=:datasetId AND f.active
                  AND (:buildingId::uuid IS NULL OR f.building_id=:buildingId::uuid)
                  AND (CAST(:facilityType AS varchar) IS NULL OR f.facility_type=:facilityType)
                  AND (CAST(:confidence AS varchar) IS NULL OR f.confidence_level=:confidence)
                GROUP BY f.facility_type ORDER BY count DESC,f.facility_type
                """;
        List<KeyCount> rows = jdbc.query(sql, params,
                (rs, row) -> new KeyCount(rs.getString("key"), rs.getLong("count")));
        long total = rows.stream().mapToLong(KeyCount::count).sum();
        return rows.stream().map(item -> new DistributionItem(item.key(), facilityLabel(item.key()), item.count(),
                round(total == 0 ? 0 : item.count() * 100.0 / total))).toList();
    }

    private List<BarrierPoint> barrierPoints(AnalyticsFilter filter, MapSqlParameterSource params) {
        String sql = """
                SELECT r.id,r.title,r.barrier_type,r.confidence_level,r.review_status,
                  ST_X(ST_PointOnSurface(r.geom)) lng,ST_Y(ST_PointOnSurface(r.geom)) lat,
                  CASE WHEN r.barrier_type IN ('TEMPORARY_CLOSURE','ENTRANCE_CLOSED','ELEVATOR_OUTAGE','STAIRS') THEN 3 ELSE 1 END impact_weight
                FROM barrier_report r
                WHERE r.dataset_id=:datasetId AND r.review_status='APPROVED' AND r.active
                  AND (r.starts_at IS NULL OR r.starts_at<=CURRENT_TIMESTAMP)
                  AND (r.ends_at IS NULL OR r.ends_at>CURRENT_TIMESTAMP)
                  AND r.created_at>=:fromAt AND r.created_at<:toExclusive
                  AND (CAST(:barrierType AS varchar) IS NULL OR r.barrier_type=:barrierType)
                  AND (CAST(:confidence AS varchar) IS NULL OR r.confidence_level=:confidence)
                  AND (:buildingId::uuid IS NULL OR EXISTS (SELECT 1 FROM building b WHERE b.id=:buildingId::uuid AND ST_DWithin(ST_SetSRID(ST_PointOnSurface(r.geom),4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)))
                ORDER BY impact_weight DESC,r.created_at DESC
                """;
        return jdbc.query(sql, params, (rs, row) -> new BarrierPoint(rs.getObject("id", UUID.class),
                rs.getString("title"), rs.getString("barrier_type"), rs.getString("confidence_level"),
                rs.getString("review_status"), rs.getDouble("lng"), rs.getDouble("lat"),
                rs.getInt("impact_weight")));
    }

    private List<BarrierTrend> barrierTrend(AnalyticsFilter filter, MapSqlParameterSource params) {
        String sql = """
                SELECT day::date,
                  COUNT(r.id) FILTER (WHERE r.created_at>=day AND r.created_at<day+INTERVAL '1 day') submitted,
                  COUNT(r.id) FILTER (WHERE r.reviewed_at>=day AND r.reviewed_at<day+INTERVAL '1 day' AND r.review_status='APPROVED') approved
                FROM generate_series(CAST(:fromAt AS timestamptz),CAST(:toAt AS timestamptz),INTERVAL '1 day') day
                LEFT JOIN barrier_report r ON r.dataset_id=:datasetId
                  AND (r.created_at>=day AND r.created_at<day+INTERVAL '1 day' OR r.reviewed_at>=day AND r.reviewed_at<day+INTERVAL '1 day')
                  AND (CAST(:barrierType AS varchar) IS NULL OR r.barrier_type=:barrierType)
                  AND (CAST(:confidence AS varchar) IS NULL OR r.confidence_level=:confidence)
                  AND (:buildingId::uuid IS NULL OR EXISTS (SELECT 1 FROM building b WHERE b.id=:buildingId::uuid AND ST_DWithin(ST_SetSRID(ST_PointOnSurface(r.geom),4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)))
                GROUP BY day ORDER BY day
                """;
        return jdbc.query(sql, params, (rs, row) -> new BarrierTrend(rs.getDate(1).toLocalDate(),
                rs.getLong("submitted"), rs.getLong("approved")));
    }

    private List<RouteRisk> routeRisks(AnalyticsFilter filter, MapSqlParameterSource params) {
        String sql = """
                SELECT route->>'profile' profile,COUNT(*) sample_count,
                  AVG(COALESCE((route->>'distanceM')::double precision,0)) avg_distance,
                  AVG(COALESCE((route->>'estimatedMinutes')::double precision,0)) avg_minutes,
                  AVG(COALESCE((route->'riskSummary'->>'highRiskEdges')::double precision,0)) avg_high_risk,
                  AVG(COALESCE(jsonb_array_length(route->'warnings'),0)) avg_warnings,
                  COUNT(*) FILTER (WHERE COALESCE((route->'riskSummary'->>'fallbackRoute')::boolean,FALSE)) fallback_count
                FROM route_history h CROSS JOIN LATERAL jsonb_array_elements(h.result_json->'routes') route
                WHERE h.dataset_id=:datasetId AND h.created_at>=:fromAt AND h.created_at<:toExclusive
                  AND (:buildingId::uuid IS NULL OR EXISTS (
                    SELECT 1 FROM building b JOIN route_node n ON n.id IN (h.start_node_id,h.end_node_id)
                    WHERE b.id=:buildingId::uuid AND ST_DWithin(ST_SetSRID(n.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)))
                GROUP BY route->>'profile'
                ORDER BY CASE route->>'profile' WHEN 'SHORTEST' THEN 1 WHEN 'ACCESSIBLE' THEN 2 ELSE 3 END
                """;
        return jdbc.query(sql, params, (rs, row) -> new RouteRisk(rs.getString("profile"),
                rs.getLong("sample_count"), round(rs.getDouble("avg_distance")),
                round(rs.getDouble("avg_minutes")), round(rs.getDouble("avg_high_risk")),
                round(rs.getDouble("avg_warnings")), rs.getLong("fallback_count")));
    }

    private List<ConfidenceDistribution> confidenceDistribution(AnalyticsFilter filter,
                                                                 MapSqlParameterSource params) {
        String sql = """
                SELECT entity_type,confidence_level,COUNT(*) count FROM (
                  SELECT 'BUILDING' entity_type,b.confidence_level FROM building b WHERE b.dataset_id=:datasetId AND b.active AND (:buildingId::uuid IS NULL OR b.id=:buildingId::uuid)
                  UNION ALL
                  SELECT 'ENTRANCE',i.confidence_level FROM building_entrance i WHERE i.dataset_id=:datasetId AND i.active AND (:buildingId::uuid IS NULL OR i.building_id=:buildingId::uuid)
                  UNION ALL
                  SELECT 'EDGE',e.confidence_level FROM route_edge e WHERE e.dataset_id=:datasetId AND e.status='ACTIVE' AND (:buildingId::uuid IS NULL OR EXISTS (SELECT 1 FROM building b WHERE b.id=:buildingId::uuid AND ST_DWithin(ST_SetSRID(e.geom,4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)))
                  UNION ALL
                  SELECT 'FACILITY',f.confidence_level FROM accessible_facility f WHERE f.dataset_id=:datasetId AND f.active AND (:buildingId::uuid IS NULL OR f.building_id=:buildingId::uuid) AND (CAST(:facilityType AS varchar) IS NULL OR f.facility_type=:facilityType)
                  UNION ALL
                  SELECT 'BARRIER',r.confidence_level FROM barrier_report r WHERE r.dataset_id=:datasetId AND r.created_at>=:fromAt AND r.created_at<:toExclusive AND (CAST(:barrierType AS varchar) IS NULL OR r.barrier_type=:barrierType) AND (:buildingId::uuid IS NULL OR EXISTS (SELECT 1 FROM building b WHERE b.id=:buildingId::uuid AND ST_DWithin(ST_SetSRID(ST_PointOnSurface(r.geom),4326)::geography,ST_SetSRID(ST_Centroid(b.geom),4326)::geography,:proximity)))
                ) items
                WHERE (CAST(:confidence AS varchar) IS NULL OR confidence_level=:confidence)
                GROUP BY entity_type,confidence_level
                """;
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (String type : List.of("BUILDING", "ENTRANCE", "EDGE", "FACILITY", "BARRIER")) {
            counts.put(type, new long[4]);
        }
        jdbc.query(sql, params, rs -> {
            int index = switch (rs.getString("confidence_level")) {
                case "HIGH" -> 0; case "MEDIUM" -> 1; case "LOW" -> 2; default -> 3;
            };
            counts.get(rs.getString("entity_type"))[index] = rs.getLong("count");
        });
        return counts.entrySet().stream().map(entry -> new ConfidenceDistribution(entry.getKey(),
                entityLabel(entry.getKey()), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2],
                entry.getValue()[3])).toList();
    }

    private void validate(AnalyticsFilter filter) {
        if (filter.datasetId() == null) throw new ResponseStatusException(BAD_REQUEST, "datasetId 不能为空");
        if (filter.facilityType() != null && !FACILITY_TYPES.contains(filter.facilityType()))
            throw new ResponseStatusException(BAD_REQUEST, "设施类型无效");
        if (filter.barrierType() != null && !BARRIER_TYPES.contains(filter.barrierType()))
            throw new ResponseStatusException(BAD_REQUEST, "障碍类型无效");
        if (filter.confidenceLevel() != null && !CONFIDENCE_LEVELS.contains(filter.confidenceLevel()))
            throw new ResponseStatusException(BAD_REQUEST, "可信等级无效");
        Integer datasets = jdbc.queryForObject("SELECT COUNT(*) FROM dataset WHERE id=:id AND enabled",
                new MapSqlParameterSource("id", filter.datasetId()), Integer.class);
        if (datasets == null || datasets == 0) throw new ResponseStatusException(NOT_FOUND, "数据集不存在或未启用");
        if (filter.buildingId() != null) {
            Integer buildings = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM building WHERE id=:buildingId AND dataset_id=:datasetId AND active",
                    new MapSqlParameterSource().addValue("buildingId", filter.buildingId())
                            .addValue("datasetId", filter.datasetId()), Integer.class);
            if (buildings == null || buildings == 0) throw new ResponseStatusException(BAD_REQUEST, "建筑不属于当前数据集");
        }
    }

    private MapSqlParameterSource parameters(AnalyticsFilter filter) {
        return new MapSqlParameterSource()
                .addValue("datasetId", filter.datasetId())
                .addValue("buildingId", filter.buildingId())
                .addValue("facilityType", filter.facilityType())
                .addValue("barrierType", filter.barrierType())
                .addValue("confidence", filter.confidenceLevel())
                .addValue("proximity", properties.getProximityMeters())
                .addValue("fromAt", filter.from().atStartOfDay(PROJECT_ZONE).toOffsetDateTime())
                .addValue("toAt", filter.to().atStartOfDay(PROJECT_ZONE).toOffsetDateTime())
                .addValue("toExclusive", filter.to().plusDays(1).atStartOfDay(PROJECT_ZONE).toOffsetDateTime());
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String facilityLabel(String key) {
        return Map.of("ACCESSIBLE_ENTRANCE", "无障碍入口", "RAMP", "坡道", "ELEVATOR", "电梯",
                "ACCESSIBLE_TOILET", "无障碍卫生间", "REST_AREA", "休息点",
                "ACCESSIBLE_PARKING", "无障碍停车位", "DROP_OFF_POINT", "上下客点",
                "TRANSIT_BOARDING_POINT", "公交乘车点").getOrDefault(key, key);
    }

    private String entityLabel(String key) {
        return Map.of("BUILDING", "建筑", "ENTRANCE", "入口", "EDGE", "道路", "FACILITY", "设施",
                "BARRIER", "障碍").getOrDefault(key, key);
    }

    private String value(Object value) { return value == null ? "ALL" : String.valueOf(value); }

    private record KeyCount(String key, long count) {}
}
