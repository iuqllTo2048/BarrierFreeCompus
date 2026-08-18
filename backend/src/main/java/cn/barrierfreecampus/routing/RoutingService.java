package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import static cn.barrierfreecampus.routing.RoutingDtos.RoutePlanRequest;
import static cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import static cn.barrierfreecampus.routing.RoutingDtos.RoutePreferences;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoutingService {
    private final RoutingRepository repository;
    private final ObjectMapper objectMapper;
    private final RouteCostPolicy costPolicy = new RouteCostPolicy();
    private final AStarRouter router = new AStarRouter(costPolicy);

    public RoutingService(RoutingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public RoutePlanResponse plan(RoutePlanRequest request) {
        RoutePreferences preferences = request.effectivePreferences();
        RouteGraph.GraphData graph = repository.loadGraph(request.datasetId());
        requireActiveNode(graph, request.startNodeId(), "起点");
        requireActiveNode(graph, request.endNodeId(), "终点");

        Map<List<UUID>, CandidateGroup> uniqueCandidates = new LinkedHashMap<>();
        List<String> notices = new ArrayList<>();
        for (RouteProfile profile : RouteProfile.values()) {
            AStarRouter.SearchOutcome outcome = router.search(
                    graph,
                    request.startNodeId(),
                    request.endNodeId(),
                    profile,
                    request.mobilityMode(),
                    request.travelPeriod(),
                    preferences,
                    false);
            if (!outcome.found()) {
                outcome = router.search(
                        graph,
                        request.startNodeId(),
                        request.endNodeId(),
                        profile,
                        request.mobilityMode(),
                        request.travelPeriod(),
                        preferences,
                        true);
            }
            if (!outcome.found()) {
                notices.add(profileLabel(profile) + "未找到可达路线");
                continue;
            }
            List<UUID> signature = outcome.path().stream().map(arc -> arc.edge().id()).toList();
            CandidateGroup group = uniqueCandidates.get(signature);
            if (group == null) {
                uniqueCandidates.put(signature, new CandidateGroup(profile, new ArrayList<>(List.of(profile)), outcome));
            } else {
                group.profiles().add(profile);
                notices.add(profileLabel(profile) + "与" + profileLabel(group.primaryProfile()) + "结果相同，已合并展示");
            }
        }

        if (uniqueCandidates.isEmpty()) {
            notices.add("当前起终点在既定安全约束下不存在可达路线");
        }
        List<RoutingDtos.RouteResult> routes = uniqueCandidates.values().stream()
                .map(group -> toResult(
                        group.primaryProfile(),
                        List.copyOf(group.profiles()),
                        group.outcome(),
                        request.mobilityMode(),
                        request.travelPeriod(),
                        preferences,
                        graph,
                        request.startNodeId()))
                .toList();
        return new RoutePlanResponse(
                request.datasetId(),
                request.startNodeId(),
                request.endNodeId(),
                request.mobilityMode(),
                request.travelPeriod(),
                routes,
                List.copyOf(notices),
                null);
    }

    private RoutingDtos.RouteResult toResult(
            RouteProfile profile,
            List<RouteProfile> equivalentProfiles,
            AStarRouter.SearchOutcome outcome,
            MobilityMode mode,
            TravelPeriod period,
            RoutePreferences preferences,
            RouteGraph.GraphData graph,
            UUID startNodeId) {
        List<RouteGraph.Arc> path = outcome.path();
        double distance = path.stream().mapToDouble(arc -> arc.edge().distanceM()).sum();
        int stairs = path.stream().mapToInt(arc -> arc.edge().stairsCount()).sum();
        Map<String, Integer> slopes = new LinkedHashMap<>();
        for (String level : List.of("FLAT", "GENTLE", "MODERATE", "STEEP", "UNKNOWN")) {
            slopes.put(level, 0);
        }
        path.forEach(arc -> slopes.compute(arc.edge().slopeLevel(), (key, value) -> value == null ? 1 : value + 1));

        int highRisk = (int) path.stream().filter(arc -> "HIGH".equals(arc.edge().riskLevel())).count();
        int mediumRisk = (int) path.stream().filter(arc -> "MEDIUM".equals(arc.edge().riskLevel())).count();
        int unknownRisk = (int) path.stream().filter(arc -> "UNKNOWN".equals(arc.edge().riskLevel())).count();
        String riskLevel = highRisk > 0 ? "HIGH" : mediumRisk > 0 ? "MEDIUM" : unknownRisk > 0 ? "UNKNOWN" : "LOW";

        Map<UUID, RouteGraph.Facility> facilities = new LinkedHashMap<>();
        Map<UUID, RouteGraph.Barrier> barriers = new LinkedHashMap<>();
        path.forEach(arc -> {
            arc.edge().facilities().forEach(facility -> facilities.putIfAbsent(facility.id(), facility));
            arc.edge().barriers().forEach(barrier -> barriers.putIfAbsent(barrier.id(), barrier));
        });
        List<RoutingDtos.RouteFacility> routeFacilities = facilities.values().stream()
                .map(facility -> new RoutingDtos.RouteFacility(
                        facility.id(), facility.name(), facility.type(), facility.openStatus(),
                        facility.confidenceLevel(), facility.point().lng(), facility.point().lat()))
                .toList();
        List<RoutingDtos.RouteBarrier> routeBarriers = barriers.values().stream()
                .map(barrier -> new RoutingDtos.RouteBarrier(
                        barrier.id(), barrier.title(), barrier.type(), barrier.confidenceLevel(),
                        isHardBlockingBarrier(barrier.type())))
                .toList();

        List<String> constraints = constraints(mode, period, preferences);
        List<String> warnings = warnings(path, stairs, outcome.relaxed(), barriers.values());
        String confidence = aggregateConfidence(path, graph, outcome, startNodeId);
        RoutingDtos.CostBreakdown costs = rounded(outcome.costBreakdown());
        RoutingDtos.AlgorithmMetrics metrics = new RoutingDtos.AlgorithmMetrics(
                outcome.metrics().expandedNodes(),
                outcome.metrics().visitedEdges(),
                outcome.metrics().queuePeak(),
                outcome.metrics().elapsedMicros(),
                round(outcome.metrics().totalCost()));
        return new RoutingDtos.RouteResult(
                profile,
                equivalentProfiles,
                geometry(path, graph, startNodeId),
                round(distance),
                estimatedMinutes(distance, stairs, mode),
                new RoutingDtos.RiskSummary(riskLevel, highRisk, mediumRisk, unknownRisk, outcome.relaxed()),
                stairs,
                Map.copyOf(slopes),
                routeFacilities,
                routeBarriers,
                confidence,
                costs,
                constraints,
                warnings,
                metrics,
                path.stream().map(arc -> arc.edge().id()).toList());
    }

    private ObjectNode geometry(List<RouteGraph.Arc> path, RouteGraph.GraphData graph, UUID startNodeId) {
        ObjectNode geometry = objectMapper.createObjectNode();
        geometry.put("type", "LineString");
        ArrayNode coordinates = geometry.putArray("coordinates");
        if (path.isEmpty()) {
            RouteGraph.Point point = graph.nodes().get(startNodeId).point();
            coordinates.addArray().add(point.lng()).add(point.lat());
            coordinates.addArray().add(point.lng()).add(point.lat());
            return geometry;
        }
        RouteGraph.Point previous = null;
        for (RouteGraph.Arc arc : path) {
            for (RouteGraph.Point point : arc.orientedGeometry()) {
                if (previous == null || point.lng() != previous.lng() || point.lat() != previous.lat()) {
                    coordinates.addArray().add(point.lng()).add(point.lat());
                    previous = point;
                }
            }
        }
        return geometry;
    }

    private List<String> constraints(MobilityMode mode, TravelPeriod period, RoutePreferences preferences) {
        List<String> result = new ArrayList<>();
        result.add("行动模式：" + modeLabel(mode));
        result.add("时段：" + (period == TravelPeriod.NIGHT ? "夜间" : "白天"));
        result.add("已排除停用、封闭和动态阻断道路");
        result.add("已遵守单向道路方向");
        if (mode == MobilityMode.WHEELCHAIR) {
            result.add("轮椅模式已强制排除楼梯");
        } else if (preferences.avoidStairs()) {
            result.add("已优先避开楼梯");
        }
        if (preferences.restAreaWeightOrDefault() > 0) {
            result.add("已考虑沿途休息点偏好");
        }
        if (preferences.accessibleToiletWeightOrDefault() > 0) {
            result.add("已考虑无障碍卫生间偏好");
        }
        return List.copyOf(result);
    }

    private List<String> warnings(
            List<RouteGraph.Arc> path,
            int stairs,
            boolean relaxed,
            java.util.Collection<RouteGraph.Barrier> barriers) {
        Set<String> warnings = new LinkedHashSet<>();
        if (relaxed) {
            warnings.add("不存在满足全部偏好的路线，当前为风险最低可达路线，请谨慎通行");
        }
        if (stairs > 0) {
            warnings.add("路线包含楼梯，共 " + stairs + " 级");
        }
        if (path.stream().anyMatch(arc -> "STEEP".equals(arc.edge().slopeLevel()))) {
            warnings.add("路线包含陡坡路段");
        }
        if (path.stream().anyMatch(arc -> "UNKNOWN".equals(arc.edge().slopeLevel())
                || "UNKNOWN".equals(arc.edge().confidenceLevel()))) {
            warnings.add("部分路段数据未核验或坡度未知");
        }
        if (!barriers.isEmpty()) {
            warnings.add("沿途存在 " + barriers.size() + " 项生效障碍信息");
        }
        return List.copyOf(warnings);
    }

    private String aggregateConfidence(
            List<RouteGraph.Arc> path,
            RouteGraph.GraphData graph,
            AStarRouter.SearchOutcome outcome,
            UUID startNodeId) {
        if (path.isEmpty()) {
            RouteGraph.Node start = graph.nodes().get(startNodeId);
            return start == null || !outcome.found() ? "UNKNOWN" : start.confidenceLevel();
        }
        List<String> levels = path.stream().map(arc -> arc.edge().confidenceLevel()).toList();
        if (levels.contains("UNKNOWN")) return "UNKNOWN";
        if (levels.contains("LOW")) return "LOW";
        if (levels.contains("MEDIUM")) return "MEDIUM";
        return "HIGH";
    }

    private long estimatedMinutes(double distance, int stairs, MobilityMode mode) {
        double speed = switch (mode) {
            case WHEELCHAIR -> 1.0;
            case CRUTCH -> 0.8;
            case TEMPORARY_INJURY -> 0.9;
            case CART_LUGGAGE -> 1.1;
            case WALKING -> 1.3;
        };
        if (distance == 0) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil((distance / speed + stairs * 2.0) / 60.0));
    }

    private RoutingDtos.CostBreakdown rounded(RoutingDtos.CostBreakdown cost) {
        return new RoutingDtos.CostBreakdown(
                round(cost.distance()), round(cost.slope()), round(cost.stairs()), round(cost.width()),
                round(cost.surface()), round(cost.lighting()), round(cost.barrier()), round(cost.uncertainty()),
                round(cost.facilityPreference()), round(cost.total()));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void requireActiveNode(RouteGraph.GraphData graph, UUID nodeId, String label) {
        RouteGraph.Node node = graph.nodes().get(nodeId);
        if (node == null || !node.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "节点不存在或已停用");
        }
    }

    private boolean isHardBlockingBarrier(String type) {
        return Set.of("TEMPORARY_CLOSURE", "CONSTRUCTION", "VEHICLE_BLOCKING", "ENTRANCE_CLOSED").contains(type);
    }

    private String profileLabel(RouteProfile profile) {
        return switch (profile) {
            case SHORTEST -> "最短路线";
            case ACCESSIBLE -> "无障碍优先路线";
            case BALANCED -> "综合路线";
        };
    }

    private String modeLabel(MobilityMode mode) {
        return switch (mode) {
            case WHEELCHAIR -> "轮椅";
            case CRUTCH -> "拐杖";
            case TEMPORARY_INJURY -> "临时受伤";
            case CART_LUGGAGE -> "推车或行李";
            case WALKING -> "步行";
        };
    }

    private record CandidateGroup(
            RouteProfile primaryProfile,
            List<RouteProfile> profiles,
            AStarRouter.SearchOutcome outcome) {
    }
}
