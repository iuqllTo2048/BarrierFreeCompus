package cn.barrierfreecampus.analytics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public record AnalyticsFilter(UUID datasetId, UUID buildingId, LocalDate from, LocalDate to,
                              String facilityType, String barrierType, String confidenceLevel) {
    private static final ZoneId PROJECT_ZONE = ZoneId.of("Asia/Shanghai");

    public AnalyticsFilter {
        LocalDate today = LocalDate.now(PROJECT_ZONE);
        if (to == null) to = today;
        if (from == null) from = to.minusDays(29);
        if (from.isAfter(to)) throw new ResponseStatusException(BAD_REQUEST, "开始日期不能晚于结束日期");
        if (from.plusYears(1).isBefore(to)) throw new ResponseStatusException(BAD_REQUEST, "单次统计时间范围不能超过 1 年");
        facilityType = blankToNull(facilityType);
        barrierType = blankToNull(barrierType);
        confidenceLevel = blankToNull(confidenceLevel);
    }

    public AnalyticsDtos.FilterView view() {
        return new AnalyticsDtos.FilterView(datasetId, buildingId, from, to,
                facilityType, barrierType, confidenceLevel);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
