package cn.barrierfreecampus.agent;

import static cn.barrierfreecampus.agent.AgentDtos.*;

import cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;
import cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePreferences;
import cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod;
import cn.barrierfreecampus.routing.RouteItineraryService;
import cn.barrierfreecampus.routing.RouteItineraryService.ItineraryPlan;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 暴露给模型的最小白名单。身份、数据集和会话均取自后端可信上下文。
 */
@Component
public class ControlledAgentTools {
    private static final Set<String> BARRIER_TYPES = Set.of(
            "STAIRS", "CONSTRUCTION", "TEMPORARY_CLOSURE", "DAMAGED_SURFACE", "NARROW_PATH",
            "VEHICLE_BLOCKING", "STEEP_SLOPE", "ELEVATOR_OUTAGE", "ENTRANCE_CLOSED", "WATERLOGGING");
    private final AgentTools tools;
    private final AgentRepository repository;
    private final RouteItineraryService itineraryService;

    public ControlledAgentTools(AgentTools tools, AgentRepository repository,
                                RouteItineraryService itineraryService) {
        this.tools = tools;
        this.repository = repository;
        this.itineraryService = itineraryService;
    }

    @Tool("搜索并确认一个校园地点。只传地点简称或名称，不要传整句路线需求；结果歧义时必须询问用户")
    public PlaceSearchToolResult searchCampusPlace(
            @P("需要确认的单个地点名称、简称或轻微错别字") String query) {
        String safeQuery = requireText(query, "地点名称", 128);
        return execute("searchCampusPlace", Map.of("query", safeQuery),
                () -> placeSearch(safeQuery), result -> Map.of(
                        "status", result.status(), "candidateCount", result.candidates().size()));
    }

    @Tool("使用起点、有序途经点、终点和行动方式调用后端自建 A*。后端逐段规划并合并完整路线；地点歧义时只返回候选，不计算路线")
    public RouteToolResult calculateAccessibleRoutes(
            @P("起点名称；缺少起点时不要调用，先询问用户") String startPlace,
            @P("终点名称；缺少终点时不要调用，先询问用户") String endPlace,
            @P(value = "按用户要求依次经过的途经点名称，最多 3 个；没有时传空数组", required = false)
            List<String> waypointPlaces,
            @P("行动方式：WHEELCHAIR、CRUTCH、TEMPORARY_INJURY、CART_LUGGAGE 或 WALKING") String mobilityMode,
            @P(value = "用户是否明确偏好最短距离", required = false) Boolean preferShortest,
            @P(value = "用户是否明确偏好较低风险", required = false) Boolean preferLowRisk) {
        String startQuery = requireText(startPlace, "起点", 128);
        String endQuery = requireText(endPlace, "终点", 128);
        List<String> waypointQueries = normalizeWaypoints(waypointPlaces);
        String modeText = requireText(mobilityMode, "行动方式", 32).toUpperCase(Locale.ROOT);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("startPlace", startQuery);
        arguments.put("waypointPlaces", waypointQueries);
        arguments.put("endPlace", endQuery);
        arguments.put("mobilityMode", modeText);
        arguments.put("preferShortest", Boolean.TRUE.equals(preferShortest));
        arguments.put("preferLowRisk", Boolean.TRUE.equals(preferLowRisk));
        return execute("calculateAccessibleRoutes", arguments,
                () -> calculateRoutes(startQuery, waypointQueries, endQuery, modeText,
                        Boolean.TRUE.equals(preferShortest), Boolean.TRUE.equals(preferLowRisk)),
                result -> Map.of("status", result.status(), "routeCount", result.routes().size(),
                        "waypointCount", result.waypointNames().size(),
                        "segmentCount", result.segments().size(),
                        "startCandidates", result.startCandidates().size(),
                        "waypointCandidateGroups", result.waypointCandidates().size(),
                        "endCandidates", result.endCandidates().size()));
    }

    @Tool("从一个已知校园地点查询最近的无障碍设施；地点歧义时只返回候选")
    public NearestFacilityToolResult searchNearestAccessibleFacilities(
            @P("作为距离参考的校园地点名称") String originPlace,
            @P(value = "设施类型；可为空，或使用 ACCESSIBLE_TOILET、RAMP、ELEVATOR、REST_AREA 等枚举", required = false)
            String facilityType) {
        String originQuery = requireText(originPlace, "参考地点", 128);
        String type = facilityType == null ? "" : facilityType.trim().toUpperCase(Locale.ROOT);
        return execute("searchNearestAccessibleFacilities", Map.of("originPlace", originQuery, "facilityType", type),
                () -> nearestFacilities(originQuery, type),
                result -> Map.of("status", result.status(), "count", result.facilities().size(),
                        "candidateCount", result.originCandidates().size()));
    }

    @Tool("查询当前数据集中已经管理员审核、生效且未过期的动态障碍")
    public List<BarrierSummary> searchActiveBarriers() {
        return execute("searchActiveBarriers", Map.of(),
                () -> tools.searchActiveBarriers(AgentExecutionContext.require().datasetId()),
                result -> Map.of("count", result.size()));
    }

    @Tool("只生成障碍上报草稿，不会提交正式上报或让障碍生效；位置歧义时只返回候选")
    public BarrierDraftToolResult createBarrierReportDraft(
            @P("障碍附近的校园地点名称") String place,
            @P("障碍类型枚举") String barrierType,
            @P("用户提供的障碍描述") String description) {
        String placeQuery = requireText(place, "障碍位置", 128);
        String type = requireText(barrierType, "障碍类型", 40).toUpperCase(Locale.ROOT);
        String safeDescription = requireText(description, "障碍描述", 1000);
        if (!BARRIER_TYPES.contains(type)) throw new IllegalArgumentException("不支持的障碍类型：" + barrierType);
        return execute("createBarrierReportDraft",
                Map.of("place", placeQuery, "barrierType", type, "descriptionLength", safeDescription.length()),
                () -> createDraft(placeQuery, type, safeDescription),
                result -> Map.of("status", result.status(), "candidateCount", result.placeCandidates().size(),
                        "draftCreated", result.draft() != null));
    }

    private PlaceSearchToolResult placeSearch(String query) {
        Resolution resolution = resolve(query);
        return switch (resolution.status()) {
            case "FOUND" -> new PlaceSearchToolResult("FOUND", query, resolution.candidates(), "地点已唯一确认");
            case "AMBIGUOUS" -> new PlaceSearchToolResult("AMBIGUOUS", query, resolution.candidates(),
                    "地点存在多个匹配，请展示候选并询问用户");
            default -> new PlaceSearchToolResult("NOT_FOUND", query, List.of(), "当前数据集中未找到该地点");
        };
    }

    private RouteToolResult calculateRoutes(String startQuery, List<String> waypointQueries,
                                            String endQuery, String modeText,
                                            boolean preferShortest, boolean preferLowRisk) {
        Resolution start = resolve(startQuery);
        Resolution end = resolve(endQuery);
        List<Resolution> waypoints = waypointQueries.stream().map(this::resolve).toList();
        Map<String, List<PlaceResult>> waypointCandidates = new LinkedHashMap<>();
        for (int index = 0; index < waypointQueries.size(); index++) {
            Resolution resolution = waypoints.get(index);
            if (!"FOUND".equals(resolution.status())) {
                waypointCandidates.put(waypointQueries.get(index), resolution.candidates());
            }
        }
        if (!"FOUND".equals(start.status()) || !waypointCandidates.isEmpty() || !"FOUND".equals(end.status())) {
            return new RouteToolResult("NEEDS_CLARIFICATION", null, List.of(), null, modeText,
                    List.of(), List.of(), null, List.of(), List.of(),
                    start.candidates(), Map.copyOf(waypointCandidates), end.candidates(),
                    "起点、途经点或终点未唯一确认，必须先询问用户");
        }
        MobilityMode mode;
        try {
            mode = MobilityMode.valueOf(modeText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的行动方式：" + modeText);
        }
        PlaceResult startPlace = start.candidates().getFirst();
        List<PlaceResult> waypointPlaces = waypoints.stream()
                .map(resolution -> resolution.candidates().getFirst()).toList();
        PlaceResult endPlace = end.candidates().getFirst();
        List<PlaceResult> orderedPlaces = new ArrayList<>();
        orderedPlaces.add(startPlace);
        orderedPlaces.addAll(waypointPlaces);
        orderedPlaces.add(endPlace);
        if (orderedPlaces.stream().anyMatch(place -> place.nearestNodeId() == null)) {
            throw new IllegalArgumentException("起点、途经点或终点尚未连接到可用路网");
        }
        boolean avoidStairs = mode != MobilityMode.WALKING;
        double distanceWeight = preferShortest ? 1.5 : 1.0;
        double slopeWeight = preferLowRisk ? 1.4 : 1.0;
        double widthWeight = preferLowRisk ? 1.2 : 1.0;
        RoutePreferences preferences = new RoutePreferences(
                avoidStairs, distanceWeight, slopeWeight, widthWeight, null, null);
        AgentExecutionContext.TurnContext context = AgentExecutionContext.require();
        ItineraryPlan itinerary = itineraryService.plan(context.datasetId(),
                orderedPlaces.stream().map(PlaceResult::nearestNodeId).toList(), mode,
                TravelPeriod.DAY, preferences);
        RoutePlanResponse response = itinerary.combined();
        context.routeResult(response);
        context.endpoints(startPlace, endPlace);
        RouteComparison comparison = response.routes().isEmpty() ? null : tools.compareRoutes(response);
        List<FacilitySummary> facilities = response.routes().isEmpty()
                ? List.of() : tools.searchFacilitiesNearRoute(response);
        context.comparison(comparison);
        List<RouteComparisonItem> summaries = summarizeRoutes(response);
        List<RouteSegmentSummary> segments = new ArrayList<>();
        for (int index = 0; index < itinerary.segments().size(); index++) {
            RoutePlanResponse segment = itinerary.segments().get(index);
            segments.add(new RouteSegmentSummary(index + 1, orderedPlaces.get(index).name(),
                    orderedPlaces.get(index + 1).name(), summarizeRoutes(segment), segment.notices()));
        }
        String message = response.routes().isEmpty() ? "后端 A* 未找到完整可达路线"
                : waypointPlaces.isEmpty() ? "路线已由后端 A* 计算"
                : "已按 " + waypointPlaces.size() + " 个途经点拆分为 "
                + (waypointPlaces.size() + 1) + " 段，并由后端 A* 合并完整路线";
        return new RouteToolResult(response.routes().isEmpty() ? "NO_ROUTE" : "ROUTES_READY",
                startPlace.name(), waypointPlaces.stream().map(PlaceResult::name).toList(),
                endPlace.name(), mode.name(), summaries, List.copyOf(segments), comparison,
                facilities, response.notices(), List.of(), Map.of(), List.of(), message);
    }

    private List<RouteComparisonItem> summarizeRoutes(RoutePlanResponse response) {
        return response.routes().stream().map(route -> new RouteComparisonItem(
                route.profile().name(), route.distanceM(), route.estimatedMinutes(), route.riskSummary().level(),
                route.stairsCount(), route.slopeSummary(), route.riskSummary().fallbackRoute(),
                route.confidence(), route.warnings())).toList();
    }

    private List<String> normalizeWaypoints(List<String> waypointPlaces) {
        if (waypointPlaces == null) return List.of();
        if (waypointPlaces.size() > 3) throw new IllegalArgumentException("途经点最多支持 3 个");
        return waypointPlaces.stream().map(value -> requireText(value, "途经点", 128)).toList();
    }

    private NearestFacilityToolResult nearestFacilities(String originQuery, String facilityType) {
        Resolution origin = resolve(originQuery);
        if (!"FOUND".equals(origin.status())) {
            return new NearestFacilityToolResult("NEEDS_CLARIFICATION", null, origin.candidates(), List.of(),
                    "参考地点未唯一确认，必须先询问用户");
        }
        PlaceResult place = origin.candidates().getFirst();
        List<NearestFacilitySummary> facilities = tools.searchNearestAccessibleFacilities(
                AgentExecutionContext.require().datasetId(), place.lng(), place.lat(), facilityType, 5);
        return new NearestFacilityToolResult("FOUND", place.name(), List.of(), facilities,
                facilities.isEmpty() ? "当前数据未记录符合条件的设施" : "已按直线距离排序");
    }

    private BarrierDraftToolResult createDraft(String placeQuery, String type, String description) {
        Resolution resolution = resolve(placeQuery);
        if (!"FOUND".equals(resolution.status())) {
            return new BarrierDraftToolResult("NEEDS_CLARIFICATION", resolution.candidates(), null,
                    "障碍位置未唯一确认，必须先询问用户");
        }
        PlaceResult place = resolution.candidates().getFirst();
        String title = place.name() + "附近" + barrierLabel(type);
        AgentExecutionContext.TurnContext context = AgentExecutionContext.require();
        BarrierSubmitRequest payload = new BarrierSubmitRequest(context.datasetId(),
                title.substring(0, Math.min(title.length(), 128)), type, description, 24, place.lng(), place.lat());
        BarrierDraftView draft = tools.createBarrierReportDraft(context.conversationId(), payload);
        context.barrierDraft(draft);
        return new BarrierDraftToolResult("DRAFT_CREATED", List.of(), draft,
                "仅生成草稿，用户确认后仍需走正常上报和管理员审核");
    }

    private Resolution resolve(String query) {
        List<PlaceResult> candidates = tools.searchCampusPlace(
                AgentExecutionContext.require().datasetId(), query, 3);
        if (candidates.isEmpty()) return new Resolution("NOT_FOUND", List.of());
        List<PlaceResult> exact = candidates.stream().filter(item ->
                item.name().equalsIgnoreCase(query) || item.externalId().equalsIgnoreCase(query)).toList();
        if (exact.size() == 1) return new Resolution("FOUND", List.of(exact.getFirst()));
        if (candidates.size() == 1) return new Resolution("FOUND", candidates);
        return new Resolution("AMBIGUOUS", candidates.stream().limit(3).toList());
    }

    private <T> T execute(String name, Object arguments, Supplier<T> action, Function<T, Object> summary) {
        AgentExecutionContext.TurnContext context = AgentExecutionContext.require();
        context.claimToolCall(name, String.valueOf(arguments));
        long started = System.nanoTime();
        context.emit("tool_start", Map.of("name", name));
        try {
            T result = action.get();
            Object resultSummary = summary.apply(result);
            repository.logTool(context.invocationId(), name, arguments, resultSummary,
                    elapsedMs(started), true, null);
            context.emit("tool_result", Map.of("name", name, "summary", resultSummary));
            return result;
        } catch (RuntimeException exception) {
            repository.logTool(context.invocationId(), name, arguments, Map.of(),
                    elapsedMs(started), false, safeError(exception));
            throw exception;
        }
    }

    private String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(label + "过长");
        return trimmed;
    }

    private String barrierLabel(String type) {
        return switch (type) {
            case "STAIRS" -> "楼梯障碍";
            case "CONSTRUCTION" -> "施工障碍";
            case "TEMPORARY_CLOSURE" -> "临时封闭";
            case "WATERLOGGING" -> "道路积水";
            case "STEEP_SLOPE" -> "陡坡风险";
            case "NARROW_PATH" -> "道路狭窄";
            case "VEHICLE_BLOCKING" -> "车辆占道";
            case "ELEVATOR_OUTAGE" -> "电梯停运";
            case "ENTRANCE_CLOSED" -> "入口关闭";
            default -> "路面损坏";
        };
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(300, message.length()));
    }

    private record Resolution(String status, List<PlaceResult> candidates) {
    }
}
