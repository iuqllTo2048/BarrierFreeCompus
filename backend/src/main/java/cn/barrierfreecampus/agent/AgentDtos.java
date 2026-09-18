package cn.barrierfreecampus.agent;

import cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;
import cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import cn.barrierfreecampus.routing.RoutingDtos.RoutePlanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    public record NearestFacilitySummary(UUID id, String name, String facilityType, String openStatus,
                                         String confidenceLevel, double distanceM, double lng, double lat) {}
    public record BarrierSummary(UUID id, String title, String barrierType,
                                 String confidenceLevel, boolean blocking) {}
    public record PlaceSearchToolResult(String status, String query, List<PlaceResult> candidates,
                                        String message) {}
    public record RouteToolResult(String status, String startName, List<String> waypointNames,
                                  String endName, String mobilityMode,
                                  List<RouteComparisonItem> routes, List<RouteSegmentSummary> segments,
                                  RouteComparison comparison,
                                  List<FacilitySummary> facilities, List<String> notices,
                                  List<PlaceResult> startCandidates,
                                  Map<String, List<PlaceResult>> waypointCandidates,
                                  List<PlaceResult> endCandidates,
                                  String message) {}
    public record RouteSegmentSummary(int index, String startName, String endName,
                                      List<RouteComparisonItem> routes, List<String> notices) {}
    /** 仅供地图展示；模型仍只接收上方的分段摘要。 */
    public record RouteDisplaySegment(String profile, int index, String startName, String endName,
                                      JsonNode geometry, double distanceM, long estimatedMinutes,
                                      String riskLevel) {}
    public record NearestFacilityToolResult(String status, String originName,
                                            List<PlaceResult> originCandidates,
                                            List<NearestFacilitySummary> facilities, String message) {}
    public record BarrierDraftToolResult(String status, List<PlaceResult> placeCandidates,
                                         BarrierDraftView draft, String message) {}
    public record RouteComparison(String recommendedProfile, List<RouteComparisonItem> routes,
                                  List<String> reasons) {}
    public record RouteComparisonItem(String profile, double distanceM, long estimatedMinutes,
                                      String riskLevel, int stairsCount, Map<String, Integer> slopeSummary,
                                      boolean fallbackRoute, String confidence, List<String> warnings) {}
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
