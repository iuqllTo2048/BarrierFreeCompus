package cn.barrierfreecampus.business;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BusinessDtos {
    private BusinessDtos() {
    }

    public record ProfileView(
            String username,
            String displayName,
            String defaultMobilityMode,
            boolean avoidStairs,
            double distanceWeight,
            double slopeWeight,
            double widthWeight,
            boolean preferRestArea,
            boolean preferAccessibleToilet) {
    }

    public record ProfileUpdateRequest(
            @Size(max = 64) String displayName,
            @NotBlank @Pattern(regexp = "WHEELCHAIR|CRUTCH|TEMPORARY_INJURY|CART_LUGGAGE|WALKING")
            String defaultMobilityMode,
            boolean avoidStairs,
            @DecimalMin("0.5") @DecimalMax("2.0") double distanceWeight,
            @DecimalMin("0.5") @DecimalMax("2.0") double slopeWeight,
            @DecimalMin("0.5") @DecimalMax("2.0") double widthWeight,
            boolean preferRestArea,
            boolean preferAccessibleToilet) {
    }

    public record FacilityDetail(
            UUID id,
            String name,
            String facilityType,
            String buildingName,
            String floorLabel,
            String openStatus,
            String description,
            String dataSource,
            String confidenceLevel,
            String photoUrl,
            OffsetDateTime updatedAt,
            double lng,
            double lat,
            double averageRating,
            int ratingCount,
            Integer myRating,
            List<FacilityCommentView> comments) {
    }

    public record FacilityCommentView(long id, String username, String content, OffsetDateTime createdAt) {
    }

    public record RatingRequest(@Min(1) @Max(5) int rating) {
    }

    public record CommentRequest(@NotBlank @Size(max = 1000) String content) {
    }

    public record SuggestionRequest(
            @NotBlank @Size(max = 32) String suggestionType,
            @NotBlank @Size(max = 1000) String content) {
    }

    public record FacilitySuggestionView(
            UUID id,
            UUID facilityId,
            String facilityName,
            String username,
            String suggestionType,
            String content,
            String status,
            OffsetDateTime createdAt) {
    }

    public record SuggestionReviewRequest(
            @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String status) {
    }

    public record BarrierSubmitRequest(
            @NotNull UUID datasetId,
            @NotBlank @Size(max = 128) String title,
            @NotBlank @Pattern(regexp = "STAIRS|CONSTRUCTION|TEMPORARY_CLOSURE|DAMAGED_SURFACE|NARROW_PATH|VEHICLE_BLOCKING|STEEP_SLOPE|ELEVATOR_OUTAGE|ENTRANCE_CLOSED|WATERLOGGING")
            String barrierType,
            @NotBlank @Size(max = 1000) String description,
            @Min(1) @Max(4320) int expectedDurationHours,
            @DecimalMin("-180") @DecimalMax("180") double lng,
            @DecimalMin("-90") @DecimalMax("90") double lat) {
    }

    public record BarrierReportView(
            UUID id,
            UUID datasetId,
            String externalId,
            String title,
            String barrierType,
            String description,
            String reviewStatus,
            boolean active,
            String confidenceLevel,
            UUID matchedReportId,
            String reporterUsername,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt,
            OffsetDateTime reviewedAt,
            double lng,
            double lat) {
    }

    public record BarrierReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED|NEEDS_VERIFICATION") String decision,
            boolean fieldVerified,
            @Size(max = 500) String note) {
    }

    public record RouteHistoryView(
            UUID id,
            UUID datasetId,
            UUID startNodeId,
            UUID endNodeId,
            String startName,
            String endName,
            String mobilityMode,
            String travelPeriod,
            JsonNode result,
            OffsetDateTime createdAt) {
    }

    public record FavoriteRequest(
            @NotBlank @Pattern(regexp = "SHORTEST|ACCESSIBLE|BALANCED") String routeProfile,
            @NotBlank @Size(max = 100) String name) {
    }

    public record FavoriteView(
            UUID id,
            UUID historyId,
            String routeProfile,
            String name,
            JsonNode routeResult,
            OffsetDateTime createdAt) {
    }

    public record AdminOverview(Map<String, Long> counts, List<BarrierReportView> pendingBarriers) {
    }

    public record AdminUserView(long id, String username, String role, boolean enabled, OffsetDateTime createdAt) {
    }

    public record UserStatusRequest(boolean enabled) {
    }

    public record AuditView(
            long id,
            String actor,
            String action,
            String targetType,
            String targetId,
            String detail,
            OffsetDateTime createdAt) {
    }

    public record SettingView(String key, String value, String description, OffsetDateTime updatedAt) {
    }

    public record SettingUpdateRequest(@NotBlank @Size(max = 1000) String value) {
    }
}
