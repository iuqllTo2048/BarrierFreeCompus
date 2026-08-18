package cn.barrierfreecampus.agent;

import static cn.barrierfreecampus.agent.AgentDtos.*;

import cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanRequest;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import cn.barrierfreecampus.routing.RoutingDtos.RouteResult;
import cn.barrierfreecampus.routing.RoutingService;
import dev.langchain4j.agent.tool.Tool;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AgentTools {
    private static final Set<String> BLOCKING_BARRIERS = Set.of(
            "TEMPORARY_CLOSURE", "CONSTRUCTION", "VEHICLE_BLOCKING", "ENTRANCE_CLOSED");
    private final JdbcTemplate jdbc;
    private final RoutingService routingService;
    private final AgentRepository repository;

    public AgentTools(JdbcTemplate jdbc, RoutingService routingService, AgentRepository repository) {
        this.jdbc = jdbc;
        this.routingService = routingService;
        this.repository = repository;
    }

    @Tool("在启用的校园数据集中搜索建筑、入口、路网节点或无障碍设施")
    public List<PlaceResult> searchCampusPlace(UUID datasetId, String query, int requestedLimit) {
        requireEnabledDataset(datasetId);
        int limit = Math.max(1, Math.min(8, requestedLimit));
        String sql = """
                WITH places AS (
                  SELECT b.id,'BUILDING' kind,b.name,b.external_id,
                    ST_X(ST_Centroid(b.geom)) lng,ST_Y(ST_Centroid(b.geom)) lat,b.confidence_level
                  FROM building b WHERE b.dataset_id=? AND b.active=TRUE
                  UNION ALL
                  SELECT e.id,'ENTRANCE',e.name,e.external_id,ST_X(e.geom),ST_Y(e.geom),e.confidence_level
                  FROM building_entrance e WHERE e.dataset_id=? AND e.active=TRUE AND e.status<>'CLOSED'
                  UNION ALL
                  SELECT n.id,'NODE',COALESCE(n.name,n.external_id),n.external_id,ST_X(n.geom),ST_Y(n.geom),n.confidence_level
                  FROM route_node n WHERE n.dataset_id=? AND n.active=TRUE
                  UNION ALL
                  SELECT f.id,'FACILITY',f.name,f.external_id,ST_X(f.geom),ST_Y(f.geom),f.confidence_level
                  FROM accessible_facility f WHERE f.dataset_id=? AND f.active=TRUE AND f.open_status<>'CLOSED'
                )
                SELECT p.*,(SELECT n.id FROM route_node n WHERE n.dataset_id=? AND n.active=TRUE
                  ORDER BY n.geom <-> ST_SetSRID(ST_MakePoint(p.lng,p.lat),0) LIMIT 1) nearest_node_id
                FROM places p
                WHERE LOWER(p.name) LIKE '%'||LOWER(?)||'%'
                   OR LOWER(?) LIKE '%'||LOWER(p.name)||'%'
                ORDER BY CASE WHEN LOWER(p.name)=LOWER(?) THEN 0 ELSE 1 END,p.name LIMIT ?
                """;
        return jdbc.query(sql, (rs, row) -> new PlaceResult(
                rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("name"),
                rs.getString("external_id"), rs.getObject("nearest_node_id", UUID.class),
                rs.getDouble("lng"), rs.getDouble("lat"), rs.getString("confidence_level")),
                datasetId, datasetId, datasetId, datasetId, datasetId, query, query, query, limit);
    }

    @Tool("调用自建路网 A* 计算最短、无障碍优先和综合路线")
    public RoutePlanResponse calculateAccessibleRoutes(RoutePlanRequest request) {
        requireEnabledDataset(request.datasetId());
        return routingService.plan(request);
    }

    @Tool("从已计算路线中查找沿途有效无障碍设施")
    public List<FacilitySummary> searchFacilitiesNearRoute(RoutePlanResponse response) {
        Map<UUID, FacilitySummary> unique = new LinkedHashMap<>();
        response.routes().stream().flatMap(route -> route.facilities().stream()).forEach(facility ->
                unique.putIfAbsent(facility.id(), new FacilitySummary(facility.id(), facility.name(),
                        facility.facilityType(), facility.openStatus(), facility.confidenceLevel(),
                        facility.lng(), facility.lat())));
        return List.copyOf(unique.values());
    }

    @Tool("查询数据集中已审核、生效且未过期的动态障碍")
    public List<BarrierSummary> searchActiveBarriers(UUID datasetId) {
        requireEnabledDataset(datasetId);
        return jdbc.query("""
                SELECT id,title,barrier_type,confidence_level FROM barrier_report
                WHERE dataset_id=? AND review_status='APPROVED' AND active=TRUE
                  AND (starts_at IS NULL OR starts_at<=CURRENT_TIMESTAMP)
                  AND (ends_at IS NULL OR ends_at>CURRENT_TIMESTAMP)
                ORDER BY updated_at DESC LIMIT 50
                """, (rs, row) -> new BarrierSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("barrier_type"), rs.getString("confidence_level"),
                BLOCKING_BARRIERS.contains(rs.getString("barrier_type"))), datasetId);
    }

    @Tool("使用确定性规则比较路线距离、风险、楼梯和警告")
    public RouteComparison compareRoutes(RoutePlanResponse response) {
        List<RouteComparisonItem> items = response.routes().stream().map(route -> new RouteComparisonItem(
                route.profile().name(), route.distanceM(), route.estimatedMinutes(), route.riskSummary().level(),
                route.stairsCount(), route.warnings().size())).toList();
        RouteResult recommended = response.routes().stream().min(Comparator
                .comparingInt((RouteResult route) -> riskRank(route.riskSummary().level()))
                .thenComparingInt(RouteResult::stairsCount)
                .thenComparingDouble(RouteResult::distanceM)).orElse(null);
        List<String> reasons = new ArrayList<>();
        if (recommended != null) {
            reasons.add("优先选择风险等级较低且楼梯更少的路线");
            if (!recommended.warnings().isEmpty()) reasons.addAll(recommended.warnings());
            if (recommended.riskSummary().fallbackRoute()) reasons.add("该路线是约束放宽后的风险最低可达方案");
        }
        return new RouteComparison(recommended == null ? null : recommended.profile().name(), items, reasons);
    }

    @Tool("创建障碍上报草稿；草稿不会写入正式障碍表")
    public BarrierDraftView createBarrierReportDraft(UUID conversationId, BarrierSubmitRequest payload) {
        UUID id = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);
        repository.saveDraft(id, conversationId, AgentExecutionContext.requireUsername(), payload, expiresAt);
        return new BarrierDraftView(id, payload, "PENDING", expiresAt);
    }

    private void requireEnabledDataset(UUID datasetId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataset WHERE id=? AND enabled=TRUE", Integer.class, datasetId);
        if (count == null || count != 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "数据集不存在或未启用");
        }
    }

    private int riskRank(String risk) {
        return switch (risk) { case "LOW" -> 0; case "UNKNOWN" -> 1; case "MEDIUM" -> 2; default -> 3; };
    }
}
