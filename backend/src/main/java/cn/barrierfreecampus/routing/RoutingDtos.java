package cn.barrierfreecampus.routing;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoutingDtos {
    private RoutingDtos() {
    }

    public enum MobilityMode {
        WHEELCHAIR,
        CRUTCH,
        TEMPORARY_INJURY,
        CART_LUGGAGE,
        WALKING
    }

    public enum RouteProfile {
        SHORTEST,
        ACCESSIBLE,
        BALANCED
    }

    public enum TravelPeriod {
        DAY,
        NIGHT
    }

    public record RoutePreferences(
            boolean avoidStairs,
            @DecimalMin("0.5") @DecimalMax("2.0") Double distanceWeight,
            @DecimalMin("0.5") @DecimalMax("2.0") Double slopeWeight,
            @DecimalMin("0.5") @DecimalMax("2.0") Double widthWeight,
            @DecimalMin("0.5") @DecimalMax("2.0") Double restAreaWeight,
            @DecimalMin("0.5") @DecimalMax("2.0") Double accessibleToiletWeight) {
        public double distanceWeightOrDefault() {
            return distanceWeight == null ? 1.0 : distanceWeight;
        }

        public double slopeWeightOrDefault() {
            return slopeWeight == null ? 1.0 : slopeWeight;
        }

        public double widthWeightOrDefault() {
            return widthWeight == null ? 1.0 : widthWeight;
        }

        public double restAreaWeightOrDefault() {
            return restAreaWeight == null ? 0.0 : restAreaWeight;
        }

        public double accessibleToiletWeightOrDefault() {
            return accessibleToiletWeight == null ? 0.0 : accessibleToiletWeight;
        }

        public static RoutePreferences defaults() {
            return new RoutePreferences(false, 1.0, 1.0, 1.0, null, null);
        }
    }

    public record RoutePlanRequest(
            @NotNull UUID datasetId,
            @NotNull UUID startNodeId,
            @NotNull UUID endNodeId,
            @NotNull MobilityMode mobilityMode,
            @NotNull TravelPeriod travelPeriod,
            @Valid RoutePreferences preferences) {
        public RoutePreferences effectivePreferences() {
            return preferences == null ? RoutePreferences.defaults() : preferences;
        }
    }

    public record RoutePlanResponse(
            UUID datasetId,
            UUID startNodeId,
            UUID endNodeId,
            MobilityMode mobilityMode,
            TravelPeriod travelPeriod,
            List<RouteResult> routes,
            List<String> notices,
            UUID historyId) {
    }

    public record RouteResult(
            RouteProfile profile,
            List<RouteProfile> equivalentProfiles,
            JsonNode geometry,
            double distanceM,
            long estimatedMinutes,
            RiskSummary riskSummary,
            int stairsCount,
            Map<String, Integer> slopeSummary,
            List<RouteFacility> facilities,
            List<RouteBarrier> barriers,
            String confidence,
            CostBreakdown costBreakdown,
            List<String> constraints,
            List<String> warnings,
            AlgorithmMetrics algorithmMetrics,
            List<UUID> edgeIds) {
    }

    public record RiskSummary(
            String level,
            int highRiskEdges,
            int mediumRiskEdges,
            int unknownRiskEdges,
            boolean fallbackRoute) {
    }

    public record RouteFacility(
            UUID id,
            String name,
            String facilityType,
            String openStatus,
            String confidenceLevel,
            double lng,
            double lat) {
    }

    public record RouteBarrier(
            UUID id,
            String title,
            String barrierType,
            String confidenceLevel,
            boolean blocking) {
    }

    public record CostBreakdown(
            double distance,
            double slope,
            double stairs,
            double width,
            double surface,
            double lighting,
            double barrier,
            double uncertainty,
            double facilityPreference,
            double total) {
        public CostBreakdown plus(CostBreakdown other) {
            return new CostBreakdown(
                    distance + other.distance,
                    slope + other.slope,
                    stairs + other.stairs,
                    width + other.width,
                    surface + other.surface,
                    lighting + other.lighting,
                    barrier + other.barrier,
                    uncertainty + other.uncertainty,
                    facilityPreference + other.facilityPreference,
                    total + other.total);
        }

        public static CostBreakdown zero() {
            return new CostBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record AlgorithmMetrics(
            int expandedNodes,
            int visitedEdges,
            int queuePeak,
            long elapsedMicros,
            double totalCost) {
    }
}
