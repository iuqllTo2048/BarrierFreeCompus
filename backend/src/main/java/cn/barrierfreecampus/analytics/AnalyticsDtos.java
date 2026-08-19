package cn.barrierfreecampus.analytics;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record FilterView(UUID datasetId, UUID buildingId, LocalDate from, LocalDate to,
                             String facilityType, String barrierType, String confidenceLevel) {}

    public record SummaryView(long buildings, long facilities, long effectiveBarriers,
                              long routePlans, double averageBuildingScore) {}

    public record BuildingScore(UUID id, String name, double score,
                                double entranceScore, double elevatorScore, double toiletScore,
                                double roadScore, double barrierScore, double completenessScore,
                                boolean dataSufficient, List<String> reasons, double lng, double lat) {}

    public record DistributionItem(String key, String label, long count, double percentage) {}

    public record BarrierPoint(UUID id, String title, String barrierType, String confidenceLevel,
                               String reviewStatus, double lng, double lat, int impactWeight) {}

    public record BarrierTrend(LocalDate date, long submitted, long approved) {}

    public record RouteRisk(String profile, long sampleCount, double averageDistanceM,
                            double averageMinutes, double averageHighRiskEdges,
                            double averageWarningCount, long fallbackCount) {}

    public record ConfidenceDistribution(String entityType, String entityLabel,
                                         long high, long medium, long low, long unknown) {}

    public record AnalyticsOverview(FilterView filter, SummaryView summary,
                                    List<BuildingScore> buildingScores,
                                    List<DistributionItem> facilityDistribution,
                                    List<BarrierPoint> barrierPoints,
                                    List<BarrierTrend> barrierTrend,
                                    List<RouteRisk> routeRisks,
                                    List<ConfidenceDistribution> confidenceDistribution,
                                    OffsetDateTime generatedAt) {}

    public record GovernanceSummary(boolean aiEnabled, boolean degraded, String generatedBy,
                                    String modelName, String text) {}
}
