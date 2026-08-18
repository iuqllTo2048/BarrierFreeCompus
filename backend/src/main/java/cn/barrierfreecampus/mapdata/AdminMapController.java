package cn.barrierfreecampus.mapdata;

import cn.barrierfreecampus.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/map")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMapController {
    private final MapDataService mapDataService;

    public AdminMapController(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @GetMapping("/datasets")
    public ApiResponse<List<MapDtos.DatasetView>> datasets() {
        return ApiResponse.ok(mapDataService.listDatasets(true));
    }

    @GetMapping("/datasets/{datasetId}/snapshot")
    public ApiResponse<MapDtos.MapSnapshot> snapshot(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) String bbox) {
        return ApiResponse.ok(mapDataService.snapshot(datasetId, bbox, true));
    }

    @PatchMapping("/datasets/{datasetId}")
    public ApiResponse<MapDtos.DatasetView> datasetStatus(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.DatasetStatusRequest request,
            Authentication authentication) {
        return ApiResponse.ok(mapDataService.setDatasetEnabled(datasetId, request.enabled(), authentication.getName()));
    }

    @PostMapping("/datasets/{datasetId}/nodes")
    public ApiResponse<Map<String, UUID>> createNode(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.NodeRequest request,
            Authentication authentication) {
        UUID id = mapDataService.saveNode(datasetId, null, request, authentication.getName());
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/datasets/{datasetId}/nodes/{id}")
    public ApiResponse<Map<String, UUID>> updateNode(
            @PathVariable UUID datasetId,
            @PathVariable UUID id,
            @Valid @RequestBody MapDtos.NodeRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.saveNode(datasetId, id, request, authentication.getName())));
    }

    @PostMapping("/datasets/{datasetId}/edges")
    public ApiResponse<Map<String, UUID>> createEdge(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.EdgeRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.saveEdge(datasetId, null, request, authentication.getName())));
    }

    @PutMapping("/datasets/{datasetId}/edges/{id}")
    public ApiResponse<Map<String, UUID>> updateEdge(
            @PathVariable UUID datasetId,
            @PathVariable UUID id,
            @Valid @RequestBody MapDtos.EdgeRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.saveEdge(datasetId, id, request, authentication.getName())));
    }

    @PostMapping("/datasets/{datasetId}/buildings")
    public ApiResponse<Map<String, UUID>> createBuilding(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.BuildingRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.createBuilding(datasetId, request, authentication.getName())));
    }

    @PostMapping("/datasets/{datasetId}/entrances")
    public ApiResponse<Map<String, UUID>> createEntrance(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.EntranceRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.createEntrance(datasetId, request, authentication.getName())));
    }

    @PostMapping("/datasets/{datasetId}/facilities")
    public ApiResponse<Map<String, UUID>> createFacility(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.FacilityRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.createFacility(datasetId, request, authentication.getName())));
    }

    @PostMapping("/datasets/{datasetId}/barriers")
    public ApiResponse<Map<String, UUID>> createBarrier(
            @PathVariable UUID datasetId,
            @Valid @RequestBody MapDtos.BarrierRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", mapDataService.createBarrier(datasetId, request, authentication.getName())));
    }

    @GetMapping("/datasets/{datasetId}/geojson")
    public ApiResponse<JsonNode> exportGeoJson(@PathVariable UUID datasetId) {
        return ApiResponse.ok(mapDataService.exportGeoJson(datasetId));
    }

    @PostMapping("/datasets/{datasetId}/geojson")
    public ApiResponse<MapDtos.ImportResult> importGeoJson(
            @PathVariable UUID datasetId,
            @RequestBody JsonNode geoJson,
            Authentication authentication) {
        return ApiResponse.ok(mapDataService.importGeoJson(datasetId, geoJson, authentication.getName()));
    }
}
