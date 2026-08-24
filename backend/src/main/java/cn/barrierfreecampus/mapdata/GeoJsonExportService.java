package cn.barrierfreecampus.mapdata;

import static cn.barrierfreecampus.mapdata.MapDtos.BuildingView;
import static cn.barrierfreecampus.mapdata.MapDtos.BarrierView;
import static cn.barrierfreecampus.mapdata.MapDtos.Coordinate;
import static cn.barrierfreecampus.mapdata.MapDtos.EdgeView;
import static cn.barrierfreecampus.mapdata.MapDtos.EntranceView;
import static cn.barrierfreecampus.mapdata.MapDtos.FacilityView;
import static cn.barrierfreecampus.mapdata.MapDtos.MapSnapshot;
import static cn.barrierfreecampus.mapdata.MapDtos.NodeView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * GeoJSON v2 导出。
 */
@Component
public class GeoJsonExportService {
    private final ObjectMapper objectMapper;
    private final MapDataSupport support;
    private final MapSnapshotService snapshotService;

    public GeoJsonExportService(ObjectMapper objectMapper, MapDataSupport support,
                                MapSnapshotService snapshotService) {
        this.objectMapper = objectMapper;
        this.support = support;
        this.snapshotService = snapshotService;
    }

    public JsonNode exportGeoJson(UUID datasetId) {
        MapSnapshot snapshot = snapshotService.snapshot(datasetId, null, true);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "FeatureCollection");
        root.put("schemaVersion", 2);
        root.put("datasetId", datasetId.toString());
        root.put("datasetCode", snapshot.dataset().code());
        root.put("coordinateSystem", snapshot.dataset().coordinateSystem());
        root.put("exportedAt", Instant.now().toString());
        ArrayNode features = root.putArray("features");
        for (BuildingView building : snapshot.buildings()) {
            ObjectNode feature = feature(features, building.geometry(), "BUILDING", building.externalId(), building.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("category", building.category());
            properties.put("active", building.active());
            properties.put("dataSource", building.dataSource());
            properties.put("confidenceLevel", building.confidenceLevel());
        }
        for (EntranceView entrance : snapshot.entrances()) {
            ObjectNode feature = feature(features, support.point(entrance.lng(), entrance.lat()), "ENTRANCE",
                    entrance.externalId(), entrance.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("buildingExternalId", externalBuildingId(snapshot.buildings(), entrance.buildingId()));
            properties.put("accessible", entrance.accessible());
            properties.put("entranceType", entrance.entranceType());
            properties.put("status", entrance.status());
            properties.put("active", entrance.active());
        }
        for (NodeView node : snapshot.nodes()) {
            ObjectNode feature = feature(features, support.point(node.lng(), node.lat()), "NODE",
                    node.externalId(), node.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("nodeType", node.nodeType());
            properties.put("active", node.active());
            properties.put("dataSource", node.dataSource());
            properties.put("confidenceLevel", node.confidenceLevel());
        }
        for (EdgeView edge : snapshot.edges()) {
            ObjectNode feature = feature(features, edge.geometry(), "EDGE", edge.externalId(), edge.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("fromNodeExternalId", externalNodeId(snapshot.nodes(), edge.fromNodeId()));
            properties.put("toNodeExternalId", externalNodeId(snapshot.nodes(), edge.toNodeId()));
            properties.put("distanceM", edge.distanceM());
            properties.put("slopeLevel", edge.slopeLevel());
            properties.put("hasStairs", edge.hasStairs());
            properties.put("stairsCount", edge.stairsCount());
            properties.put("widthLevel", edge.widthLevel());
            properties.put("surfaceType", edge.surfaceType());
            properties.put("lightingLevel", edge.lightingLevel());
            properties.put("bidirectional", edge.bidirectional());
            properties.put("status", edge.status());
            properties.put("riskLevel", edge.riskLevel());
            properties.put("dataSource", edge.dataSource());
            properties.put("confidenceLevel", edge.confidenceLevel());
        }
        for (FacilityView facility : snapshot.facilities()) {
            ObjectNode feature = feature(features, support.point(facility.lng(), facility.lat()), "FACILITY",
                    facility.externalId(), facility.name());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("facilityType", facility.facilityType());
            if (facility.buildingId() != null) {
                properties.put("buildingExternalId", externalBuildingId(snapshot.buildings(), facility.buildingId()));
            }
            properties.put("floorLabel", facility.floorLabel());
            properties.put("openStatus", facility.openStatus());
            properties.put("description", facility.description());
            properties.put("active", facility.active());
            properties.put("dataSource", facility.dataSource());
            properties.put("confidenceLevel", facility.confidenceLevel());
        }
        for (BarrierView barrier : snapshot.barriers()) {
            if ("USER_REPORT".equals(barrier.dataSource())) continue;
            ObjectNode feature = feature(features, barrier.geometry(), "BARRIER", barrier.externalId(), barrier.title());
            ObjectNode properties = (ObjectNode) feature.get("properties");
            properties.put("barrierType", barrier.barrierType());
            properties.put("description", barrier.description());
            properties.put("reviewStatus", barrier.reviewStatus());
            properties.put("active", barrier.active());
            properties.put("dataSource", barrier.dataSource());
            properties.put("confidenceLevel", barrier.confidenceLevel());
        }
        return root;
    }

    private ObjectNode feature(ArrayNode features, JsonNode geometry, String entityType, String externalId, String name) {
        ObjectNode feature = features.addObject();
        feature.put("type", "Feature");
        feature.set("geometry", geometry);
        ObjectNode properties = feature.putObject("properties");
        properties.put("entityType", entityType);
        properties.put("externalId", externalId);
        properties.put("name", name);
        return feature;
    }

    private String externalNodeId(List<NodeView> nodes, UUID id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst().map(NodeView::externalId)
                .orElseThrow(() -> new IllegalStateException("道路引用节点缺失"));
    }

    private String externalBuildingId(List<BuildingView> buildings, UUID id) {
        return buildings.stream().filter(building -> building.id().equals(id)).findFirst()
                .map(BuildingView::externalId)
                .orElseThrow(() -> new IllegalStateException("对象引用建筑缺失"));
    }
}
