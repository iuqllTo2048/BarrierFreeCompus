package cn.barrierfreecampus.mapdata;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class MapDtos {
    private MapDtos() {
    }

    public record DatasetView(
            UUID id,
            String code,
            String name,
            String datasetType,
            String coordinateSystem,
            boolean enabled,
            boolean demo,
            Long seed,
            String description,
            double centerLng,
            double centerLat) {
    }

    public record BuildingView(UUID id, String externalId, String name, String category, boolean active,
                               String dataSource, String confidenceLevel, JsonNode geometry) {
    }

    public record EntranceView(UUID id, UUID buildingId, String externalId, String name, boolean accessible,
                               String entranceType, String status, boolean active, double lng, double lat) {
    }

    public record NodeView(UUID id, String externalId, String name, String nodeType, boolean active,
                           String dataSource, String confidenceLevel, double lng, double lat) {
    }

    public record EdgeView(UUID id, String externalId, String name, UUID fromNodeId, UUID toNodeId,
                           BigDecimal distanceM, String slopeLevel, boolean hasStairs, int stairsCount,
                           String widthLevel, String surfaceType, String lightingLevel, boolean bidirectional,
                           String status, String riskLevel, String dataSource, String confidenceLevel,
                           JsonNode geometry) {
    }

    public record FacilityView(UUID id, UUID buildingId, String externalId, String name, String facilityType,
                               String floorLabel, String openStatus, String description, boolean active,
                               String dataSource, String confidenceLevel, double lng, double lat) {
    }

    public record BarrierView(UUID id, String externalId, String title, String barrierType, String description,
                              String reviewStatus, boolean active, String dataSource, String confidenceLevel,
                              JsonNode geometry) {
    }

    public record MapSnapshot(DatasetView dataset, List<BuildingView> buildings, List<EntranceView> entrances,
                              List<NodeView> nodes, List<EdgeView> edges, List<FacilityView> facilities,
                              List<BarrierView> barriers) {
    }

    public record ImportResult(int nodes, int edges, int facilities) {
    }

    public record Coordinate(
            @DecimalMin("-180") @DecimalMax("180") double lng,
            @DecimalMin("-90") @DecimalMax("90") double lat) {
    }

    public record DatasetStatusRequest(boolean enabled) {
    }

    public record NodeRequest(
            @NotBlank @Size(max = 64) String externalId,
            @Size(max = 128) String name,
            @NotBlank @Pattern(regexp = "INTERSECTION|ENTRANCE|WAYPOINT|FACILITY_CONNECTOR") String nodeType,
            boolean active,
            @NotNull @Valid Coordinate coordinate) {
    }

    public record EdgeRequest(
            @NotBlank @Size(max = 64) String externalId,
            @Size(max = 128) String name,
            @NotNull UUID fromNodeId,
            @NotNull UUID toNodeId,
            @NotNull @Positive BigDecimal distanceM,
            @NotBlank @Pattern(regexp = "FLAT|GENTLE|MODERATE|STEEP|UNKNOWN") String slopeLevel,
            boolean hasStairs,
            @PositiveOrZero int stairsCount,
            @NotBlank @Pattern(regexp = "NARROW|STANDARD|WIDE|UNKNOWN") String widthLevel,
            @NotBlank @Pattern(regexp = "ASPHALT|CONCRETE|BRICK|GRAVEL|DIRT|UNKNOWN") String surfaceType,
            @NotBlank @Pattern(regexp = "NONE|LOW|MEDIUM|HIGH|UNKNOWN") String lightingLevel,
            boolean bidirectional,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE|CLOSED") String status,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|UNKNOWN") String riskLevel,
            @Valid List<Coordinate> intermediatePoints) {
    }

    public record BuildingRequest(
            @NotBlank @Size(max = 64) String externalId,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 32) String category,
            @Size(max = 500) String description,
            boolean active,
            @NotNull @Valid Coordinate center) {
    }

    public record EntranceRequest(
            @NotNull UUID buildingId,
            @NotBlank @Size(max = 64) String externalId,
            @NotBlank @Size(max = 128) String name,
            boolean accessible,
            @NotBlank @Size(max = 32) String entranceType,
            @NotBlank @Pattern(regexp = "OPEN|CLOSED|UNKNOWN") String status,
            boolean active,
            @NotNull @Valid Coordinate coordinate) {
    }

    public record FacilityRequest(
            UUID buildingId,
            @NotBlank @Size(max = 64) String externalId,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Pattern(regexp = "ACCESSIBLE_ENTRANCE|RAMP|ELEVATOR|ACCESSIBLE_TOILET|REST_AREA|ACCESSIBLE_PARKING|DROP_OFF_POINT|TRANSIT_BOARDING_POINT") String facilityType,
            @Size(max = 32) String floorLabel,
            @NotBlank @Pattern(regexp = "OPEN|CLOSED|UNKNOWN") String openStatus,
            @Size(max = 1000) String description,
            boolean active,
            @NotNull @Valid Coordinate coordinate) {
    }

    public record BarrierRequest(
            @NotBlank @Size(max = 64) String externalId,
            @NotBlank @Size(max = 128) String title,
            @NotBlank @Pattern(regexp = "STAIRS|CONSTRUCTION|TEMPORARY_CLOSURE|DAMAGED_SURFACE|NARROW_PATH|VEHICLE_BLOCKING|STEEP_SLOPE|ELEVATOR_OUTAGE|ENTRANCE_CLOSED|WATERLOGGING") String barrierType,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "PENDING|NEEDS_VERIFICATION|APPROVED|REJECTED") String reviewStatus,
            boolean active,
            @NotNull @Valid Coordinate coordinate) {
    }
}
