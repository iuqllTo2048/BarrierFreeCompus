package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.Coordinate;
import static cn.barrierfreecampus.mapdata.MapDtos.DatasetView;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.ImportResult;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * GeoJSON v1/v2 导入、预检、应用与备份恢复。
 */
@Component
public class GeoJsonImportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MapDataSupport support;
    private final GeoJsonExportService exportService;
    private final MapObjectService objectService;

    public GeoJsonImportService(JdbcTemplate jdbc, ObjectMapper objectMapper, MapDataSupport support,
                                GeoJsonExportService exportService, MapObjectService objectService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.support = support;
        this.exportService = exportService;
        this.objectService = objectService;
    }

    @Transactional
    public ImportResult importGeoJson(UUID datasetId, JsonNode root, String actor) {
        DatasetView dataset = support.requireDataset(datasetId, true);
        if (!dataset.demo()) throw support.badRequest("GeoJSON 安全导入仅允许 DEMO 数据集");
        if (!"FeatureCollection".equals(root.path("type").asText())) throw support.badRequest("必须是 FeatureCollection");
        if (!datasetId.toString().equals(root.path("datasetId").asText())) throw support.badRequest("datasetId 与目标数据集不一致");
        if (!dataset.coordinateSystem().equals(root.path("coordinateSystem").asText())) {
            throw support.badRequest("坐标系必须为 " + dataset.coordinateSystem());
        }
        JsonNode features = root.path("features");
        if (!features.isArray() || features.size() > 500) throw support.badRequest("features 必须是最多 500 项的数组");

        int nodes = 0;
        int facilities = 0;
        int edges = 0;
        for (JsonNode feature : features) {
            if ("NODE".equals(feature.path("properties").path("entityType").asText())) {
                JsonNode coordinate = requirePoint(feature);
                JsonNode props = feature.path("properties");
                objectService.saveNode(datasetId, null, new NodeRequest(
                        support.requiredText(props, "externalId"), support.nullableText(props, "name"),
                        props.path("nodeType").asText("INTERSECTION"), props.path("active").asBoolean(true),
                        new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble())), actor);
                nodes++;
            }
        }
        for (JsonNode feature : features) {
            if ("FACILITY".equals(feature.path("properties").path("entityType").asText())) {
                JsonNode coordinate = requirePoint(feature);
                JsonNode props = feature.path("properties");
                createOrUpdateImportedFacility(datasetId, props, coordinate, actor);
                facilities++;
            }
        }
        for (JsonNode feature : features) {
            if ("EDGE".equals(feature.path("properties").path("entityType").asText())) {
                JsonNode props = feature.path("properties");
                JsonNode coordinates = feature.path("geometry").path("coordinates");
                if (!"LineString".equals(feature.path("geometry").path("type").asText())
                        || !coordinates.isArray() || coordinates.size() < 2) {
                    throw support.badRequest("EDGE geometry 必须是至少两个坐标的 LineString");
                }
                UUID from = nodeIdByExternal(datasetId, support.requiredText(props, "fromNodeExternalId"));
                UUID to = nodeIdByExternal(datasetId, support.requiredText(props, "toNodeExternalId"));
                List<Coordinate> intermediate = new ArrayList<>();
                for (int index = 1; index < coordinates.size() - 1; index++) {
                    JsonNode coordinate = coordinates.get(index);
                    intermediate.add(new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
                }
                EdgeRequest request = new EdgeRequest(
                        support.requiredText(props, "externalId"), support.nullableText(props, "name"), from, to,
                        props.path("distanceM").decimalValue(), props.path("slopeLevel").asText("UNKNOWN"),
                        props.path("hasStairs").asBoolean(false), props.path("stairsCount").asInt(0),
                        props.path("widthLevel").asText("UNKNOWN"), props.path("surfaceType").asText("UNKNOWN"),
                        props.path("lightingLevel").asText("UNKNOWN"), props.path("bidirectional").asBoolean(true),
                        props.path("status").asText("ACTIVE"), props.path("riskLevel").asText("UNKNOWN"), intermediate);
                UUID existing = optionalEdgeId(datasetId, request.externalId());
                objectService.saveEdge(datasetId, existing, request, actor);
                edges++;
            }
        }
        support.audit(actor, "GEOJSON_IMPORT", "DATASET", datasetId.toString());
        return new ImportResult(nodes, edges, facilities);
    }

    public MapDtos.ImportPreview previewGeoJson(UUID datasetId, JsonNode root) {
        DatasetView dataset = support.requireDataset(datasetId, true);
        ImportAnalysis analysis = analyzeImport(dataset, root);
        return new MapDtos.ImportPreview(
                fingerprint(root), analysis.targetFingerprint(), analysis.summaries(),
                analysis.conflictSamples(), analysis.errors(), analysis.warnings());
    }

    @Transactional
    public MapDtos.ImportApplyResult applyGeoJson(
            UUID datasetId, MapDtos.ImportApplyRequest request, String actor) {
        DatasetView dataset = support.requireDataset(datasetId, true);
        ImportAnalysis analysis = analyzeImport(dataset, request.geoJson());
        if (!analysis.errors().isEmpty()) throw support.badRequest("GeoJSON 预检未通过：" + analysis.errors().getFirst());
        if (!fingerprint(request.geoJson()).equals(request.payloadFingerprint())) {
            throw support.badRequest("文件内容与预检时不一致，请重新预检");
        }
        if (!analysis.targetFingerprint().equals(request.targetFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标数据集已发生变化，请重新预检");
        }

        JsonNode backup = exportService.exportGeoJson(datasetId);
        UUID backupId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO geojson_import_backup(
                    id,dataset_id,actor_id,target_fingerprint,payload_fingerprint,conflict_policy,snapshot_json)
                SELECT ?,?,id,?,?,?,?::jsonb FROM app_user WHERE username=?
                """,
                backupId, datasetId, request.targetFingerprint(), request.payloadFingerprint(),
                request.conflictPolicy(), backup.toString(), actor);

        Map<String, JsonNode> target = featureIndex(backup.path("features"));
        Map<String, JsonNode> incoming = analysis.incoming();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int keptLocal = 0;
        for (String type : MapDataSupport.IMPORT_ORDER) {
            for (Map.Entry<String, JsonNode> entry : incoming.entrySet()) {
                if (!entry.getKey().startsWith(type + ":")) continue;
                JsonNode existing = target.get(entry.getKey());
                if (existing != null && existing.equals(entry.getValue())) {
                    unchanged++;
                    continue;
                }
                if (existing != null && "KEEP_TARGET".equals(request.conflictPolicy())) {
                    keptLocal++;
                    continue;
                }
                upsertImportedFeature(datasetId, entry.getValue(), actor);
                if (existing == null) created++;
                else updated++;
            }
        }
        support.audit(actor, "GEOJSON_FORMAL_IMPORT", "DATASET", datasetId.toString());
        return new MapDtos.ImportApplyResult(backupId, created, updated, unchanged, keptLocal);
    }

    public List<MapDtos.GeoJsonBackupView> listGeoJsonBackups(UUID datasetId) {
        support.requireDataset(datasetId, true);
        return jdbc.query(
                """
                SELECT b.id, b.target_fingerprint, b.payload_fingerprint, b.conflict_policy,
                       b.snapshot_json, b.created_at, u.username
                FROM geojson_import_backup b
                LEFT JOIN app_user u ON u.id = b.actor_id
                WHERE b.dataset_id = ?
                ORDER BY b.created_at DESC
                """,
                (rs, row) -> new MapDtos.GeoJsonBackupView(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("username"),
                        rs.getString("conflict_policy"),
                        rs.getString("target_fingerprint"),
                        rs.getString("payload_fingerprint"),
                        objectCounts(support.parseJson(rs.getString("snapshot_json")))),
                datasetId);
    }

    public MapDtos.RestorePreview previewGeoJsonRestore(UUID datasetId, UUID backupId) {
        support.requireDataset(datasetId, true);
        BackupRow backup = requireBackup(datasetId, backupId);
        RestorePlan plan = buildRestorePlan(datasetId, backup.snapshot());
        return new MapDtos.RestorePreview(
                backup.id(),
                backup.createdAt(),
                fingerprint(exportService.exportGeoJson(datasetId)),
                backup.targetFingerprint(),
                objectCounts(backup.snapshot()),
                toDeleteCounts(plan),
                keptCounts(plan),
                restoreWarnings(plan));
    }

    @Transactional
    public MapDtos.RestoreResult restoreGeoJsonBackup(
            UUID datasetId, UUID backupId, MapDtos.RestoreApplyRequest request, String actor) {
        support.requireDataset(datasetId, true);
        BackupRow backup = requireBackup(datasetId, backupId);
        String currentFingerprint = fingerprint(exportService.exportGeoJson(datasetId));
        if (!currentFingerprint.equals(request.currentFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标数据集在预检后已发生变化，请重新预检");
        }
        RestorePlan plan = buildRestorePlan(datasetId, backup.snapshot());
        applyRestoreDeletes(datasetId, plan);

        Map<String, Integer> created = new LinkedHashMap<>();
        Map<String, Integer> updated = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) {
            created.put(type, 0);
            updated.put(type, 0);
        }
        Map<String, Set<String>> currentKeys = plan.currentKeys();
        for (JsonNode feature : backup.snapshot().path("features")) {
            JsonNode properties = feature.path("properties");
            String type = properties.path("entityType").asText();
            String externalId = properties.path("externalId").asText();
            boolean existed = currentKeys.getOrDefault(type, Set.of()).contains(externalId);
            upsertImportedFeature(datasetId, feature, actor);
            if (existed) updated.merge(type, 1, Integer::sum);
            else created.merge(type, 1, Integer::sum);
        }
        support.audit(actor, "GEOJSON_BACKUP_RESTORE", "DATASET", datasetId.toString());
        return new MapDtos.RestoreResult(
                backup.id(), created, updated, toDeleteCounts(plan), keptCounts(plan),
                restoreWarnings(plan), Instant.now());
    }

    private BackupRow requireBackup(UUID datasetId, UUID backupId) {
        List<BackupRow> rows = jdbc.query(
                """
                SELECT b.id, b.dataset_id, b.target_fingerprint, b.payload_fingerprint, b.conflict_policy,
                       b.snapshot_json, b.created_at, u.username
                FROM geojson_import_backup b
                LEFT JOIN app_user u ON u.id = b.actor_id
                WHERE b.id = ? AND b.dataset_id = ?
                """,
                (rs, row) -> new BackupRow(
                        rs.getObject("id", UUID.class), rs.getObject("dataset_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("username"),
                        rs.getString("target_fingerprint"), rs.getString("payload_fingerprint"),
                        rs.getString("conflict_policy"), support.parseJson(rs.getString("snapshot_json"))),
                backupId, datasetId);
        if (rows.isEmpty()) throw support.notFound("导入备份不存在");
        return rows.getFirst();
    }

    private Map<String, Integer> objectCounts(JsonNode root) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) counts.put(type, 0);
        for (JsonNode feature : root.path("features")) {
            String type = feature.path("properties").path("entityType").asText();
            if (counts.containsKey(type)) counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    private RestorePlan buildRestorePlan(UUID datasetId, JsonNode snapshot) {
        Map<String, Set<String>> snapshotKeys = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) snapshotKeys.put(type, new LinkedHashSet<>());
        for (JsonNode feature : snapshot.path("features")) {
            String type = feature.path("properties").path("entityType").asText();
            String externalId = feature.path("properties").path("externalId").asText();
            if (snapshotKeys.containsKey(type) && !externalId.isBlank()) {
                snapshotKeys.get(type).add(externalId);
            }
        }

        Map<String, Set<String>> currentKeys = new LinkedHashMap<>();
        currentKeys.put("BUILDING", currentExternalIds(datasetId, "building"));
        currentKeys.put("ENTRANCE", currentExternalIds(datasetId, "building_entrance"));
        currentKeys.put("NODE", currentExternalIds(datasetId, "route_node"));
        currentKeys.put("EDGE", currentExternalIds(datasetId, "route_edge"));
        currentKeys.put("FACILITY", currentExternalIds(datasetId, "accessible_facility"));
        currentKeys.put("BARRIER", currentAdminBarrierIds(datasetId));

        Map<String, Set<String>> protectedKeys = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) protectedKeys.put(type, new LinkedHashSet<>());
        protectedKeys.put("FACILITY", protectedFacilityKeys(datasetId));
        protectedKeys.put("NODE", protectedNodeKeys(datasetId));
        protectedKeys.put("BUILDING", protectedBuildingKeys(datasetId, protectedKeys.get("FACILITY")));

        Map<String, List<String>> toDelete = new LinkedHashMap<>();
        Map<String, List<String>> keptBusiness = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) {
            List<String> deletes = new ArrayList<>();
            List<String> kept = new ArrayList<>();
            for (String externalId : currentKeys.get(type)) {
                if (snapshotKeys.get(type).contains(externalId)) continue;
                if (protectedKeys.get(type).contains(externalId)) kept.add(externalId);
                else deletes.add(externalId);
            }
            toDelete.put(type, deletes);
            keptBusiness.put(type, kept);
        }
        return new RestorePlan(snapshot, snapshotKeys, currentKeys, toDelete, keptBusiness);
    }

    private Set<String> currentExternalIds(UUID datasetId, String table) {
        return new LinkedHashSet<>(jdbc.query(
                "SELECT external_id FROM " + table + " WHERE dataset_id=?",
                (rs, row) -> rs.getString(1), datasetId));
    }

    private Set<String> currentAdminBarrierIds(UUID datasetId) {
        return new LinkedHashSet<>(jdbc.query(
                "SELECT external_id FROM barrier_report WHERE dataset_id=? AND data_source <> 'USER_REPORT'",
                (rs, row) -> rs.getString(1), datasetId));
    }

    private Set<String> protectedFacilityKeys(UUID datasetId) {
        Map<String, String> internalToExternal = internalToExternal(
                "SELECT id, external_id FROM accessible_facility WHERE dataset_id=?", datasetId);
        Set<String> internalIds = new HashSet<>();
        for (String table : List.of("facility_rating", "facility_comment", "facility_suggestion")) {
            internalIds.addAll(jdbc.query(
                    "SELECT DISTINCT facility_id FROM " + table + " WHERE dataset_id=?",
                    (rs, row) -> rs.getString(1), datasetId));
        }
        return externalKeys(internalToExternal, internalIds);
    }

    private Set<String> protectedNodeKeys(UUID datasetId) {
        Map<String, String> internalToExternal = internalToExternal(
                "SELECT id, external_id FROM route_node WHERE dataset_id=?", datasetId);
        Set<String> internalIds = new HashSet<>();
        internalIds.addAll(jdbc.query(
                "SELECT DISTINCT start_node_id FROM route_history WHERE dataset_id=?",
                (rs, row) -> rs.getString(1), datasetId));
        internalIds.addAll(jdbc.query(
                "SELECT DISTINCT end_node_id FROM route_history WHERE dataset_id=?",
                (rs, row) -> rs.getString(1), datasetId));
        return externalKeys(internalToExternal, internalIds);
    }

    private Set<String> protectedBuildingKeys(UUID datasetId, Set<String> protectedFacilityKeys) {
        Map<String, String> internalToExternal = internalToExternal(
                "SELECT id, external_id FROM building WHERE dataset_id=?", datasetId);
        Set<String> internalIds = new HashSet<>();
        for (String facilityExternalId : protectedFacilityKeys) {
            internalIds.addAll(jdbc.query(
                    """
                    SELECT building_id FROM accessible_facility
                    WHERE dataset_id=? AND external_id=? AND building_id IS NOT NULL
                    """,
                    (rs, row) -> rs.getString(1), datasetId, facilityExternalId));
        }
        return externalKeys(internalToExternal, internalIds);
    }

    private Map<String, String> internalToExternal(String sql, UUID datasetId) {
        Map<String, String> mapping = new HashMap<>();
        jdbc.query(sql, rs -> {
            mapping.put(rs.getString(1), rs.getString(2));
        }, datasetId);
        return mapping;
    }

    private Set<String> externalKeys(Map<String, String> internalToExternal, Set<String> internalIds) {
        Set<String> keys = new LinkedHashSet<>();
        for (String internalId : internalIds) {
            String external = internalToExternal.get(internalId);
            if (external != null) keys.add(external);
        }
        return keys;
    }

    private void applyRestoreDeletes(UUID datasetId, RestorePlan plan) {
        for (String externalId : plan.toDelete().get("BARRIER")) {
            jdbc.update(
                    "DELETE FROM barrier_report WHERE dataset_id=? AND external_id=? AND data_source <> 'USER_REPORT'",
                    datasetId, externalId);
        }
        for (String externalId : plan.toDelete().get("EDGE")) {
            jdbc.update("DELETE FROM route_edge WHERE dataset_id=? AND external_id=?", datasetId, externalId);
        }
        for (String externalId : plan.toDelete().get("NODE")) {
            jdbc.update("DELETE FROM route_node WHERE dataset_id=? AND external_id=?", datasetId, externalId);
        }
        for (String externalId : plan.toDelete().get("FACILITY")) {
            jdbc.update("DELETE FROM accessible_facility WHERE dataset_id=? AND external_id=?", datasetId, externalId);
        }
        for (String externalId : plan.toDelete().get("ENTRANCE")) {
            jdbc.update("DELETE FROM building_entrance WHERE dataset_id=? AND external_id=?", datasetId, externalId);
        }
        for (String externalId : plan.toDelete().get("BUILDING")) {
            jdbc.update("DELETE FROM building WHERE dataset_id=? AND external_id=?", datasetId, externalId);
        }
    }

    private Map<String, Integer> toDeleteCounts(RestorePlan plan) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) counts.put(type, plan.toDelete().get(type).size());
        return counts;
    }

    private Map<String, Integer> keptCounts(RestorePlan plan) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : MapDataSupport.IMPORT_ORDER) counts.put(type, plan.keptBusiness().get(type).size());
        return counts;
    }

    private List<String> restoreWarnings(RestorePlan plan) {
        List<String> warnings = new ArrayList<>();
        warnings.add("恢复将把六类地图对象替换为备份快照；用户上报、评分、评论、建议和路线历史等业务数据不受影响");
        for (String type : MapDataSupport.IMPORT_ORDER) {
            List<String> kept = plan.keptBusiness().get(type);
            if (!kept.isEmpty()) {
                String sample = String.join("、", kept.stream().limit(5).toList());
                warnings.add(MapDataSupport.TYPE_LABELS.get(type) + " " + kept.size()
                        + " 个对象因被业务数据引用而保留：" + sample + (kept.size() > 5 ? " 等" : ""));
            }
        }
        return warnings;
    }

    private ImportAnalysis analyzeImport(DatasetView dataset, JsonNode root) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!"FeatureCollection".equals(root.path("type").asText())) errors.add("必须是 FeatureCollection");
        if (root.path("schemaVersion").asInt() != 2) errors.add("Formal 同步文件必须使用 schemaVersion=2");
        if (!dataset.code().equals(root.path("datasetCode").asText())) errors.add("datasetCode 与目标数据集不一致");
        if (!dataset.coordinateSystem().equals(root.path("coordinateSystem").asText())) {
            errors.add("坐标系必须为 " + dataset.coordinateSystem());
        }
        JsonNode features = root.path("features");
        if (!features.isArray()) errors.add("features 必须是数组");
        else if (features.size() > 2000) errors.add("features 最多允许 2000 项");

        JsonNode targetExport = exportService.exportGeoJson(dataset.id());
        Map<String, JsonNode> target = featureIndex(targetExport.path("features"));
        Map<String, JsonNode> incoming = new LinkedHashMap<>();
        if (features.isArray() && features.size() <= 2000) {
            int index = 0;
            for (JsonNode feature : features) {
                validateFeature(feature, index++, incoming, errors);
            }
        }
        validateReferences(incoming, target, errors);

        Map<String, MapDtos.ImportTypeSummary> summaries = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (String type : MapDataSupport.IMPORT_ORDER) {
            int creates = 0;
            int unchanged = 0;
            int changed = 0;
            for (Map.Entry<String, JsonNode> entry : incoming.entrySet()) {
                if (!entry.getKey().startsWith(type + ":")) continue;
                JsonNode existing = target.get(entry.getKey());
                if (existing == null) creates++;
                else if (existing.equals(entry.getValue())) unchanged++;
                else {
                    changed++;
                    if (conflicts.size() < 20) conflicts.add(entry.getKey());
                }
            }
            summaries.put(type, new MapDtos.ImportTypeSummary(creates, unchanged, changed));
        }
        warnings.add("本次为合并导入，文件中缺失的本地对象不会被删除");
        if (!conflicts.isEmpty()) warnings.add("存在同编号但内容不同的对象，应用时必须选择保留或覆盖");
        return new ImportAnalysis(fingerprint(targetExport), summaries, conflicts, errors, warnings, incoming);
    }

    private void validateFeature(
            JsonNode feature, int index, Map<String, JsonNode> incoming, List<String> errors) {
        JsonNode properties = feature.path("properties");
        String type = properties.path("entityType").asText();
        String externalId = properties.path("externalId").asText();
        String label = "features[" + index + "]";
        if (!MapDataSupport.IMPORT_TYPES.contains(type)) {
            errors.add(label + " entityType 不受支持");
            return;
        }
        if (externalId.isBlank() || externalId.length() > 64) {
            errors.add(label + " externalId 不能为空且最多 64 字符");
            return;
        }
        String key = type + ":" + externalId;
        if (incoming.putIfAbsent(key, feature) != null) errors.add("存在重复对象：" + key);
        String expectedGeometry = "BUILDING".equals(type) ? "Polygon" : "EDGE".equals(type) ? "LineString" : "Point";
        JsonNode geometry = feature.path("geometry");
        if (!expectedGeometry.equals(geometry.path("type").asText())
                || !validCoordinates(geometry.path("coordinates"))) {
            errors.add(key + " geometry 必须是有效的 " + expectedGeometry);
        }
        JsonNode coordinates = geometry.path("coordinates");
        if ("EDGE".equals(type) && (!coordinates.isArray() || coordinates.size() < 2)) {
            errors.add(key + " LineString 至少需要两个坐标");
        }
        if (!"NODE".equals(type) && !"EDGE".equals(type)
                && properties.path("name").asText().isBlank()) {
            errors.add(key + " name 不能为空");
        }
        validateProperties(type, key, properties, errors);
    }

    private void validateProperties(String type, String key, JsonNode properties, List<String> errors) {
        if (properties.has("dataSource")) validateAllowed(key, properties, "dataSource", errors,
                "DEMO_GENERATED", "PUBLIC_SOURCE", "MANUAL_ESTIMATE", "FIELD_VERIFIED", "UNVERIFIED");
        if (properties.has("confidenceLevel")) validateAllowed(key, properties, "confidenceLevel", errors,
                "HIGH", "MEDIUM", "LOW", "UNKNOWN");
        switch (type) {
            case "NODE" -> validateAllowed(key, properties, "nodeType", errors,
                    "INTERSECTION", "ENTRANCE", "WAYPOINT", "FACILITY_CONNECTOR");
            case "EDGE" -> {
                validateAllowed(key, properties, "slopeLevel", errors, "FLAT", "GENTLE", "MODERATE", "STEEP", "UNKNOWN");
                validateAllowed(key, properties, "widthLevel", errors, "NARROW", "STANDARD", "WIDE", "UNKNOWN");
                validateAllowed(key, properties, "surfaceType", errors, "ASPHALT", "CONCRETE", "BRICK", "GRAVEL", "DIRT", "UNKNOWN");
                validateAllowed(key, properties, "lightingLevel", errors, "NONE", "LOW", "MEDIUM", "HIGH", "UNKNOWN");
                validateAllowed(key, properties, "status", errors, "ACTIVE", "INACTIVE", "CLOSED", "BLOCKED");
                validateAllowed(key, properties, "riskLevel", errors, "LOW", "MEDIUM", "HIGH", "UNKNOWN");
                if (properties.path("fromNodeExternalId").asText().isBlank()
                        || properties.path("toNodeExternalId").asText().isBlank()) {
                    errors.add(key + " 必须提供道路起终点 externalId");
                }
            }
            case "ENTRANCE" -> validateAllowed(key, properties, "status", errors, "OPEN", "CLOSED", "UNKNOWN");
            case "FACILITY" -> {
                validateAllowed(key, properties, "facilityType", errors,
                        "ACCESSIBLE_ENTRANCE", "RAMP", "ELEVATOR", "ACCESSIBLE_TOILET", "REST_AREA",
                        "ACCESSIBLE_PARKING", "DROP_OFF_POINT", "TRANSIT_BOARDING_POINT");
                validateAllowed(key, properties, "openStatus", errors, "OPEN", "CLOSED", "UNKNOWN");
            }
            case "BARRIER" -> {
                validateAllowed(key, properties, "barrierType", errors,
                        "STAIRS", "CONSTRUCTION", "TEMPORARY_CLOSURE", "DAMAGED_SURFACE", "NARROW_PATH",
                        "VEHICLE_BLOCKING", "STEEP_SLOPE", "ELEVATOR_OUTAGE", "ENTRANCE_CLOSED", "WATERLOGGING");
                validateAllowed(key, properties, "reviewStatus", errors,
                        "PENDING", "NEEDS_VERIFICATION", "APPROVED", "REJECTED");
            }
            default -> {
                // BUILDING 只需要通用字段和 Polygon。
            }
        }
    }

    private void validateAllowed(
            String key, JsonNode properties, String field, List<String> errors, String... allowed) {
        String value = properties.path(field).asText();
        if (!Set.of(allowed).contains(value)) errors.add(key + " 的 " + field + " 不合法");
    }

    private void validateReferences(
            Map<String, JsonNode> incoming, Map<String, JsonNode> target, List<String> errors) {
        Set<String> nodes = referencedExternalIds("NODE", incoming, target);
        Set<String> buildings = referencedExternalIds("BUILDING", incoming, target);
        for (Map.Entry<String, JsonNode> entry : incoming.entrySet()) {
            JsonNode properties = entry.getValue().path("properties");
            if (entry.getKey().startsWith("EDGE:")) {
                for (String field : List.of("fromNodeExternalId", "toNodeExternalId")) {
                    String reference = properties.path(field).asText();
                    if (!nodes.contains(reference)) errors.add(entry.getKey() + " 引用了不存在的节点：" + reference);
                }
            }
            if (entry.getKey().startsWith("ENTRANCE:")
                    || entry.getKey().startsWith("FACILITY:") && properties.hasNonNull("buildingExternalId")) {
                String reference = properties.path("buildingExternalId").asText();
                if (!buildings.contains(reference)) errors.add(entry.getKey() + " 引用了不存在的建筑：" + reference);
            }
        }
    }

    private Set<String> referencedExternalIds(
            String type, Map<String, JsonNode> incoming, Map<String, JsonNode> target) {
        Set<String> values = new LinkedHashSet<>();
        for (String key : incoming.keySet()) if (key.startsWith(type + ":")) values.add(key.substring(type.length() + 1));
        for (String key : target.keySet()) if (key.startsWith(type + ":")) values.add(key.substring(type.length() + 1));
        return values;
    }

    private void upsertImportedFeature(UUID datasetId, JsonNode feature, String actor) {
        JsonNode properties = feature.path("properties");
        String type = properties.path("entityType").asText();
        String externalId = support.requiredText(properties, "externalId");
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        switch (type) {
            case "BUILDING" -> jdbc.update(
                    """
                    INSERT INTO building(id,dataset_id,external_id,name,category,active,data_source,confidence_level,geom)
                    VALUES (?,?,?,?,?,?,?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?),0))
                    ON CONFLICT(dataset_id,external_id) DO UPDATE SET name=EXCLUDED.name,
                      category=EXCLUDED.category,active=EXCLUDED.active,data_source=EXCLUDED.data_source,
                      confidence_level=EXCLUDED.confidence_level,geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                    """,
                    UUID.randomUUID(), datasetId, externalId, support.requiredText(properties, "name"),
                    properties.path("category").asText("OTHER"), properties.path("active").asBoolean(true),
                    importedDataSource(properties), importedConfidence(properties), feature.path("geometry").toString());
            case "NODE" -> {
                UUID nodeId = objectService.saveNode(datasetId, null, new NodeRequest(
                        externalId, support.nullableText(properties, "name"),
                        properties.path("nodeType").asText("INTERSECTION"),
                        properties.path("active").asBoolean(true), coordinate(coordinates)), actor);
                jdbc.update("UPDATE route_node SET data_source=?,confidence_level=? WHERE id=?",
                        importedDataSource(properties), importedConfidence(properties), nodeId);
            }
            case "ENTRANCE" -> {
                UUID buildingId = buildingIdByExternal(datasetId, support.requiredText(properties, "buildingExternalId"));
                Coordinate point = coordinate(coordinates);
                jdbc.update(
                        """
                        INSERT INTO building_entrance(id,dataset_id,building_id,external_id,name,accessible,
                          entrance_type,status,active,data_source,confidence_level,geom)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0))
                        ON CONFLICT(dataset_id,external_id) DO UPDATE SET building_id=EXCLUDED.building_id,
                          name=EXCLUDED.name,accessible=EXCLUDED.accessible,entrance_type=EXCLUDED.entrance_type,
                          status=EXCLUDED.status,active=EXCLUDED.active,geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                        """,
                        UUID.randomUUID(), datasetId, buildingId, externalId, support.requiredText(properties, "name"),
                        properties.path("accessible").asBoolean(false),
                        properties.path("entranceType").asText("MAIN"), properties.path("status").asText("UNKNOWN"),
                        properties.path("active").asBoolean(true), MapDataSupport.MANUAL, MapDataSupport.UNKNOWN,
                        point.lng(), point.lat());
            }
            case "FACILITY" -> {
                UUID buildingId = properties.hasNonNull("buildingExternalId")
                        ? buildingIdByExternal(datasetId, properties.path("buildingExternalId").asText()) : null;
                Coordinate point = coordinate(coordinates);
                jdbc.update(
                        """
                        INSERT INTO accessible_facility(id,dataset_id,building_id,external_id,name,facility_type,
                          floor_label,open_status,description,active,data_source,confidence_level,geom)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0))
                        ON CONFLICT(dataset_id,external_id) DO UPDATE SET building_id=EXCLUDED.building_id,
                          name=EXCLUDED.name,facility_type=EXCLUDED.facility_type,floor_label=EXCLUDED.floor_label,
                          open_status=EXCLUDED.open_status,description=EXCLUDED.description,active=EXCLUDED.active,
                          data_source=EXCLUDED.data_source,confidence_level=EXCLUDED.confidence_level,
                          geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                        """,
                        UUID.randomUUID(), datasetId, buildingId, externalId, support.requiredText(properties, "name"),
                        support.requiredText(properties, "facilityType"), support.nullableText(properties, "floorLabel"),
                        properties.path("openStatus").asText("UNKNOWN"), support.nullableText(properties, "description"),
                        properties.path("active").asBoolean(true), importedDataSource(properties),
                        importedConfidence(properties), point.lng(), point.lat());
            }
            case "EDGE" -> {
                UUID from = nodeIdByExternal(datasetId, support.requiredText(properties, "fromNodeExternalId"));
                UUID to = nodeIdByExternal(datasetId, support.requiredText(properties, "toNodeExternalId"));
                List<Coordinate> intermediate = new ArrayList<>();
                for (int index = 1; index < coordinates.size() - 1; index++) {
                    intermediate.add(coordinate(coordinates.get(index)));
                }
                EdgeRequest edge = new EdgeRequest(
                        externalId, support.nullableText(properties, "name"), from, to, BigDecimal.ONE,
                        properties.path("slopeLevel").asText("UNKNOWN"),
                        properties.path("hasStairs").asBoolean(false), properties.path("stairsCount").asInt(0),
                        properties.path("widthLevel").asText("UNKNOWN"),
                        properties.path("surfaceType").asText("UNKNOWN"),
                        properties.path("lightingLevel").asText("UNKNOWN"),
                        properties.path("bidirectional").asBoolean(true),
                        properties.path("status").asText("ACTIVE"),
                        properties.path("riskLevel").asText("UNKNOWN"), intermediate);
                UUID edgeId = objectService.saveEdge(datasetId, optionalEdgeId(datasetId, externalId), edge, actor);
                jdbc.update("UPDATE route_edge SET data_source=?,confidence_level=? WHERE id=?",
                        importedDataSource(properties), importedConfidence(properties), edgeId);
            }
            case "BARRIER" -> {
                Coordinate point = coordinate(coordinates);
                jdbc.update(
                        """
                        INSERT INTO barrier_report(id,dataset_id,external_id,title,barrier_type,description,
                          review_status,active,data_source,confidence_level,geom)
                        VALUES (?,?,?,?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),0))
                        ON CONFLICT(dataset_id,external_id) DO UPDATE SET title=EXCLUDED.title,
                          barrier_type=EXCLUDED.barrier_type,description=EXCLUDED.description,
                          review_status=EXCLUDED.review_status,active=EXCLUDED.active,
                          data_source=EXCLUDED.data_source,confidence_level=EXCLUDED.confidence_level,
                          geom=EXCLUDED.geom,updated_at=CURRENT_TIMESTAMP
                        """,
                        UUID.randomUUID(), datasetId, externalId, support.requiredText(properties, "name"),
                        support.requiredText(properties, "barrierType"), support.nullableText(properties, "description"),
                        properties.path("reviewStatus").asText("PENDING"), properties.path("active").asBoolean(false),
                        importedDataSource(properties), importedConfidence(properties), point.lng(), point.lat());
            }
            default -> throw support.badRequest("不支持的 entityType：" + type);
        }
    }

    private String importedDataSource(JsonNode properties) {
        String value = properties.path("dataSource").asText(MapDataSupport.MANUAL);
        return "USER_REPORT".equals(value) ? MapDataSupport.MANUAL : value;
    }

    private String importedConfidence(JsonNode properties) {
        return properties.path("confidenceLevel").asText(MapDataSupport.UNKNOWN);
    }

    private Coordinate coordinate(JsonNode coordinates) {
        return new Coordinate(coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
    }

    private UUID buildingIdByExternal(UUID datasetId, String externalId) {
        List<UUID> values = jdbc.query(
                "SELECT id FROM building WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        if (values.isEmpty()) throw support.badRequest("引用了不存在的建筑：" + externalId);
        return values.getFirst();
    }

    private Map<String, JsonNode> featureIndex(JsonNode features) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        if (!features.isArray()) return values;
        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            String type = properties.path("entityType").asText();
            String externalId = properties.path("externalId").asText();
            if (!type.isBlank() && !externalId.isBlank()) values.put(type + ":" + externalId, feature);
        }
        return values;
    }

    private boolean validCoordinates(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.isEmpty()) return false;
        if (coordinates.size() >= 2 && coordinates.get(0).isNumber() && coordinates.get(1).isNumber()) {
            double lng = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();
            return Double.isFinite(lng) && Double.isFinite(lat)
                    && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
        }
        for (JsonNode child : coordinates) if (!validCoordinates(child)) return false;
        return true;
    }

    private String fingerprint(JsonNode value) {
        JsonNode normalized = value.deepCopy();
        if (normalized instanceof com.fasterxml.jackson.databind.node.ObjectNode object) object.remove("exportedAt");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(normalized)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("无法计算 GeoJSON 指纹", exception);
        }
    }

    private void createOrUpdateImportedFacility(UUID datasetId, JsonNode props, JsonNode coordinate, String actor) {
        String externalId = support.requiredText(props, "externalId");
        FacilityRequest request = new FacilityRequest(
                null, externalId, support.requiredText(props, "name"), props.path("facilityType").asText(),
                support.nullableText(props, "floorLabel"), props.path("openStatus").asText("UNKNOWN"),
                support.nullableText(props, "description"), props.path("active").asBoolean(true),
                new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
        List<UUID> existing = jdbc.query(
                "SELECT id FROM accessible_facility WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        if (existing.isEmpty()) {
            objectService.createFacility(datasetId, request, actor);
        } else {
            jdbc.update(
                    """
                    UPDATE accessible_facility SET name=?,facility_type=?,floor_label=?,open_status=?,description=?,
                        active=?,geom=ST_SetSRID(ST_MakePoint(?,?),0),updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND dataset_id=?
                    """,
                    request.name(), request.facilityType(), request.floorLabel(), request.openStatus(),
                    request.description(), request.active(), request.coordinate().lng(), request.coordinate().lat(),
                    existing.getFirst(), datasetId);
        }
    }

    private JsonNode requirePoint(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        JsonNode coordinates = geometry.path("coordinates");
        if (!"Point".equals(geometry.path("type").asText()) || !coordinates.isArray()
                || coordinates.size() < 2 || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) {
            throw support.badRequest("NODE/FACILITY geometry 必须是 Point");
        }
        return coordinates;
    }

    private UUID nodeIdByExternal(UUID datasetId, String externalId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM route_node WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        if (ids.isEmpty()) throw support.badRequest("道路引用了不存在的节点：" + externalId);
        return ids.getFirst();
    }

    private UUID optionalEdgeId(UUID datasetId, String externalId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM route_edge WHERE dataset_id=? AND external_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), datasetId, externalId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private record BackupRow(
            UUID id,
            UUID datasetId,
            Instant createdAt,
            String actorUsername,
            String targetFingerprint,
            String payloadFingerprint,
            String conflictPolicy,
            JsonNode snapshot) {
    }

    private record RestorePlan(
            JsonNode snapshot,
            Map<String, Set<String>> snapshotKeys,
            Map<String, Set<String>> currentKeys,
            Map<String, List<String>> toDelete,
            Map<String, List<String>> keptBusiness) {
    }

    private record ImportAnalysis(
            String targetFingerprint,
            Map<String, MapDtos.ImportTypeSummary> summaries,
            List<String> conflictSamples,
            List<String> errors,
            List<String> warnings,
            Map<String, JsonNode> incoming) {
    }
}
