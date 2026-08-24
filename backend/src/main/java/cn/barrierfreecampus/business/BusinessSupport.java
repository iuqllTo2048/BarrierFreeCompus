package cn.barrierfreecampus.business;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务模块共享的用户、设置、审计与 JSON 工具。
 */
@Component
public class BusinessSupport {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BusinessSupport(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public long userId(String username) {
        List<Long> ids = jdbc.query("SELECT id FROM app_user WHERE username=?", (rs, row) -> rs.getLong(1), username);
        return ids.stream().findFirst().orElseThrow(() -> notFound("用户不存在"));
    }

    public void audit(Long actorId, String action, String targetType, String targetId, String detail) {
        jdbc.update(
                "INSERT INTO audit_log(actor_id,action,target_type,target_id,detail) VALUES (?,?,?,?,?)",
                actorId, action, targetType, targetId, blankToNull(detail));
    }

    public void requireEnabledDataset(UUID datasetId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataset WHERE id=? AND enabled=TRUE", Integer.class, datasetId);
        if (count == null || count != 1) throw notFound("数据集不存在或未启用");
    }

    public UUID facilityDataset(UUID facilityId) {
        return jdbc.query("SELECT dataset_id FROM accessible_facility WHERE id=? AND active=TRUE",
                        (rs, row) -> rs.getObject(1, UUID.class), facilityId)
                .stream().findFirst().orElseThrow(() -> notFound("设施不存在或已停用"));
    }

    public long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    public String settingValue(String key, String fallback) {
        return jdbc.query("SELECT setting_value FROM system_setting WHERE setting_key=?",
                        (rs, row) -> rs.getString(1), key)
                .stream().findFirst().orElse(fallback);
    }

    public int settingInt(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(settingValue(key, Integer.toString(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public void validateSetting(String key, String value) {
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

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务数据序列化失败", exception);
        }
    }

    public JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务数据无法解析", exception);
        }
    }

    public ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
