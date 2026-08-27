package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.AdminOverview;
import static cn.barrierfreecampus.business.BusinessDtos.AdminUserView;
import static cn.barrierfreecampus.business.BusinessDtos.AuditView;
import static cn.barrierfreecampus.business.BusinessDtos.FacilitySuggestionView;
import static cn.barrierfreecampus.business.BusinessDtos.SettingView;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理端治理：总览、建议、用户、审计、设置、地图对象启停与 Demo 重置。
 */
@Component
public class AdminGovernanceService {
    private final BusinessSupport support;
    private final BarrierGovernanceService barrierService;

    public AdminGovernanceService(BusinessSupport support, BarrierGovernanceService barrierService) {
        this.support = support;
        this.barrierService = barrierService;
    }

    public AdminOverview overview() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("users", support.count("SELECT COUNT(*) FROM app_user"));
        counts.put("activeBarriers", support.count("SELECT COUNT(*) FROM barrier_report WHERE active=TRUE AND (ends_at IS NULL OR ends_at>CURRENT_TIMESTAMP)"));
        counts.put("pendingBarriers", support.count("SELECT COUNT(*) FROM barrier_report WHERE review_status IN ('PENDING','NEEDS_VERIFICATION')"));
        counts.put("facilities", support.count("SELECT COUNT(*) FROM accessible_facility WHERE active=TRUE"));
        counts.put("routePlans", support.count("SELECT COUNT(*) FROM route_history"));
        counts.put("suggestions", support.count("SELECT COUNT(*) FROM facility_suggestion WHERE status='PENDING'"));
        return new AdminOverview(Map.copyOf(counts), barrierService.adminBarriers("ALL").stream().limit(8).toList());
    }

    public List<FacilitySuggestionView> suggestions() {
        return support.jdbc().query(
                """
                SELECT s.id,s.facility_id,f.name facility_name,u.username,s.suggestion_type,
                  s.content,s.status,s.created_at
                FROM facility_suggestion s
                LEFT JOIN accessible_facility f ON f.id=s.facility_id
                LEFT JOIN app_user u ON u.id=s.user_id
                ORDER BY CASE s.status WHEN 'PENDING' THEN 0 ELSE 1 END,s.created_at DESC
                """,
                (rs, row) -> new FacilitySuggestionView(
                        rs.getObject("id", UUID.class), rs.getObject("facility_id", UUID.class),
                        rs.getString("facility_name"), rs.getString("username"),
                        rs.getString("suggestion_type"), rs.getString("content"), rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional
    public void reviewSuggestion(UUID id, String status, String admin) {
        if (support.jdbc().update(
                "UPDATE facility_suggestion SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id) == 0) {
            throw support.notFound("设施建议不存在");
        }
        support.audit(support.userId(admin), "FACILITY_SUGGESTION_" + status, "FACILITY_SUGGESTION", id.toString(), null);
    }

    public List<AdminUserView> users() {
        return support.jdbc().query(
                "SELECT id,username,role,enabled,created_at FROM app_user ORDER BY created_at,id",
                (rs, row) -> new AdminUserView(
                        rs.getLong("id"), rs.getString("username"), rs.getString("role"),
                        rs.getBoolean("enabled"), rs.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional
    public void setUserEnabled(long id, boolean enabled, String admin) {
        long adminId = support.userId(admin);
        if (id == adminId && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能禁用当前登录管理员");
        }
        int changed = support.jdbc().update("UPDATE app_user SET enabled=? WHERE id=?", enabled, id);
        if (changed == 0) throw support.notFound("用户不存在");
        if (!enabled) {
            support.jdbc().update(
                    "UPDATE refresh_token SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", id);
        }
        support.audit(adminId, enabled ? "USER_ENABLE" : "USER_DISABLE", "USER", Long.toString(id), null);
    }

    public List<AuditView> audits() {
        return support.jdbc().query(
                """
                SELECT a.id,u.username actor,a.action,a.target_type,a.target_id,a.detail,a.created_at
                FROM audit_log a LEFT JOIN app_user u ON u.id=a.actor_id ORDER BY a.created_at DESC LIMIT 200
                """,
                (rs, row) -> new AuditView(
                        rs.getLong("id"), rs.getString("actor"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("detail"),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }

    public List<SettingView> settings() {
        return support.jdbc().query(
                "SELECT setting_key,setting_value,description,updated_at FROM system_setting ORDER BY setting_key",
                (rs, row) -> new SettingView(
                        rs.getString("setting_key"), rs.getString("setting_value"),
                        rs.getString("description"), rs.getObject("updated_at", OffsetDateTime.class)));
    }

    @Transactional
    public SettingView updateSetting(String key, String value, String admin) {
        support.validateSetting(key, value);
        long adminId = support.userId(admin);
        int changed = support.jdbc().update(
                "UPDATE system_setting SET setting_value=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE setting_key=?",
                value, adminId, key);
        if (changed == 0) throw support.notFound("系统设置不存在");
        support.audit(adminId, "SETTING_UPDATE", "SYSTEM_SETTING", key, value);
        return settings().stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }

    @Transactional
    public void setMapObjectActive(String type, UUID id, boolean active, String admin) {
        String sql = switch (type) {
            case "building" -> "UPDATE building SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
            case "entrance" -> "UPDATE building_entrance SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
            case "node" -> "UPDATE route_node SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
            case "facility" -> "UPDATE accessible_facility SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
            case "edge" -> "UPDATE route_edge SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的地图对象类型");
        };
        Object status = "edge".equals(type) ? (active ? "ACTIVE" : "INACTIVE") : active;
        if (support.jdbc().update(sql, status, id) == 0) throw support.notFound("地图对象不存在");
        support.audit(support.userId(admin), active ? "MAP_OBJECT_ENABLE" : "MAP_OBJECT_DISABLE",
                type.toUpperCase(), id.toString(), null);
    }

    @Transactional
    public void resetDemo(UUID datasetId, String admin) {
        Boolean demo = support.jdbc().queryForObject(
                "SELECT is_demo FROM dataset WHERE id=? FOR UPDATE", Boolean.class, datasetId);
        if (!Boolean.TRUE.equals(demo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许重置 Demo 数据集");
        }
        support.jdbc().update("DELETE FROM facility_rating WHERE dataset_id=?", datasetId);
        support.jdbc().update("DELETE FROM facility_comment WHERE dataset_id=?", datasetId);
        support.jdbc().update("DELETE FROM facility_suggestion WHERE dataset_id=?", datasetId);
        support.jdbc().update("DELETE FROM route_history WHERE dataset_id=?", datasetId);
        support.jdbc().update("DELETE FROM barrier_report WHERE dataset_id=? AND data_source='USER_REPORT'", datasetId);
        support.jdbc().update("UPDATE dataset SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?", datasetId);
        support.jdbc().update("UPDATE building SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        support.jdbc().update("UPDATE building_entrance SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        support.jdbc().update("UPDATE route_node SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        support.jdbc().update(
                """
                UPDATE route_edge SET
                  status=CASE WHEN external_id='E-31' THEN 'CLOSED' ELSE 'ACTIVE' END,
                  slope_level=CASE external_id
                    WHEN 'E-02' THEN 'MODERATE' WHEN 'E-12' THEN 'STEEP'
                    WHEN 'E-04' THEN 'UNKNOWN' WHEN 'E-16' THEN 'UNKNOWN' WHEN 'E-22' THEN 'UNKNOWN'
                    ELSE slope_level END,
                  has_stairs=(external_id='E-02'),
                  stairs_count=CASE WHEN external_id='E-02' THEN 12 ELSE 0 END,
                  width_level=CASE
                    WHEN external_id='E-02' THEN 'NARROW'
                    WHEN external_id IN ('E-04','E-16','E-22') THEN 'UNKNOWN'
                    ELSE width_level END,
                  surface_type=CASE
                    WHEN external_id='E-02' OR external_id='E-12' THEN 'CONCRETE'
                    WHEN external_id IN ('E-04','E-22','E-31') THEN 'ASPHALT'
                    WHEN external_id='E-16' THEN 'UNKNOWN' ELSE surface_type END,
                  lighting_level=CASE
                    WHEN external_id='E-02' OR external_id='E-31' THEN 'MEDIUM'
                    WHEN external_id IN ('E-04','E-12','E-22') THEN 'LOW'
                    WHEN external_id='E-16' THEN 'NONE' ELSE lighting_level END,
                  risk_level=CASE
                    WHEN external_id IN ('E-02','E-12','E-31') THEN 'HIGH'
                    WHEN external_id IN ('E-04','E-16','E-22') THEN 'UNKNOWN'
                    ELSE risk_level END,
                  confidence_level='UNKNOWN',updated_at=CURRENT_TIMESTAMP
                WHERE dataset_id=? AND data_source='DEMO_GENERATED'
                """,
                datasetId);
        support.jdbc().update("UPDATE accessible_facility SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        support.jdbc().update(
                """
                UPDATE barrier_report SET active=(external_id IN ('BAR-01','BAR-02','BAR-03')),
                  review_status=CASE WHEN external_id IN ('BAR-01','BAR-02','BAR-03') THEN 'APPROVED' ELSE 'NEEDS_VERIFICATION' END,
                  confidence_level='UNKNOWN',reviewed_by=NULL,reviewed_at=NULL
                WHERE dataset_id=? AND data_source='DEMO_GENERATED'
                """,
                datasetId);
        support.audit(support.userId(admin), "DEMO_RESET", "DATASET", datasetId.toString(),
                "business-and-five-scenarios-reset");
    }
}
