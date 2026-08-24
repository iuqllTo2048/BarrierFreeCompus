package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.DatasetView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 地图数据模块共享的常量、查询与工具方法。
 */
@Component
public class MapDataSupport {
    static final String MANUAL = "MANUAL_ESTIMATE";
    static final String UNKNOWN = "UNKNOWN";
    static final List<String> IMPORT_ORDER = List.of(
            "BUILDING", "NODE", "ENTRANCE", "FACILITY", "EDGE", "BARRIER");
    static final Set<String> IMPORT_TYPES = Set.copyOf(IMPORT_ORDER);
    static final Map<String, String> TYPE_LABELS = Map.of(
            "BUILDING", "建筑", "ENTRANCE", "入口", "NODE", "道路节点",
            "EDGE", "道路", "FACILITY", "设施", "BARRIER", "管理员障碍");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DatasetMapper datasetMapper;

    public MapDataSupport(JdbcTemplate jdbc, ObjectMapper objectMapper, DatasetMapper datasetMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.datasetMapper = datasetMapper;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public DatasetMapper datasetMapper() {
        return datasetMapper;
    }

    public DatasetView requireDataset(UUID id, boolean includeDisabled) {
        String condition = includeDisabled ? "" : " AND d.enabled = TRUE";
        List<DatasetView> rows = jdbc.query(
                """
                SELECT d.id, d.code, d.name, d.dataset_type, d.coordinate_system, d.enabled, d.is_demo,
                       d.seed, d.description, c.center_lng, c.center_lat
                FROM dataset d JOIN campus c ON c.id = d.campus_id
                WHERE d.id = ?
                """ + condition,
                (rs, row) -> new DatasetView(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("dataset_type"), rs.getString("coordinate_system"),
                        rs.getBoolean("enabled"), rs.getBoolean("is_demo"),
                        rs.getObject("seed", Long.class), rs.getString("description"),
                        rs.getDouble("center_lng"), rs.getDouble("center_lat")),
                id);
        if (rows.isEmpty()) throw notFound("数据集不存在或未启用");
        return rows.getFirst();
    }

    public void audit(String username, String action, String targetType, String targetId) {
        jdbc.update(
                """
                INSERT INTO audit_log(actor_id,action,target_type,target_id)
                SELECT id,?,?,? FROM app_user WHERE username=?
                """,
                action, targetType, targetId, username);
    }

    public JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库空间数据无法转换为 GeoJSON", exception);
        }
    }

    public ObjectNode point(double lng, double lat) {
        ObjectNode point = objectMapper.createObjectNode();
        point.put("type", "Point");
        point.putArray("coordinates").add(lng).add(lat);
        return point;
    }

    public String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw badRequest(field + " 不能为空");
        return value;
    }

    public String nullableText(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    public ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
