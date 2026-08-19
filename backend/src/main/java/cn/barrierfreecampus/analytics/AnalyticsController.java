package cn.barrierfreecampus.analytics;

import cn.barrierfreecampus.common.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<AnalyticsDtos.AnalyticsOverview> overview(
            @RequestParam UUID datasetId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String barrierType,
            @RequestParam(required = false) String confidenceLevel) {
        return ApiResponse.ok(service.overview(filter(datasetId, buildingId, from, to,
                facilityType, barrierType, confidenceLevel)));
    }

    @PostMapping("/ai-summary")
    public ApiResponse<AnalyticsDtos.GovernanceSummary> aiSummary(
            @RequestParam UUID datasetId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String barrierType,
            @RequestParam(required = false) String confidenceLevel) {
        return ApiResponse.ok(service.governanceSummary(filter(datasetId, buildingId, from, to,
                facilityType, barrierType, confidenceLevel)));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> csv(
            @RequestParam UUID datasetId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String barrierType,
            @RequestParam(required = false) String confidenceLevel) {
        byte[] content = service.csv(filter(datasetId, buildingId, from, to,
                facilityType, barrierType, confidenceLevel));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("barrier-free-analytics.csv", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }

    private AnalyticsFilter filter(UUID datasetId, UUID buildingId, LocalDate from, LocalDate to,
                                   String facilityType, String barrierType, String confidenceLevel) {
        return new AnalyticsFilter(datasetId, buildingId, from, to,
                facilityType, barrierType, confidenceLevel);
    }
}
