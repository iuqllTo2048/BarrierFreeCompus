package cn.barrierfreecampus.analytics;

import static cn.barrierfreecampus.analytics.AnalyticsDtos.BuildingScore;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 治理统计 CSV 导出。
 */
@Component
public class AnalyticsCsvService {
    private final AnalyticsQueryService queryService;

    public AnalyticsCsvService(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    public byte[] csv(AnalyticsFilter filter) {
        AnalyticsDtos.AnalyticsOverview overview = queryService.overview(filter);
        StringBuilder csv = new StringBuilder("\uFEFF统计区段,维度,指标,数值,单位,筛选上下文\r\n");
        String context = queryService.filterContext(filter);
        for (BuildingScore item : overview.buildingScores()) {
            row(csv, "建筑评分", item.name(), "总分", item.score(), "分", context);
            row(csv, "建筑评分", item.name(), "无障碍入口", item.entranceScore(), "分", context);
            row(csv, "建筑评分", item.name(), "道路可达性", item.roadScore(), "分", context);
        }
        overview.facilityDistribution().forEach(item ->
                row(csv, "设施分布", item.label(), "数量", item.count(), "个", context));
        overview.barrierPoints().forEach(item -> {
            row(csv, "障碍空间", item.title(), "影响权重", item.impactWeight(), "级", context);
            row(csv, "障碍空间", item.title(), "坐标", item.lng() + ";" + item.lat(), "GCJ-02", context);
        });
        overview.barrierTrend().forEach(item -> {
            row(csv, "障碍趋势", item.date().toString(), "新增上报", item.submitted(), "条", context);
            row(csv, "障碍趋势", item.date().toString(), "审核通过", item.approved(), "条", context);
        });
        overview.routeRisks().forEach(item -> {
            row(csv, "路线风险", profileLabel(item.profile()), "样本数", item.sampleCount(), "次", context);
            row(csv, "路线风险", profileLabel(item.profile()), "平均高风险边", item.averageHighRiskEdges(), "条", context);
        });
        overview.confidenceDistribution().forEach(item -> {
            row(csv, "可信度", item.entityLabel(), "HIGH", item.high(), "条", context);
            row(csv, "可信度", item.entityLabel(), "MEDIUM", item.medium(), "条", context);
            row(csv, "可信度", item.entityLabel(), "LOW", item.low(), "条", context);
            row(csv, "可信度", item.entityLabel(), "UNKNOWN", item.unknown(), "条", context);
        });
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void row(StringBuilder target, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) target.append(',');
            target.append(csvCell(String.valueOf(cells[i])));
        }
        target.append("\r\n");
    }

    private String csvCell(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String profileLabel(String key) {
        return switch (key == null ? "" : key.toUpperCase(Locale.ROOT)) {
            case "SHORTEST" -> "最短路线"; case "ACCESSIBLE" -> "无障碍优先";
            case "BALANCED" -> "综合路线"; default -> key;
        };
    }
}
