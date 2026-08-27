package cn.barrierfreecampus.analytics;

import static cn.barrierfreecampus.analytics.AnalyticsDtos.BuildingScore;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.ConfidenceDistribution;
import static cn.barrierfreecampus.analytics.AnalyticsDtos.GovernanceSummary;

import cn.barrierfreecampus.agent.AiGateway;
import cn.barrierfreecampus.agent.AiProperties;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 治理统计门面：查询汇总、AI/规则摘要与 CSV 的统一入口。
 * 具体实现拆分到 AnalyticsQueryService、BuildingScoreService 与 AnalyticsCsvService。
 */
@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final AnalyticsQueryService queryService;
    private final AnalyticsCsvService csvService;
    private final AiGateway gateway;
    private final AiProperties aiProperties;

    public AnalyticsService(AnalyticsQueryService queryService, AnalyticsCsvService csvService,
                            AiGateway gateway, AiProperties aiProperties) {
        this.queryService = queryService;
        this.csvService = csvService;
        this.gateway = gateway;
        this.aiProperties = aiProperties;
    }

    public AnalyticsDtos.AnalyticsOverview overview(AnalyticsFilter filter) {
        return queryService.overview(filter);
    }

    public GovernanceSummary governanceSummary(AnalyticsFilter filter) {
        AnalyticsDtos.AnalyticsOverview overview = queryService.overview(filter);
        String rules = ruleSummary(overview);
        if (!aiProperties.isEnabled()) {
            return new GovernanceSummary(false, false, "RULES", aiProperties.effectiveModelName(), rules);
        }
        try {
            String context = factualContext(overview, filter);
            String aiText = gateway.summarizeGovernance(context);
            if (containsUnknownNumber(aiText, context)) {
                log.warn("治理总结包含未经后端验证的数值，已改用规则摘要");
                return new GovernanceSummary(true, true, "RULES", aiProperties.effectiveModelName(),
                        rules + "\n\n模型输出包含未经验证的数值，已自动改用规则摘要。");
            }
            return new GovernanceSummary(true, false, "MODEL", aiProperties.effectiveModelName(), aiText);
        } catch (RuntimeException exception) {
            log.warn("治理总结模型不可用，返回规则摘要 error={}", safeError(exception));
            return new GovernanceSummary(true, true, "RULES", aiProperties.effectiveModelName(),
                    rules + "\n\n智能治理建议暂时不可用，上述结论来自结构化统计。");
        }
    }

    public byte[] csv(AnalyticsFilter filter) {
        return csvService.csv(filter);
    }

    private String ruleSummary(AnalyticsDtos.AnalyticsOverview overview) {
        BuildingScore lowest = overview.buildingScores().stream().min(Comparator.comparingDouble(BuildingScore::score))
                .orElse(null);
        long unknown = overview.confidenceDistribution().stream().mapToLong(ConfidenceDistribution::unknown).sum();
        String building = lowest == null ? "当前筛选范围没有可评分建筑。"
                : "建筑平均分为 " + overview.summary().averageBuildingScore() + "，当前最低为“"
                + lowest.name() + "”（" + lowest.score() + " 分）。";
        String barrier = overview.summary().effectiveBarriers() == 0 ? "当前筛选范围未发现生效障碍。"
                : "当前有 " + overview.summary().effectiveBarriers() + " 项生效障碍，建议优先处理影响权重为 3 的阻断类障碍。";
        String quality = unknown == 0 ? "当前统计对象中无 UNKNOWN 可信度记录。"
                : "当前有 " + unknown + " 条 UNKNOWN 可信度记录，建议安排数据核验。";
        return building + "\n" + barrier + "\n" + quality;
    }

    private String factualContext(AnalyticsDtos.AnalyticsOverview overview, AnalyticsFilter filter) {
        String lowest = overview.buildingScores().stream().min(Comparator.comparingDouble(BuildingScore::score))
                .map(item -> item.name() + "=" + item.score() + ",原因=" + String.join("/", item.reasons()))
                .orElse("无");
        return "筛选=" + queryService.filterContext(filter) + "\n"
                + "建筑数=" + overview.summary().buildings() + "\n"
                + "建筑平均分=" + overview.summary().averageBuildingScore() + "\n"
                + "最低分建筑=" + lowest + "\n"
                + "设施数=" + overview.summary().facilities() + "\n"
                + "生效障碍数=" + overview.summary().effectiveBarriers() + "\n"
                + "路线样本数=" + overview.summary().routePlans() + "\n"
                + "可信度分布=" + overview.confidenceDistribution();
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(300, message.length()));
    }

    private boolean containsUnknownNumber(String text, String context) {
        Set<String> allowed = new HashSet<>(Set.of("1", "2", "3"));
        Matcher contextNumbers = NUMBER.matcher(context);
        while (contextNumbers.find()) allowed.add(contextNumbers.group());
        Matcher outputNumbers = NUMBER.matcher(text);
        while (outputNumbers.find()) {
            if (!allowed.contains(outputNumbers.group())) return true;
        }
        return false;
    }
}
