package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.BarrierRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.BuildingRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.DatasetView;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.EntranceRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityRequest;
import static cn.barrierfreecampus.mapdata.MapDtos.ImportResult;
import static cn.barrierfreecampus.mapdata.MapDtos.MapSnapshot;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeRequest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 地图数据门面：数据集、快照、对象 CRUD 与 GeoJSON 导入/导出/备份恢复的统一入口。
 * 具体实现按领域拆分到 MapSnapshotService、MapObjectService、GeoJsonExportService 与
 * GeoJsonImportService，本类保持原有公开方法签名，Controller 调用不变。
 */
@Service
public class MapDataService {
    private final JdbcTemplate jdbc;
    private final DatasetMapper datasetMapper;
    private final MapDataSupport support;
    private final MapSnapshotService snapshotService;
    private final MapObjectService objectService;
    private final GeoJsonExportService exportService;
    private final GeoJsonImportService importService;

    public MapDataService(
            JdbcTemplate jdbc,
            DatasetMapper datasetMapper,
            MapDataSupport support,
            MapSnapshotService snapshotService,
            MapObjectService objectService,
            GeoJsonExportService exportService,
            GeoJsonImportService importService) {
        this.jdbc = jdbc;
        this.datasetMapper = datasetMapper;
        this.support = support;
        this.snapshotService = snapshotService;
        this.objectService = objectService;
        this.exportService = exportService;
        this.importService = importService;
    }

    public List<DatasetView> listDatasets(boolean includeDisabled) {
        String condition = includeDisabled ? "" : " WHERE d.enabled = TRUE";
        return jdbc.query(
                """
                SELECT d.id, d.code, d.name, d.dataset_type, d.coordinate_system, d.enabled, d.is_demo,
                       d.seed, d.description, c.center_lng, c.center_lat
                FROM dataset d JOIN campus c ON c.id = d.campus_id
                """ + condition + " ORDER BY d.enabled DESC, d.created_at DESC, d.name",
                (rs, row) -> new DatasetView(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("dataset_type"), rs.getString("coordinate_system"),
                        rs.getBoolean("enabled"), rs.getBoolean("is_demo"),
                        rs.getObject("seed", Long.class), rs.getString("description"),
                        rs.getDouble("center_lng"), rs.getDouble("center_lat")));
    }

    @Transactional
    public DatasetView setDatasetEnabled(UUID datasetId, boolean enabled, String actor) {
        DatasetEntity entity = datasetMapper.selectById(datasetId);
        if (entity == null) throw support.notFound("数据集不存在");
        if (datasetMapper.updateEnabled(datasetId, enabled) != 1) throw support.notFound("数据集不存在");
        support.audit(actor, "DATASET_STATUS_CHANGE", "DATASET", datasetId.toString());
        return support.requireDataset(datasetId, true);
    }

    public MapSnapshot snapshot(UUID datasetId, String bbox, boolean includeDisabled) {
        return snapshotService.snapshot(datasetId, bbox, includeDisabled);
    }

    public UUID saveNode(UUID datasetId, UUID id, NodeRequest request, String actor) {
        return objectService.saveNode(datasetId, id, request, actor);
    }

    public void deleteMapObject(String type, UUID datasetId, UUID id, String actor) {
        objectService.deleteMapObject(type, datasetId, id, actor);
    }

    public UUID saveEdge(UUID datasetId, UUID id, EdgeRequest request, String actor) {
        return objectService.saveEdge(datasetId, id, request, actor);
    }

    public UUID createBuilding(UUID datasetId, BuildingRequest request, String actor) {
        return objectService.createBuilding(datasetId, request, actor);
    }

    public UUID createEntrance(UUID datasetId, EntranceRequest request, String actor) {
        return objectService.createEntrance(datasetId, request, actor);
    }

    public UUID createFacility(UUID datasetId, FacilityRequest request, String actor) {
        return objectService.createFacility(datasetId, request, actor);
    }

    public UUID createBarrier(UUID datasetId, BarrierRequest request, String actor) {
        return objectService.createBarrier(datasetId, request, actor);
    }

    public JsonNode exportGeoJson(UUID datasetId) {
        return exportService.exportGeoJson(datasetId);
    }

    public ImportResult importGeoJson(UUID datasetId, JsonNode geoJson, String actor) {
        return importService.importGeoJson(datasetId, geoJson, actor);
    }

    public MapDtos.ImportPreview previewGeoJson(UUID datasetId, JsonNode root) {
        return importService.previewGeoJson(datasetId, root);
    }

    public MapDtos.ImportApplyResult applyGeoJson(
            UUID datasetId, MapDtos.ImportApplyRequest request, String actor) {
        return importService.applyGeoJson(datasetId, request, actor);
    }

    public List<MapDtos.GeoJsonBackupView> listGeoJsonBackups(UUID datasetId) {
        return importService.listGeoJsonBackups(datasetId);
    }

    public MapDtos.RestorePreview previewGeoJsonRestore(UUID datasetId, UUID backupId) {
        return importService.previewGeoJsonRestore(datasetId, backupId);
    }

    public MapDtos.RestoreResult restoreGeoJsonBackup(
            UUID datasetId, UUID backupId, MapDtos.RestoreApplyRequest request, String actor) {
        return importService.restoreGeoJsonBackup(datasetId, backupId, request, actor);
    }
}
