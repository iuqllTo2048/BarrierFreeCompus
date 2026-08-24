package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.BarrierReportView;
import static cn.barrierfreecampus.business.BusinessDtos.BarrierReviewRequest;
import static cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 障碍上报、审核、核验与自动过期。
 */
@Component
public class BarrierGovernanceService {
    private final BusinessSupport support;

    public BarrierGovernanceService(BusinessSupport support) {
        this.support = support;
    }

    @Transactional
    public BarrierReportView submitBarrier(String username, BarrierSubmitRequest request) {
        long userId = support.userId(username);
        support.requireEnabledDataset(request.datasetId());
        int matchHours = support.settingInt("barrier.match.window.hours", 24, 1, 168);
        double matchDegrees = support.settingInt("barrier.match.radius.meters", 50, 5, 500) / 111_000.0;
        Integer duplicate = support.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM barrier_report
                WHERE dataset_id=? AND reporter_id=? AND barrier_type=?
                  AND review_status <> 'REJECTED' AND created_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 hour')
                  AND ST_DWithin(geom,ST_SetSRID(ST_MakePoint(?,?),0),?)
                """,
                Integer.class, request.datasetId(), userId, request.barrierType(), matchHours,
                request.lng(), request.lat(), matchDegrees);
        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你近期已在附近上报同类障碍，请勿重复提交");
        }

        UUID matched = support.jdbc().query(
                        """
                        SELECT COALESCE(matched_report_id,id) canonical_id FROM barrier_report
                        WHERE dataset_id=? AND reporter_id<>? AND reporter_id IS NOT NULL AND barrier_type=?
                          AND review_status IN ('PENDING','NEEDS_VERIFICATION')
                          AND created_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 hour')
                          AND ST_DWithin(geom,ST_SetSRID(ST_MakePoint(?,?),0),?)
                        ORDER BY created_at DESC LIMIT 1
                        """,
                        (rs, row) -> rs.getObject("canonical_id", UUID.class),
                        request.datasetId(), userId, request.barrierType(), matchHours,
                        request.lng(), request.lat(), matchDegrees)
                .stream().findFirst().orElse(null);

        UUID id = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(request.expectedDurationHours());
        String status = matched == null ? "PENDING" : "NEEDS_VERIFICATION";
        String confidence = matched == null ? "LOW" : "MEDIUM";
        support.jdbc().update(
                """
                INSERT INTO barrier_report(id,dataset_id,reporter_id,external_id,title,barrier_type,description,
                  review_status,active,starts_at,ends_at,data_source,confidence_level,geom,matched_report_id)
                VALUES (?,?,?,? ,?,?,?, ?,FALSE,CURRENT_TIMESTAMP,?,'USER_REPORT',?,
                  ST_SetSRID(ST_MakePoint(?,?),0),?)
                """,
                id, request.datasetId(), userId, "USR-" + id.toString().substring(0, 8).toUpperCase(),
                request.title().trim(), request.barrierType(), request.description().trim(), status, expiresAt,
                confidence, request.lng(), request.lat(), matched);
        if (matched != null) {
            support.jdbc().update(
                    """
                    UPDATE barrier_report SET review_status='NEEDS_VERIFICATION',confidence_level='MEDIUM',
                      updated_at=CURRENT_TIMESTAMP
                    WHERE (id=? OR matched_report_id=?) AND review_status IN ('PENDING','NEEDS_VERIFICATION')
                    """,
                    matched, matched);
        }
        support.audit(userId, "BARRIER_REPORT_SUBMIT", "BARRIER_REPORT", id.toString(),
                matched == null ? "single-user-low" : "multi-user-medium");
        return barrierById(id);
    }

    public List<BarrierReportView> myBarriers(String username) {
        return barrierQuery("WHERE b.reporter_id=? ORDER BY b.created_at DESC", support.userId(username));
    }

    public List<BarrierReportView> adminBarriers(String status) {
        if (status == null || status.isBlank() || "ALL".equals(status)) {
            return barrierQuery("ORDER BY CASE b.review_status WHEN 'NEEDS_VERIFICATION' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END,b.created_at DESC");
        }
        return barrierQuery("WHERE b.review_status=? ORDER BY b.created_at DESC", status);
    }

    @Transactional
    public BarrierReportView reviewBarrier(UUID id, String admin, BarrierReviewRequest request) {
        if (request.fieldVerified() && !"APPROVED".equals(request.decision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "实地核验只能与审核通过同时提交");
        }
        long adminId = support.userId(admin);
        BarrierReportView current = barrierById(id);
        boolean approved = "APPROVED".equals(request.decision());
        String confidence = request.fieldVerified()
                ? "HIGH"
                : "MEDIUM".equals(current.confidenceLevel()) ? "MEDIUM" : "LOW";
        support.jdbc().update(
                """
                UPDATE barrier_report SET review_status=?,active=?,confidence_level=?,reviewed_by=?,
                  reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """,
                request.decision(), approved, confidence, adminId, id);
        support.audit(adminId, "BARRIER_REVIEW_" + request.decision(), "BARRIER_REPORT", id.toString(),
                (request.fieldVerified() ? "field-verified; " : "") + support.nullToEmpty(request.note()));
        return barrierById(id);
    }

    @Transactional
    public int expireBarriers() {
        if (!Boolean.parseBoolean(support.settingValue("barrier.scheduler.enabled", "true"))) return 0;
        List<UUID> expired = support.jdbc().query(
                """
                UPDATE barrier_report SET active=FALSE,updated_at=CURRENT_TIMESTAMP
                WHERE active=TRUE AND ends_at IS NOT NULL AND ends_at<=CURRENT_TIMESTAMP RETURNING id
                """,
                (rs, row) -> rs.getObject("id", UUID.class));
        expired.forEach(id -> support.audit(null, "BARRIER_AUTO_EXPIRE", "BARRIER_REPORT", id.toString(), null));
        return expired.size();
    }

    private List<BarrierReportView> barrierQuery(String suffix, Object... args) {
        return support.jdbc().query(
                """
                SELECT b.id,b.dataset_id,b.external_id,b.title,b.barrier_type,b.description,b.review_status,
                  b.active,b.confidence_level,b.matched_report_id,u.username reporter_username,b.ends_at,
                  b.created_at,b.reviewed_at,ST_X(b.geom) lng,ST_Y(b.geom) lat
                FROM barrier_report b LEFT JOIN app_user u ON u.id=b.reporter_id
                """ + suffix,
                (rs, row) -> barrierRow(rs), args);
    }

    private BarrierReportView barrierById(UUID id) {
        return barrierQuery("WHERE b.id=?", id).stream().findFirst()
                .orElseThrow(() -> support.notFound("障碍上报不存在"));
    }

    private BarrierReportView barrierRow(ResultSet rs) throws SQLException {
        return new BarrierReportView(
                rs.getObject("id", UUID.class), rs.getObject("dataset_id", UUID.class),
                rs.getString("external_id"), rs.getString("title"), rs.getString("barrier_type"),
                rs.getString("description"), rs.getString("review_status"), rs.getBoolean("active"),
                rs.getString("confidence_level"), rs.getObject("matched_report_id", UUID.class),
                rs.getString("reporter_username"), rs.getObject("ends_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("reviewed_at", OffsetDateTime.class),
                rs.getDouble("lng"), rs.getDouble("lat"));
    }
}
