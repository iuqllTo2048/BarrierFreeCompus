package cn.barrierfreecampus.agent;

import static cn.barrierfreecampus.agent.AgentDtos.BarrierDraftView;
import static cn.barrierfreecampus.agent.AgentDtos.PlaceResult;
import static cn.barrierfreecampus.agent.AgentDtos.RouteComparison;

import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 单次智能体调用的可信上下文。模型不能创建或覆盖这里的数据集、用户和会话身份。
 */
public final class AgentExecutionContext {
    private static final int MAX_TOOL_CALLS = 8;
    private static final ThreadLocal<TurnContext> CURRENT = new ThreadLocal<>();

    private AgentExecutionContext() {
    }

    public static void set(TurnContext context) {
        CURRENT.set(context);
    }

    public static TurnContext require() {
        TurnContext context = CURRENT.get();
        if (context == null) throw new IllegalStateException("智能体执行上下文缺失");
        return context;
    }

    public static String requireUsername() {
        return require().username();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static final class TurnContext {
        private final String username;
        private final UUID datasetId;
        private final UUID conversationId;
        private final UUID invocationId;
        private final BiConsumer<String, Object> eventSink;
        private final Set<String> executedCalls = new LinkedHashSet<>();
        private int toolCalls;
        private RoutePlanResponse routeResult;
        private RouteComparison comparison;
        private BarrierDraftView barrierDraft;
        private PlaceResult startPlace;
        private PlaceResult endPlace;

        public TurnContext(String username, UUID datasetId, UUID conversationId, UUID invocationId,
                           BiConsumer<String, Object> eventSink) {
            this.username = username;
            this.datasetId = datasetId;
            this.conversationId = conversationId;
            this.invocationId = invocationId;
            this.eventSink = eventSink;
        }

        public void claimToolCall(String toolName, String signature) {
            if (!executedCalls.add(toolName + ":" + signature)) {
                throw new IllegalStateException("不得重复调用相同工具和参数：" + toolName);
            }
            toolCalls++;
            if (toolCalls > MAX_TOOL_CALLS) {
                throw new IllegalStateException("单次请求的白名单工具调用次数超过限制：" + toolName);
            }
        }

        public void emit(String event, Object data) {
            eventSink.accept(event, data);
        }

        public String username() { return username; }
        public UUID datasetId() { return datasetId; }
        public UUID conversationId() { return conversationId; }
        public UUID invocationId() { return invocationId; }
        public RoutePlanResponse routeResult() { return routeResult; }
        public RouteComparison comparison() { return comparison; }
        public BarrierDraftView barrierDraft() { return barrierDraft; }
        public PlaceResult startPlace() { return startPlace; }
        public PlaceResult endPlace() { return endPlace; }
        public void routeResult(RoutePlanResponse value) { routeResult = value; }
        public void comparison(RouteComparison value) { comparison = value; }
        public void barrierDraft(BarrierDraftView value) { barrierDraft = value; }
        public void endpoints(PlaceResult start, PlaceResult end) {
            startPlace = start;
            endPlace = end;
        }
    }
}
