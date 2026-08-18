package cn.barrierfreecampus.agent;

import cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;
import cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AgentDtos {
    private AgentDtos() {}

    public record ConversationView(UUID id, String title, String status, String provider,
                                   String modelName, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record MessageView(UUID id, String role, String content, UUID requestId, OffsetDateTime createdAt) {}
    public record ConversationDetail(ConversationView conversation, List<MessageView> messages) {}
    public record CreateConversationRequest(@Size(max = 100) String title) {}
    public record AssistantStatus(boolean enabled, String mode, String provider, String modelName,
                                  String degradationMessage) {}
    public record SendMessageRequest(@NotNull UUID datasetId,
                                     @NotBlank @Size(max = 2000) String content,
                                     MobilityMode mobilityMode) {}
    public record PlaceResult(UUID id, String kind, String name, String externalId,
                              UUID nearestNodeId, double lng, double lat, String confidenceLevel) {}
    public record FacilitySummary(UUID id, String name, String facilityType, String openStatus,
                                  String confidenceLevel, double lng, double lat) {}
    public record BarrierSummary(UUID id, String title, String barrierType,
                                 String confidenceLevel, boolean blocking) {}
    public record RouteComparison(String recommendedProfile, List<RouteComparisonItem> routes,
                                  List<String> reasons) {}
    public record RouteComparisonItem(String profile, double distanceM, long estimatedMinutes,
                                      String riskLevel, int stairsCount, int warningCount) {}
    public record BarrierDraftView(UUID id, BarrierSubmitRequest payload, String status,
                                   OffsetDateTime expiresAt) {}
    public record AgentResult(String text, RoutePlanResponse routeResult,
                              RouteComparison comparison, BarrierDraftView barrierDraft) {}
    public record InvocationView(UUID id, UUID conversationId, UUID requestId, String username,
                                 String provider, String modelName, long latencyMs, boolean success,
                                 String errorCode, String errorSummary, List<ToolCallView> tools,
                                 OffsetDateTime createdAt) {}
    public record ToolCallView(String toolName, String argumentSummary, String resultSummary,
                               long latencyMs, boolean success, String errorSummary) {}
}
