package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.*;

import cn.barrierfreecampus.routing.RoutingDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BusinessService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public ProfileView profile(String username) {
        ensureProfile(username);
        return jdbc.queryForObject(
                """
                SELECT u.username,COALESCE(p.display_name,u.username) display_name,p.default_mobility_mode,
                  p.avoid_stairs,p.distance_weight,p.slope_weight,p.width_weight,
                  p.prefer_rest_area,p.prefer_accessible_toilet
                FROM app_user u JOIN user_profile p ON p.user_id=u.id WHERE u.username=?
                """,
                (rs, row) -> new ProfileView(
                        rs.getString("username"), rs.getString("display_name"),
                        rs.getString("default_mobility_mode"), rs.getBoolean("avoid_stairs"),
                        rs.getDouble("distance_weight"), rs.getDouble("slope_weight"),
                        rs.getDouble("width_weight"), rs.getBoolean("prefer_rest_area"),
                        rs.getBoolean("prefer_accessible_toilet")),
                username);
    }

    @Transactional
    public ProfileView updateProfile(String username, ProfileUpdateRequest request) {
        long userId = userId(username);
        jdbc.update(
                """
                INSERT INTO user_profile(user_id,display_name,default_mobility_mode,avoid_stairs,
                  distance_weight,slope_weight,width_weight,prefer_rest_area,prefer_accessible_toilet)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET display_name=EXCLUDED.display_name,
                  default_mobility_mode=EXCLUDED.default_mobility_mode,avoid_stairs=EXCLUDED.avoid_stairs,
                  distance_weight=EXCLUDED.distance_weight,slope_weight=EXCLUDED.slope_weight,
                  width_weight=EXCLUDED.width_weight,prefer_rest_area=EXCLUDED.prefer_rest_area,
                  prefer_accessible_toilet=EXCLUDED.prefer_accessible_toilet,updated_at=CURRENT_TIMESTAMP
                """,
                userId, blankToNull(request.displayName()), request.defaultMobilityMode(), request.avoidStairs(),
                request.distanceWeight(), request.slopeWeight(), request.widthWeight(),
                request.preferRestArea(), request.preferAccessibleToilet());
        audit(userId, "PROFILE_UPDATE", "USER", Long.toString(userId), null);
        return profile(username);
    }

    public FacilityDetail facility(UUID facilityId, String username) {
        long userId = userId(username);
        FacilityDetail base = jdbc.query(
                        """
                        SELECT f.id,f.name,f.facility_type,MAX(b.name) building_name,f.floor_label,f.open_status,f.description,
                          f.data_source,f.confidence_level,f.photo_url,f.updated_at,ST_X(f.geom) lng,ST_Y(f.geom) lat,
                          COALESCE(AVG(r.rating),0) average_rating,COUNT(r.id) rating_count,
                          MAX(CASE WHEN r.user_id=? THEN r.rating END) my_rating
                        FROM accessible_facility f LEFT JOIN facility_rating r ON r.facility_id=f.id
                        LEFT JOIN building b ON b.id=f.building_id
                        WHERE f.id=? AND f.active=TRUE
                        GROUP BY f.id
                        """,
                        (rs, row) -> new FacilityDetail(
                                rs.getObject("id", UUID.class), rs.getString("name"),
                                rs.getString("facility_type"), rs.getString("building_name"), rs.getString("floor_label"),
                                rs.getString("open_status"), rs.getString("description"),
                                rs.getString("data_source"), rs.getString("confidence_level"),
                                rs.getString("photo_url"), rs.getObject("updated_at", OffsetDateTime.class),
                                rs.getDouble("lng"), rs.getDouble("lat"),
                                Math.round(rs.getDouble("average_rating") * 10.0) / 10.0,
                                rs.getInt("rating_count"), (Integer) rs.getObject("my_rating"), List.of()),
                        userId, facilityId)
                .stream().findFirst().orElseThrow(() -> notFound("设施不存在或已停用"));
        List<FacilityCommentView> comments = jdbc.query(
                """
                SELECT c.id,u.username,c.content,c.created_at FROM facility_comment c
                JOIN app_user u ON u.id=c.user_id
                WHERE c.facility_id=? AND c.status='VISIBLE' ORDER BY c.created_at DESC LIMIT 50
                """,
                (rs, row) -> new FacilityCommentView(
                        rs.getLong("id"), rs.getString("username"), rs.getString("content"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                facilityId);
        return new FacilityDetail(
                base.id(), base.name(), base.facilityType(), base.buildingName(), base.floorLabel(), base.openStatus(),
                base.description(), base.dataSource(), base.confidenceLevel(), base.photoUrl(), base.updatedAt(),
                base.lng(), base.lat(),
                base.averageRating(), base.ratingCount(), base.myRating(), comments);
    }

    @Transactional
    public void rateFacility(UUID facilityId, String username, RatingRequest request) {
        long userId = userId(username);
        UUID datasetId = facilityDataset(facilityId);
        jdbc.update(
                """
                INSERT INTO facility_rating(dataset_id,facility_id,user_id,rating) VALUES (?,?,?,?)
                ON CONFLICT(facility_id,user_id) DO UPDATE SET rating=EXCLUDED.rating,updated_at=CURRENT_TIMESTAMP
                """,
                datasetId, facilityId, userId, request.rating());
        audit(userId, "FACILITY_RATE", "ACCESSIBLE_FACILITY", facilityId.toString(), "rating=" + request.rating());
    }

    @Transactional
    public long commentFacility(UUID facilityId, String username, CommentRequest request) {
        long userId = userId(username);
        UUID datasetId = facilityDataset(facilityId);
        Long id = jdbc.queryForObject(
                """
                INSERT INTO facility_comment(dataset_id,facility_id,user_id,content)
                VALUES (?,?,?,?) RETURNING id
                """,
                Long.class, datasetId, facilityId, userId, request.content().trim());
        audit(userId, "FACILITY_COMMENT", "ACCESSIBLE_FACILITY", facilityId.toString(), null);
        return id == null ? 0 : id;
    }

    @Transactional
    public UUID suggestFacility(UUID facilityId, String username, SuggestionRequest request) {
        long userId = userId(username);
        UUID datasetId = facilityDataset(facilityId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO facility_suggestion(id,dataset_id,facility_id,user_id,suggestion_type,content) VALUES (?,?,?,?,?,?)",
                id, datasetId, facilityId, userId, request.suggestionType(), request.content().trim());
        audit(userId, "FACILITY_SUGGEST", "ACCESSIBLE_FACILITY", facilityId.toString(), request.suggestionType());
        return id;
    }

    @Transactional
    public BarrierReportView submitBarrier(String username, BarrierSubmitRequest request) {
        long userId = userId(username);
        requireEnabledDataset(request.datasetId());
        int matchHours = settingInt("barrier.match.window.hours", 24, 1, 168);
        double matchDegrees = settingInt("barrier.match.radius.meters", 50, 5, 500) / 111_000.0;
        Integer duplicate = jdbc.queryForObject(
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

        UUID matched = jdbc.query(
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
        jdbc.update(
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
            jdbc.update(
                    """
                    UPDATE barrier_report SET review_status='NEEDS_VERIFICATION',confidence_level='MEDIUM',
                      updated_at=CURRENT_TIMESTAMP
                    WHERE (id=? OR matched_report_id=?) AND review_status IN ('PENDING','NEEDS_VERIFICATION')
                    """,
                    matched, matched);
        }
        audit(userId, "BARRIER_REPORT_SUBMIT", "BARRIER_REPORT", id.toString(),
                matched == null ? "single-user-low" : "multi-user-medium");
        return barrierById(id);
    }

    public List<BarrierReportView> myBarriers(String username) {
        return barrierQuery("WHERE b.reporter_id=? ORDER BY b.created_at DESC", userId(username));
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
        long adminId = userId(admin);
        BarrierReportView current = barrierById(id);
        boolean approved = "APPROVED".equals(request.decision());
        String confidence = request.fieldVerified()
                ? "HIGH"
                : "MEDIUM".equals(current.confidenceLevel()) ? "MEDIUM" : "LOW";
        jdbc.update(
                """
                UPDATE barrier_report SET review_status=?,active=?,confidence_level=?,reviewed_by=?,
                  reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """,
                request.decision(), approved, confidence, adminId, id);
        audit(adminId, "BARRIER_REVIEW_" + request.decision(), "BARRIER_REPORT", id.toString(),
                (request.fieldVerified() ? "field-verified; " : "") + nullToEmpty(request.note()));
        return barrierById(id);
    }

    @Transactional
    public UUID recordHistory(String username, RoutingDtos.RoutePlanRequest request, RoutingDtos.RoutePlanResponse result) {
        long userId = userId(username);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO route_history(id,user_id,dataset_id,start_node_id,end_node_id,mobility_mode,
                  travel_period,request_json,result_json) VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                """,
                id, userId, request.datasetId(), request.startNodeId(), request.endNodeId(),
                request.mobilityMode().name(), request.travelPeriod().name(), json(request), json(result));
        return id;
    }

    public List<RouteHistoryView> history(String username) {
        return jdbc.query(
                """
                SELECT h.id,h.dataset_id,h.start_node_id,h.end_node_id,
                  COALESCE(s.name,s.external_id) start_name,COALESCE(e.name,e.external_id) end_name,
                  h.mobility_mode,h.travel_period,h.result_json::text result_json,h.created_at
                FROM route_history h JOIN route_node s ON s.id=h.start_node_id JOIN route_node e ON e.id=h.end_node_id
                WHERE h.user_id=? ORDER BY h.created_at DESC LIMIT 100
                """,
                (rs, row) -> historyRow(rs), userId(username));
    }

    @Transactional
    public void deleteHistory(UUID id, String username) {
        int changed = jdbc.update("DELETE FROM route_history WHERE id=? AND user_id=?", id, userId(username));
        if (changed == 0) throw notFound("路线历史不存在");
    }

    @Transactional
    public UUID favorite(UUID historyId, String username, FavoriteRequest request) {
        long userId = userId(username);
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM route_history WHERE id=? AND user_id=?", Integer.class, historyId, userId);
        if (owned == null || owned != 1) throw notFound("路线历史不存在");
        UUID id = UUID.randomUUID();
        UUID saved = jdbc.queryForObject(
                """
                INSERT INTO route_favorite(id,user_id,history_id,route_profile,name) VALUES (?,?,?,?,?)
                ON CONFLICT(user_id,history_id,route_profile) DO UPDATE SET name=EXCLUDED.name
                RETURNING id
                """,
                UUID.class, id, userId, historyId, request.routeProfile(), request.name().trim());
        audit(userId, "ROUTE_FAVORITE", "ROUTE_HISTORY", historyId.toString(), request.routeProfile());
        return saved;
    }

    public List<FavoriteView> favorites(String username) {
        return jdbc.query(
                """
                SELECT f.id,f.history_id,f.route_profile,f.name,h.result_json::text result_json,f.created_at
                FROM route_favorite f JOIN route_history h ON h.id=f.history_id
                WHERE f.user_id=? ORDER BY f.created_at DESC
                """,
                (rs, row) -> new FavoriteView(
                        rs.getObject("id", UUID.class), rs.getObject("history_id", UUID.class),
                        rs.getString("route_profile"), rs.getString("name"),
                        readJson(rs.getString("result_json")), rs.getObject("created_at", OffsetDateTime.class)),
                userId(username));
    }

    @Transactional
    public void removeFavorite(UUID id, String username) {
        int changed = jdbc.update("DELETE FROM route_favorite WHERE id=? AND user_id=?", id, userId(username));
        if (changed == 0) throw notFound("收藏不存在");
    }

    public AdminOverview overview() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("users", count("SELECT COUNT(*) FROM app_user"));
        counts.put("activeBarriers", count("SELECT COUNT(*) FROM barrier_report WHERE active=TRUE AND (ends_at IS NULL OR ends_at>CURRENT_TIMESTAMP)"));
        counts.put("pendingBarriers", count("SELECT COUNT(*) FROM barrier_report WHERE review_status IN ('PENDING','NEEDS_VERIFICATION')"));
        counts.put("facilities", count("SELECT COUNT(*) FROM accessible_facility WHERE active=TRUE"));
        counts.put("routePlans", count("SELECT COUNT(*) FROM route_history"));
        counts.put("suggestions", count("SELECT COUNT(*) FROM facility_suggestion WHERE status='PENDING'"));
        return new AdminOverview(Map.copyOf(counts), adminBarriers("ALL").stream().limit(8).toList());
    }

    public List<FacilitySuggestionView> suggestions() {
        return jdbc.query(
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
        if (jdbc.update("UPDATE facility_suggestion SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id) == 0) {
            throw notFound("设施建议不存在");
        }
        audit(userId(admin), "FACILITY_SUGGESTION_" + status, "FACILITY_SUGGESTION", id.toString(), null);
    }

    public List<AdminUserView> users() {
        return jdbc.query(
                "SELECT id,username,role,enabled,created_at FROM app_user ORDER BY created_at,id",
                (rs, row) -> new AdminUserView(
                        rs.getLong("id"), rs.getString("username"), rs.getString("role"),
                        rs.getBoolean("enabled"), rs.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional
    public void setUserEnabled(long id, boolean enabled, String admin) {
        long adminId = userId(admin);
        if (id == adminId && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能禁用当前登录管理员");
        }
        int changed = jdbc.update("UPDATE app_user SET enabled=? WHERE id=?", enabled, id);
        if (changed == 0) throw notFound("用户不存在");
        if (!enabled) jdbc.update("UPDATE refresh_token SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", id);
        audit(adminId, enabled ? "USER_ENABLE" : "USER_DISABLE", "USER", Long.toString(id), null);
    }

    public List<AuditView> audits() {
        return jdbc.query(
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
        return jdbc.query(
                "SELECT setting_key,setting_value,description,updated_at FROM system_setting ORDER BY setting_key",
                (rs, row) -> new SettingView(
                        rs.getString("setting_key"), rs.getString("setting_value"),
                        rs.getString("description"), rs.getObject("updated_at", OffsetDateTime.class)));
    }

    @Transactional
    public SettingView updateSetting(String key, String value, String admin) {
        validateSetting(key, value);
        long adminId = userId(admin);
        int changed = jdbc.update(
                "UPDATE system_setting SET setting_value=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE setting_key=?",
                value, adminId, key);
        if (changed == 0) throw notFound("系统设置不存在");
        audit(adminId, "SETTING_UPDATE", "SYSTEM_SETTING", key, value);
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
        if (jdbc.update(sql, status, id) == 0) throw notFound("地图对象不存在");
        audit(userId(admin), active ? "MAP_OBJECT_ENABLE" : "MAP_OBJECT_DISABLE", type.toUpperCase(), id.toString(), null);
    }

    @Transactional
    public void resetDemo(UUID datasetId, String admin) {
        Boolean demo = jdbc.queryForObject("SELECT is_demo FROM dataset WHERE id=? FOR UPDATE", Boolean.class, datasetId);
        if (!Boolean.TRUE.equals(demo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许重置 Demo 数据集");
        }
        jdbc.update("DELETE FROM facility_rating WHERE dataset_id=?", datasetId);
        jdbc.update("DELETE FROM facility_comment WHERE dataset_id=?", datasetId);
        jdbc.update("DELETE FROM facility_suggestion WHERE dataset_id=?", datasetId);
        jdbc.update("DELETE FROM route_history WHERE dataset_id=?", datasetId);
        jdbc.update("DELETE FROM barrier_report WHERE dataset_id=? AND data_source='USER_REPORT'", datasetId);
        jdbc.update("UPDATE building SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        jdbc.update("UPDATE building_entrance SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        jdbc.update("UPDATE route_node SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        jdbc.update("UPDATE route_edge SET status='ACTIVE' WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        jdbc.update("UPDATE accessible_facility SET active=TRUE WHERE dataset_id=? AND data_source='DEMO_GENERATED'", datasetId);
        jdbc.update(
                """
                UPDATE barrier_report SET active=(external_id IN ('BAR-01','BAR-02','BAR-03')),
                  review_status=CASE WHEN external_id IN ('BAR-01','BAR-02','BAR-03') THEN 'APPROVED' ELSE 'NEEDS_VERIFICATION' END,
                  confidence_level='UNKNOWN',reviewed_by=NULL,reviewed_at=NULL
                WHERE dataset_id=? AND data_source='DEMO_GENERATED'
                """,
                datasetId);
        audit(userId(admin), "DEMO_RESET", "DATASET", datasetId.toString(), "business-and-status-reset");
    }

    @Transactional
    public int expireBarriers() {
        if (!Boolean.parseBoolean(settingValue("barrier.scheduler.enabled", "true"))) return 0;
        List<UUID> expired = jdbc.query(
                """
                UPDATE barrier_report SET active=FALSE,updated_at=CURRENT_TIMESTAMP
                WHERE active=TRUE AND ends_at IS NOT NULL AND ends_at<=CURRENT_TIMESTAMP RETURNING id
                """,
                (rs, row) -> rs.getObject("id", UUID.class));
        expired.forEach(id -> audit(null, "BARRIER_AUTO_EXPIRE", "BARRIER_REPORT", id.toString(), null));
        return expired.size();
    }

    private List<BarrierReportView> barrierQuery(String suffix, Object... args) {
        return jdbc.query(
                """
                SELECT b.id,b.dataset_id,b.external_id,b.title,b.barrier_type,b.description,b.review_status,
                  b.active,b.confidence_level,b.matched_report_id,u.username reporter_username,b.ends_at,
                  b.created_at,b.reviewed_at,ST_X(b.geom) lng,ST_Y(b.geom) lat
                FROM barrier_report b LEFT JOIN app_user u ON u.id=b.reporter_id
                """ + suffix,
                (rs, row) -> barrierRow(rs), args);
    }

    private BarrierReportView barrierById(UUID id) {
        return barrierQuery("WHERE b.id=?", id).stream().findFirst().orElseThrow(() -> notFound("障碍上报不存在"));
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

    private RouteHistoryView historyRow(ResultSet rs) throws SQLException {
        return new RouteHistoryView(
                rs.getObject("id", UUID.class), rs.getObject("dataset_id", UUID.class),
                rs.getObject("start_node_id", UUID.class), rs.getObject("end_node_id", UUID.class),
                rs.getString("start_name"), rs.getString("end_name"), rs.getString("mobility_mode"),
                rs.getString("travel_period"), readJson(rs.getString("result_json")),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private void ensureProfile(String username) {
        long id = userId(username);
        jdbc.update("INSERT INTO user_profile(user_id,display_name) VALUES (?,?) ON CONFLICT(user_id) DO NOTHING", id, username);
    }

    private long userId(String username) {
        List<Long> ids = jdbc.query("SELECT id FROM app_user WHERE username=?", (rs, row) -> rs.getLong(1), username);
        return ids.stream().findFirst().orElseThrow(() -> notFound("用户不存在"));
    }

    private UUID facilityDataset(UUID facilityId) {
        return jdbc.query("SELECT dataset_id FROM accessible_facility WHERE id=? AND active=TRUE",
                        (rs, row) -> rs.getObject(1, UUID.class), facilityId)
                .stream().findFirst().orElseThrow(() -> notFound("设施不存在或已停用"));
    }

    private void requireEnabledDataset(UUID datasetId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dataset WHERE id=? AND enabled=TRUE", Integer.class, datasetId);
        if (count == null || count != 1) throw notFound("数据集不存在或未启用");
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String settingValue(String key, String fallback) {
        return jdbc.query("SELECT setting_value FROM system_setting WHERE setting_key=?",
                        (rs, row) -> rs.getString(1), key)
                .stream().findFirst().orElse(fallback);
    }

    private int settingInt(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(settingValue(key, Integer.toString(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void validateSetting(String key, String value) {
        try {
            switch (key) {
                case "barrier.match.radius.meters" -> {
                    int number = Integer.parseInt(value);
                    if (number < 5 || number > 500) throw new NumberFormatException();
                }
                case "barrier.match.window.hours" -> {
                    int number = Integer.parseInt(value);
                    if (number < 1 || number > 168) throw new NumberFormatException();
                }
                case "barrier.scheduler.enabled" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new NumberFormatException();
                    }
                }
                default -> throw notFound("系统设置不存在");
            }
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统设置值超出允许范围");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务数据序列化失败", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务数据无法解析", exception);
        }
    }

    private void audit(Long actorId, String action, String targetType, String targetId, String detail) {
        jdbc.update(
                "INSERT INTO audit_log(actor_id,action,target_type,target_id,detail) VALUES (?,?,?,?,?)",
                actorId, action, targetType, targetId, blankToNull(detail));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
