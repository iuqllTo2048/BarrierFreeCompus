package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;

/**
 * 复用单段 A* 规划多段行程，并把各段结果合并为前端现有路线结构。
 */
@Service
public class RouteItineraryService {
    private static final int MAX_WAYPOINTS = 3;
    private final RoutingService routingService;
    private final ObjectMapper objectMapper;

    public RouteItineraryService(RoutingService routingService, ObjectMapper objectMapper) {
        this.routingService = routingService;
        this.objectMapper = objectMapper;
    }

    public ItineraryPlan plan(UUID datasetId, List<UUID> orderedNodeIds, MobilityMode mobilityMode,
                              TravelPeriod travelPeriod, RoutePreferences preferences) {
        if (orderedNodeIds == null || orderedNodeIds.size() < 2) {
            throw new IllegalArgumentException("多段路线至少需要起点和终点");
        }
        if (orderedNodeIds.size() > MAX_WAYPOINTS + 2) {
            throw new IllegalArgumentException("途经点最多支持 " + MAX_WAYPOINTS + " 个");
        }
        if (new LinkedHashSet<>(orderedNodeIds).size() != orderedNodeIds.size()) {
            throw new IllegalArgumentException("起点、途经点和终点不能重复");
        }

        List<RoutePlanResponse> segments = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        for (int index = 0; index < orderedNodeIds.size() - 1; index++) {
            RoutePlanResponse segment = routingService.plan(new RoutePlanRequest(
                    datasetId, orderedNodeIds.get(index), orderedNodeIds.get(index + 1), mobilityMode,
                    travelPeriod, preferences));
            segments.add(segment);
            int segmentNumber = index + 1;
            segment.notices().forEach(notice -> notices.add("第 " + segmentNumber + " 段：" + notice));
            if (segment.routes().isEmpty()) {
                notices.add("第 " + (index + 1) + " 段不存在可达路线，无法生成完整途经点行程");
                return new ItineraryPlan(new RoutePlanResponse(
                        datasetId, orderedNodeIds.getFirst(), orderedNodeIds.getLast(), mobilityMode,
                        travelPeriod, List.of(), List.copyOf(notices), null), List.copyOf(segments));
            }
        }

        Map<List<UUID>, RouteResult> uniqueRoutes = new LinkedHashMap<>();
        for (RouteProfile profile : RouteProfile.values()) {
            List<RouteResult> profileSegments = new ArrayList<>();
            for (int index = 0; index < segments.size(); index++) {
                RouteResult selected = routeForProfile(segments.get(index), profile);
                if (selected == null) {
                    notices.add("第 " + (index + 1) + " 段未返回" + profileLabel(profile)
                            + "，未生成该类型的完整行程");
                    profileSegments.clear();
                    break;
                }
                profileSegments.add(selected);
            }
            if (profileSegments.isEmpty()) continue;
            RouteResult merged = merge(profile, profileSegments);
            List<UUID> signature = List.copyOf(merged.edgeIds());
            RouteResult existing = uniqueRoutes.get(signature);
            if (existing == null) {
                uniqueRoutes.put(signature, merged);
            } else {
                List<RouteProfile> equivalents = new ArrayList<>(existing.equivalentProfiles());
                equivalents.add(profile);
                uniqueRoutes.put(signature, withEquivalentProfiles(existing, List.copyOf(equivalents)));
                notices.add(profileLabel(profile) + "与" + profileLabel(existing.profile()) + "结果相同，已合并展示");
            }
        }

        RoutePlanResponse combined = new RoutePlanResponse(
                datasetId, orderedNodeIds.getFirst(), orderedNodeIds.getLast(), mobilityMode, travelPeriod,
                List.copyOf(uniqueRoutes.values()), List.copyOf(notices), null);
        return new ItineraryPlan(combined, List.copyOf(segments));
    }

    private RouteResult routeForProfile(RoutePlanResponse response, RouteProfile profile) {
        return response.routes().stream()
                .filter(route -> route.profile() == profile || route.equivalentProfiles().contains(profile))
                .findFirst()
                .orElse(null);
    }

    private RouteResult merge(RouteProfile profile, List<RouteResult> segments) {
        double distance = 0;
        long minutes = 0;
        int stairs = 0;
        int highRiskEdges = 0;
        int mediumRiskEdges = 0;
        int unknownRiskEdges = 0;
        boolean fallbackRoute = false;
        Map<String, Integer> slopes = new LinkedHashMap<>();
        for (String level : List.of("FLAT", "GENTLE", "MODERATE", "STEEP", "UNKNOWN")) {
            slopes.put(level, 0);
        }
        Map<UUID, RouteFacility> facilities = new LinkedHashMap<>();
        Map<UUID, RouteBarrier> barriers = new LinkedHashMap<>();
        Set<String> constraints = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        List<UUID> edgeIds = new ArrayList<>();
        CostBreakdown costs = CostBreakdown.zero();
        int expandedNodes = 0;
        int visitedEdges = 0;
        int queuePeak = 0;
        long elapsedMicros = 0;
        double totalCost = 0;
        List<String> confidences = new ArrayList<>();

        for (RouteResult segment : segments) {
            distance += segment.distanceM();
            minutes += segment.estimatedMinutes();
            stairs += segment.stairsCount();
            highRiskEdges += segment.riskSummary().highRiskEdges();
            mediumRiskEdges += segment.riskSummary().mediumRiskEdges();
            unknownRiskEdges += segment.riskSummary().unknownRiskEdges();
            fallbackRoute |= segment.riskSummary().fallbackRoute();
            segment.slopeSummary().forEach((level, count) -> slopes.merge(level, count, Integer::sum));
            segment.facilities().forEach(item -> facilities.putIfAbsent(item.id(), item));
            segment.barriers().forEach(item -> barriers.putIfAbsent(item.id(), item));
            constraints.addAll(segment.constraints());
            warnings.addAll(segment.warnings());
            edgeIds.addAll(segment.edgeIds());
            costs = costs.plus(segment.costBreakdown());
            expandedNodes += segment.algorithmMetrics().expandedNodes();
            visitedEdges += segment.algorithmMetrics().visitedEdges();
            queuePeak = Math.max(queuePeak, segment.algorithmMetrics().queuePeak());
            elapsedMicros += segment.algorithmMetrics().elapsedMicros();
            totalCost += segment.algorithmMetrics().totalCost();
            confidences.add(segment.confidence());
        }

        String riskLevel = highRiskEdges > 0 ? "HIGH"
                : mediumRiskEdges > 0 ? "MEDIUM"
                : unknownRiskEdges > 0 ? "UNKNOWN" : "LOW";
        return new RouteResult(
                profile,
                List.of(profile),
                mergeGeometry(segments),
                round(distance),
                minutes,
                new RiskSummary(riskLevel, highRiskEdges, mediumRiskEdges, unknownRiskEdges, fallbackRoute),
                stairs,
                Map.copyOf(slopes),
                List.copyOf(facilities.values()),
                List.copyOf(barriers.values()),
                worstConfidence(confidences),
                costs,
                List.copyOf(constraints),
                List.copyOf(warnings),
                new AlgorithmMetrics(expandedNodes, visitedEdges, queuePeak, elapsedMicros, round(totalCost)),
                List.copyOf(edgeIds));
    }

    private JsonNode mergeGeometry(List<RouteResult> segments) {
        ObjectNode geometry = objectMapper.createObjectNode();
        geometry.put("type", "LineString");
        ArrayNode merged = geometry.putArray("coordinates");
        JsonNode previous = null;
        for (RouteResult segment : segments) {
            for (JsonNode point : segment.geometry().path("coordinates")) {
                if (previous == null || !sameCoordinate(previous, point)) {
                    merged.add(point.deepCopy());
                    previous = point;
                }
            }
        }
        return geometry;
    }

    private boolean sameCoordinate(JsonNode left, JsonNode right) {
        return left.isArray() && right.isArray() && left.size() >= 2 && right.size() >= 2
                && Double.compare(left.get(0).asDouble(), right.get(0).asDouble()) == 0
                && Double.compare(left.get(1).asDouble(), right.get(1).asDouble()) == 0;
    }

    private RouteResult withEquivalentProfiles(RouteResult route, List<RouteProfile> profiles) {
        return new RouteResult(route.profile(), profiles, route.geometry(), route.distanceM(),
                route.estimatedMinutes(), route.riskSummary(), route.stairsCount(), route.slopeSummary(),
                route.facilities(), route.barriers(), route.confidence(), route.costBreakdown(),
                route.constraints(), route.warnings(), route.algorithmMetrics(), route.edgeIds());
    }

    private String worstConfidence(List<String> levels) {
        if (levels.contains("UNKNOWN")) return "UNKNOWN";
        if (levels.contains("LOW")) return "LOW";
        if (levels.contains("MEDIUM")) return "MEDIUM";
        return "HIGH";
    }

    private String profileLabel(RouteProfile profile) {
        return switch (profile) {
            case SHORTEST -> "最短路线";
            case ACCESSIBLE -> "无障碍优先路线";
            case BALANCED -> "综合路线";
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record ItineraryPlan(RoutePlanResponse combined, List<RoutePlanResponse> segments) {
    }
}
