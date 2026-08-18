package cn.barrierfreecampus.mapdata;

import cn.barrierfreecampus.common.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/map")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class MapDataController {
    private final MapDataService mapDataService;

    public MapDataController(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @GetMapping("/datasets")
    public ApiResponse<List<MapDtos.DatasetView>> datasets() {
        return ApiResponse.ok(mapDataService.listDatasets(false));
    }

    @GetMapping("/datasets/{datasetId}/snapshot")
    public ApiResponse<MapDtos.MapSnapshot> snapshot(
            @PathVariable UUID datasetId,
            @RequestParam(required = false) String bbox) {
        return ApiResponse.ok(mapDataService.snapshot(datasetId, bbox, false));
    }
}
