package cn.barrierfreecampus.analytics;

import static cn.barrierfreecampus.analytics.AnalyticsDtos.*;

import cn.barrierfreecampus.agent.AiGateway;
import cn.barrierfreecampus.agent.AiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final ZoneId PROJECT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> FACILITY_TYPES = Set.of("ACCESSIBLE_ENTRANCE", "RAMP", "ELEVATOR",
            "ACCESSIBLE_TOILET", "REST_AREA", "ACCESSIBLE_PARKING", "DROP_OFF_POINT",
            "TRANSIT_BOARDING_POINT");
    private static final Set<String> BARRIER_TYPES = Set.of("STAIRS", "CONSTRUCTION", "TEMPORARY_CLOSURE",
            "DAMAGED_SURFACE", "NARROW_PATH", "VEHICLE_BLOCKING", "STEEP_SLOPE", "ELEVATOR_OUTAGE",
            "ENTRANCE_CLOSED", "WATERLOGGING");
    private static final Set<String> CONFIDENCE_LEVELS = Set.of("HIGH", "MEDIUM", "LOW", "UNKNOWN");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsProperties properties;
    private final AiGateway gateway;
    private final AiProperties aiProperties;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, AnalyticsProperties properties,
                            AiGateway gateway, AiProperties aiProperties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.gateway = gateway;
        this.aiProperties = aiProperties;
        if (Math.abs(properties.totalWeight() - 100.0) > 0.001) {
            throw new IllegalStateException("建筑无障碍评分权重之和必须为 100");
        }
    }

    public AnalyticsOverview overview(AnalyticsFilter filter) {
        validate(filter);
        MapSqlParameterSource params = parameters(filter);
        List<BuildingScore> scores = buildingScores(params);
        List<DistributionItem> facilities = facilityDistribution(filter, params);
        List<BarrierPoint> barriers = barrierPoints(filter, params);
        List<BarrierTrend> trend = barrierTrend(filter, params);
        List<RouteRisk> risks = routeRisks(filter, params);
        List<ConfidenceDistribution> confidence = confidenceDistribution(filter, params);
        long facilityCount = facilities.stream().mapToLong(DistributionItem::count).sum();
        long routeCount = risks.stream().mapToLong(RouteRisk::sampleCount).sum();
        double averageScore = scores.stream().mapToDouble(BuildingScore::score).average().orElse(0);
        SummaryView summary = new SummaryView(scores.size(), facilityCount, barriers.size(), routeCount,
                round(averageScore));
        return new AnalyticsOverview(filter.view(), summary, scores, facilities, barriers, trend, risks,
                confidence, OffsetDateTime.now(PROJECT_ZONE));
    }

    public GovernanceSummary governanceSummary(AnalyticsFilter filter) {
        AnalyticsOverview overview = overview(filter);
        String rules = ruleSummary(overview);
        if (!aiProperties.isEnabled()) {
            return new GovernanceSummary(false, false, "RULES", aiProperties.effectiveModelName(), rules);
        }
        try {
            String context = factualContext(overview);
            String aiText = gateway.summarizeGovernance(context);
            if (containsUnknownNumber(aiText, context)) {
                log.warn("治理总结包含未经后端验证的数值，已改用规则摘要");
                return new GovernanceSummary(true, true, "RULES", aiProperties.effectiveModelName(),
                        rules + "\n\n模型输出包含未经验证的数值，已自动改用规则摘要。");
            }
            return new GovernanceSummary(true, false, "MODEL", aiProperties.effectiveModelName(), aiText);
        } catch (RuntimeException exception) {
            log.warn("治理总结模型不可用，返回规则摘要 error={}", safeError(exception));
            return new GovernanceSummary(true, true, "RULES", aiProperties.effectiveModelName(),
                    rules + "\n\n智能治理建议暂时不可用，上述结论来自结构化统计。");
        }
    }

    public byte[] csv(AnalyticsFilter filter) {
        AnalyticsOverview overview = overview(filter);
        StringBuilder csv = new StringBuilder("\uFEFF统计区段,维度,指标,数值,单位,筛选上下文\r\n");
        String context = filterContext(filter);
        for (BuildingScore item : overview.buildingScores()) {
            row(csv, "建筑评分", item.name(), "总分", item.score(), "分", context);
            row(csv, "建筑评分", item.name(), "无障碍入口", item.entranceScore(), "分", context);
            row(csv, "建筑评分", item.name(), "道路可达性", item.roadScore(), "分", context);
        }
        overview.facilityDistribution().forEach(item ->
                row(csv, "设施分布", item.label(), "数量", item.count(), "个", context));
        overview.barrierPoints().forEach(item -> {
            row(csv, "障碍空间", item.title(), "影响权重", item.impactWeight(), "级", context);
            row(csv, "障碍空间", item.title(), "坐标", item.lng() + ";" + item.lat(), "GCJ-02", context);
        });
        overview.barrierTrend().forEach(item -> {
            row(csv, "障碍趋势", item.date().toString(), "新增上报", item.submitted(), "条", context);
            row(csv, "障碍趋势", item.date().toString(), "审核通过", item.approved(), "条", context);
        });
        overview.routeRisks().forEach(item -> {
            row(csv, "路线风险", profileLabel(item.profile()), "样本数", item.sampleCount(), "次", context);
            row(csv, "路线风险", profileLabel(item.profile()), "平均高风险边", item.averageHighRiskEdges(), "条", context);
        });
        overview.confidenceDistribution().forEach(item -> {
            row(csv, "可信度", item.entityLabel(), "HIGH", item.high(), "条", context);
            row(csv, "可信度", item.entityLabel(), "MEDIUM", item.medium(), "条", context);
            row(csv, "可信度", item.entityLabel(), "LOW", item.low(), "条", context);
            row(csv, "可信度", item.entityLabel(), "UNKNOWN", item.unknown(), "条", context);
        });
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<BuildingScore> buildingScores(MapSqlParameterSource params) {
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

    private String ruleSummary(AnalyticsOverview overview) {
        BuildingScore lowest = overview.buildingScores().stream().min(Comparator.comparingDouble(BuildingScore::score))
                .orElse(null);
        long unknown = overview.confidenceDistribution().stream().mapToLong(ConfidenceDistribution::unknown).sum();
        String building = lowest == null ? "当前筛选范围没有可评分建筑。"
                : "建筑平均分为 " + overview.summary().averageBuildingScore() + "，当前最低为“"
                + lowest.name() + "”（" + lowest.score() + " 分）。";
        String barrier = overview.summary().effectiveBarriers() == 0 ? "当前筛选范围未发现生效障碍。"
                : "当前有 " + overview.summary().effectiveBarriers() + " 项生效障碍，建议优先处理影响权重为 3 的阻断类障碍。";
        String quality = unknown == 0 ? "当前统计对象中无 UNKNOWN 可信度记录。"
                : "当前有 " + unknown + " 条 UNKNOWN 可信度记录，建议安排数据核验。";
        return building + "\n" + barrier + "\n" + quality;
    }

    private String factualContext(AnalyticsOverview overview) {
        String lowest = overview.buildingScores().stream().min(Comparator.comparingDouble(BuildingScore::score))
                .map(item -> item.name() + "=" + item.score() + ",原因=" + String.join("/", item.reasons()))
                .orElse("无");
        return "筛选=" + filterContext(overview.filter()) + "\n"
                + "建筑数=" + overview.summary().buildings() + "\n"
                + "建筑平均分=" + overview.summary().averageBuildingScore() + "\n"
                + "最低分建筑=" + lowest + "\n"
                + "设施数=" + overview.summary().facilities() + "\n"
                + "生效障碍数=" + overview.summary().effectiveBarriers() + "\n"
                + "路线样本数=" + overview.summary().routePlans() + "\n"
                + "可信度分布=" + overview.confidenceDistribution();
    }

    private String filterContext(AnalyticsFilter filter) { return filterContext(filter.view()); }
    private String filterContext(FilterView filter) {
        return "dataset=" + filter.datasetId() + ";building=" + value(filter.buildingId())
                + ";from=" + filter.from() + ";to=" + filter.to()
                + ";facility=" + value(filter.facilityType()) + ";barrier=" + value(filter.barrierType())
                + ";confidence=" + value(filter.confidenceLevel());
    }

    private void row(StringBuilder target, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) target.append(',');
            target.append(csvCell(String.valueOf(cells[i])));
        }
        target.append("\r\n");
    }

    private String csvCell(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0 : Math.min(1, numerator / (double) denominator);
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

    private String profileLabel(String key) {
        return switch (key == null ? "" : key.toUpperCase(Locale.ROOT)) {
            case "SHORTEST" -> "最短路线"; case "ACCESSIBLE" -> "无障碍优先";
            case "BALANCED" -> "综合路线"; default -> key;
        };
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(300, message.length()));
    }

    private boolean containsUnknownNumber(String text, String context) {
        Set<String> allowed = new java.util.HashSet<>(Set.of("1", "2", "3"));
        Matcher contextNumbers = NUMBER.matcher(context);
        while (contextNumbers.find()) allowed.add(contextNumbers.group());
        Matcher outputNumbers = NUMBER.matcher(text);
        while (outputNumbers.find()) {
            if (!allowed.contains(outputNumbers.group())) return true;
        }
        return false;
    }

    private String value(Object value) { return value == null ? "ALL" : String.valueOf(value); }
    private record KeyCount(String key, long count) {}
}
