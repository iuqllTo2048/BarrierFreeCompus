package cn.barrierfreecampus.agent;

import static cn.barrierfreecampus.agent.AgentDtos.*;

import cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;
import cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanRequest;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePreferences;
import cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentService {
    public static final String DEGRADATION_MESSAGE = "智能服务暂时不可用，基础路线规划仍可使用";
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private final AgentRepository repository;
    private final AgentTools tools;
    private final AiGateway gateway;
    private final AiProperties properties;
    private final AgentSafetyPolicy safetyPolicy;

    public AgentService(AgentRepository repository, AgentTools tools, AiGateway gateway, AiProperties properties,
                        AgentSafetyPolicy safetyPolicy) {
        this.repository = repository;
        this.tools = tools;
        this.gateway = gateway;
        this.properties = properties;
        this.safetyPolicy = safetyPolicy;
    }

    public ConversationView createConversation(String username, String title) {
        String safeTitle = title == null || title.isBlank() ? "新的路线咨询" : title.trim();
        return repository.createConversation(username, safeTitle, properties);
    }

    public List<ConversationView> conversations(String username) {
        return repository.conversations(username);
    }

    public ConversationDetail conversation(String username, UUID id) {
        return new ConversationDetail(repository.conversation(username, id), repository.messages(username, id));
    }

    public AssistantStatus status() {
        return new AssistantStatus(properties.isEnabled(), properties.isEnabled() ? "REAL" : "MOCK",
                properties.provider(), properties.effectiveModelName(), DEGRADATION_MESSAGE);
    }

    public SseEmitter stream(String username, UUID conversationId, SendMessageRequest request) {
        repository.conversation(username, conversationId);
        UUID requestId = UUID.randomUUID();
        UUID messageId = repository.addMessage(conversationId, "USER", request.content().trim(), requestId);
        UUID invocationId = repository.startInvocation(username, conversationId, messageId, requestId, properties);
        SseEmitter emitter = new SseEmitter(60_000L);
        CompletableFuture.runAsync(() -> execute(username, conversationId, request, requestId, invocationId, emitter));
        return emitter;
    }

    public List<InvocationView> invocations() {
        return repository.invocations();
    }

    public void confirmDraft(UUID id, String username) {
        repository.confirmDraft(id, username);
    }

    private void execute(String username, UUID conversationId, SendMessageRequest request, UUID requestId,
                         UUID invocationId, SseEmitter emitter) {
        long started = System.nanoTime();
        AgentExecutionContext.setUsername(username);
        try {
            emit(emitter, "request", Map.of("requestId", requestId, "mode", properties.isEnabled() ? "REAL" : "MOCK"));
            emit(emitter, "status", Map.of("text", "正在理解需求并核对校园数据"));
            AgentResult result = respond(conversationId, request, invocationId, emitter);
            Exception providerError = null;
            String finalText = result.text();
            if (properties.isEnabled()) {
                try {
                    finalText = gateway.explain(result.text());
                } catch (Exception exception) {
                    providerError = exception;
                    log.warn("外部智能模型不可用，保留白名单工具结果 requestId={} error={}",
                            requestId, safeError(exception));
                    finalText = result.text() + "\n\n" + DEGRADATION_MESSAGE;
                    emit(emitter, "status", Map.of("text", DEGRADATION_MESSAGE, "degraded", true));
                }
            }
            emitText(emitter, finalText);
            if (result.routeResult() != null) emit(emitter, "route_result", result.routeResult());
            if (result.comparison() != null) emit(emitter, "comparison", result.comparison());
            if (result.barrierDraft() != null) emit(emitter, "barrier_draft", result.barrierDraft());
            repository.addMessage(conversationId, "ASSISTANT", finalText, requestId);
            repository.finishInvocation(invocationId, elapsedMs(started), providerError == null,
                    providerError == null ? null : "PROVIDER_DEGRADED", safeError(providerError));
            emit(emitter, "done", Map.of("requestId", requestId, "degraded", providerError != null));
            emitter.complete();
        } catch (Exception exception) {
            log.warn("智能助手请求失败 requestId={}", requestId, exception);
            repository.finishInvocation(invocationId, elapsedMs(started), false,
                    exception.getClass().getSimpleName(), safeError(exception));
            try {
                repository.addMessage(conversationId, "ASSISTANT", DEGRADATION_MESSAGE, requestId);
                emit(emitter, "error", Map.of("requestId", requestId, "message", DEGRADATION_MESSAGE));
                emitter.complete();
            } catch (Exception sendException) {
                emitter.completeWithError(sendException);
            }
        } finally {
            AgentExecutionContext.clear();
        }
    }

    private AgentResult respond(UUID conversationId, SendMessageRequest request,
                                UUID invocationId, SseEmitter emitter) {
        String content = request.content().trim();
        if (safetyPolicy.isDisallowed(content)) {
            return new AgentResult("该请求涉及越权、系统命令、数据库或敏感配置，智能助手不会执行。你仍可以进行校园路线规划、设施查询或创建障碍上报草稿。",
                    null, null, null);
        }
        if (content.contains("上报") || content.contains("报告障碍")) {
            return createDraft(conversationId, request, invocationId, emitter);
        }
        if (isRouteRequest(content)) {
            return planRoute(request, invocationId, emitter);
        }
        if (content.contains("障碍") || content.contains("封路") || content.contains("施工")) {
            List<BarrierSummary> barriers = tool(invocationId, emitter, "searchActiveBarriers",
                    Map.of("datasetId", request.datasetId()),
                    () -> tools.searchActiveBarriers(request.datasetId()), value -> Map.of("count", value.size()));
            String text = barriers.isEmpty() ? "当前数据集中没有已审核且正在生效的动态障碍。"
                    : "当前有 " + barriers.size() + " 项已审核且正在生效的障碍；其中 "
                    + barriers.stream().filter(BarrierSummary::blocking).count() + " 项可能阻断通行。";
            return new AgentResult(text, null, null, null);
        }
        return new AgentResult("我可以根据校园真实路网规划无障碍路线、比较路线风险、查询沿途设施与生效障碍，也可以先生成障碍上报草稿。请告诉我起点、终点和行动方式。",
                null, null, null);
    }

    private AgentResult planRoute(SendMessageRequest request, UUID invocationId, SseEmitter emitter) {
        List<PlaceResult> places = tool(invocationId, emitter, "searchCampusPlace",
                Map.of("datasetId", request.datasetId(), "queryLength", request.content().length(), "limit", 8),
                () -> tools.searchCampusPlace(request.datasetId(), request.content(), 8),
                value -> Map.of("count", value.size(), "kinds", value.stream().map(PlaceResult::kind).distinct().toList()));
        List<PlaceResult> ordered = distinctNodesByMention(places, request.content());
        if (ordered.size() < 2) {
            String found = ordered.isEmpty() ? "尚未识别到明确地点" : "已识别“" + ordered.getFirst().name() + "”";
            return new AgentResult(found + "。请再提供" + (ordered.isEmpty() ? "起点和终点" : "另一个端点") + "，例如“从图书馆到体育馆”。",
                    null, null, null);
        }
        MobilityMode mode = request.mobilityMode() == null ? MobilityMode.WALKING : request.mobilityMode();
        RoutePreferences preferences = new RoutePreferences(mode == MobilityMode.WHEELCHAIR,
                1.0, 1.0, 1.0, null, null);
        RoutePlanRequest routeRequest = new RoutePlanRequest(request.datasetId(), ordered.get(0).nearestNodeId(),
                ordered.get(1).nearestNodeId(), mode, TravelPeriod.DAY, preferences);
        RoutePlanResponse routes = tool(invocationId, emitter, "calculateAccessibleRoutes",
                Map.of("datasetId", request.datasetId(), "start", ordered.get(0).name(),
                        "end", ordered.get(1).name(), "mobilityMode", mode.name()),
                () -> tools.calculateAccessibleRoutes(routeRequest),
                value -> Map.of("routeCount", value.routes().size(), "noticeCount", value.notices().size()));
        List<FacilitySummary> facilities = tool(invocationId, emitter, "searchFacilitiesNearRoute",
                Map.of("routeCount", routes.routes().size()), () -> tools.searchFacilitiesNearRoute(routes),
                value -> Map.of("count", value.size()));
        List<BarrierSummary> barriers = tool(invocationId, emitter, "searchActiveBarriers",
                Map.of("datasetId", request.datasetId()), () -> tools.searchActiveBarriers(request.datasetId()),
                value -> Map.of("count", value.size()));
        RouteComparison comparison = tool(invocationId, emitter, "compareRoutes",
                Map.of("routeCount", routes.routes().size()), () -> tools.compareRoutes(routes),
                value -> Map.of("recommendedProfile", value.recommendedProfile() == null ? "NONE" : value.recommendedProfile()));
        String text;
        if (routes.routes().isEmpty()) {
            text = "从“" + ordered.get(0).name() + "”到“" + ordered.get(1).name()
                    + "”暂未找到可达路线。基础路网返回的提示为：" + String.join("；", routes.notices());
        } else {
            text = "已按“" + ordered.get(0).name() + " → " + ordered.get(1).name() + "”和“"
                    + mobilityLabel(mode) + "”计算真实校园路网。综合比较推荐 "
                    + profileLabel(comparison.recommendedProfile()) + "；共找到 " + routes.routes().size()
                    + " 条不重复候选路线、" + facilities.size() + " 项沿途设施。当前数据集另有 "
                    + barriers.size() + " 项生效障碍，具体风险以路线卡片和地图标注为准。";
        }
        return new AgentResult(text, routes, comparison, null);
    }

    private AgentResult createDraft(UUID conversationId, SendMessageRequest request,
                                    UUID invocationId, SseEmitter emitter) {
        List<PlaceResult> places = tool(invocationId, emitter, "searchCampusPlace",
                Map.of("datasetId", request.datasetId(), "queryLength", request.content().length(), "limit", 4),
                () -> tools.searchCampusPlace(request.datasetId(), request.content(), 4),
                value -> Map.of("count", value.size()));
        if (places.isEmpty()) {
            return new AgentResult("我可以先生成草稿，但还缺少障碍位置。请补充附近的建筑、入口或道路节点名称。",
                    null, null, null);
        }
        PlaceResult place = places.getFirst();
        String type = inferBarrierType(request.content());
        String title = (place.name() + "附近" + barrierLabel(type));
        BarrierSubmitRequest payload = new BarrierSubmitRequest(request.datasetId(),
                title.substring(0, Math.min(128, title.length())), type,
                request.content().substring(0, Math.min(1000, request.content().length())),
                24, place.lng(), place.lat());
        BarrierDraftView draft = tool(invocationId, emitter, "createBarrierReportDraft",
                Map.of("datasetId", request.datasetId(), "barrierType", type, "place", place.name()),
                () -> tools.createBarrierReportDraft(conversationId, payload),
                value -> Map.of("draftId", value.id(), "status", value.status()));
        return new AgentResult("已根据“" + place.name() + "”生成“" + barrierLabel(type)
                + "”上报草稿。它尚未生效，请核对位置、类型和描述后确认提交；确认后仍会进入正常审核流程。",
                null, null, draft);
    }

    private <T> T tool(UUID invocationId, SseEmitter emitter, String name, Object arguments,
                       Supplier<T> action, java.util.function.Function<T, Object> summary) {
        long started = System.nanoTime();
        emit(emitter, "tool_start", Map.of("name", name));
        try {
            T result = action.get();
            Object resultSummary = summary.apply(result);
            repository.logTool(invocationId, name, arguments, resultSummary, elapsedMs(started), true, null);
            emit(emitter, "tool_result", Map.of("name", name, "summary", resultSummary));
            return result;
        } catch (RuntimeException exception) {
            repository.logTool(invocationId, name, arguments, Map.of(), elapsedMs(started), false, safeError(exception));
            throw exception;
        }
    }

    private List<PlaceResult> distinctNodesByMention(List<PlaceResult> places, String content) {
        List<PlaceResult> sorted = new ArrayList<>(places);
        String lower = content.toLowerCase(Locale.ROOT);
        sorted.sort(Comparator.comparingInt(place -> {
            int index = lower.indexOf(place.name().toLowerCase(Locale.ROOT));
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        Map<UUID, PlaceResult> unique = new LinkedHashMap<>();
        sorted.stream().filter(place -> place.nearestNodeId() != null)
                .forEach(place -> unique.putIfAbsent(place.nearestNodeId(), place));
        return List.copyOf(unique.values());
    }

    private boolean isRouteRequest(String content) {
        return content.contains("路线") || content.contains("怎么走") || content.contains("如何去")
                || content.contains("从") && (content.contains("到") || content.contains("去"));
    }

    private String inferBarrierType(String content) {
        if (content.contains("楼梯")) return "STAIRS";
        if (content.contains("施工")) return "CONSTRUCTION";
        if (content.contains("封路") || content.contains("封闭")) return "TEMPORARY_CLOSURE";
        if (content.contains("积水")) return "WATERLOGGING";
        if (content.contains("陡坡") || content.contains("坡太陡")) return "STEEP_SLOPE";
        if (content.contains("狭窄")) return "NARROW_PATH";
        if (content.contains("车") || content.contains("占道")) return "VEHICLE_BLOCKING";
        if (content.contains("电梯")) return "ELEVATOR_OUTAGE";
        if (content.contains("入口")) return "ENTRANCE_CLOSED";
        return "DAMAGED_SURFACE";
    }

    private String barrierLabel(String type) {
        return switch (type) {
            case "STAIRS" -> "楼梯障碍"; case "CONSTRUCTION" -> "施工障碍";
            case "TEMPORARY_CLOSURE" -> "临时封闭"; case "WATERLOGGING" -> "道路积水";
            case "STEEP_SLOPE" -> "陡坡风险"; case "NARROW_PATH" -> "道路狭窄";
            case "VEHICLE_BLOCKING" -> "车辆占道"; case "ELEVATOR_OUTAGE" -> "电梯停运";
            case "ENTRANCE_CLOSED" -> "入口关闭"; default -> "路面损坏";
        };
    }

    private String mobilityLabel(MobilityMode mode) {
        return switch (mode) {
            case WHEELCHAIR -> "轮椅"; case CRUTCH -> "拐杖"; case TEMPORARY_INJURY -> "临时受伤";
            case CART_LUGGAGE -> "推车或行李"; case WALKING -> "步行";
        };
    }

    private String profileLabel(String profile) {
        if (profile == null) return "暂无推荐";
        return switch (profile) { case "SHORTEST" -> "最短路线"; case "ACCESSIBLE" -> "无障碍优先路线";
            case "BALANCED" -> "综合路线"; default -> profile; };
    }

    private void emitText(SseEmitter emitter, String text) {
        int chunk = 36;
        for (int index = 0; index < text.length(); index += chunk) {
            emit(emitter, "delta", Map.of("text", text.substring(index, Math.min(text.length(), index + chunk))));
        }
    }

    private void emit(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE 连接已断开", exception);
        }
    }

    private long elapsedMs(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }
    private String safeError(Exception exception) {
        if (exception == null) return null;
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(300, message.length()));
    }
}
