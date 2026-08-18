package cn.barrierfreecampus.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RouteGraph {
    private RouteGraph() {
    }

    record Point(double lng, double lat) {
    }

    record Node(UUID id, String externalId, String name, boolean active, String confidenceLevel, Point point) {
    }

    record Facility(
            UUID id,
            String name,
            String type,
            String openStatus,
            String confidenceLevel,
            Point point) {
    }

    record Barrier(
            UUID id,
            String title,
            String type,
            String confidenceLevel,
            boolean active) {
    }

    record Edge(
            UUID id,
            String externalId,
            String name,
            UUID fromNodeId,
            UUID toNodeId,
            double distanceM,
            String slopeLevel,
            boolean hasStairs,
            int stairsCount,
            String widthLevel,
            String surfaceType,
            String lightingLevel,
            boolean bidirectional,
            String status,
            String riskLevel,
            String confidenceLevel,
            List<Point> geometry,
            List<Facility> facilities,
            List<Barrier> barriers) {
    }

    record Arc(Edge edge, UUID fromNodeId, UUID toNodeId, boolean reversed) {
        List<Point> orientedGeometry() {
            if (!reversed) {
                return edge.geometry();
            }
            List<Point> reversedPoints = new ArrayList<>(edge.geometry());
            java.util.Collections.reverse(reversedPoints);
            return reversedPoints;
        }
    }

    record GraphData(
            Map<UUID, Node> nodes,
            List<Edge> edges,
            Map<UUID, List<Arc>> adjacency) {
        static GraphData of(List<Node> nodes, List<Edge> edges) {
            Map<UUID, Node> nodeMap = new HashMap<>();
            nodes.forEach(node -> nodeMap.put(node.id(), node));
            Map<UUID, List<Arc>> adjacency = new HashMap<>();
            nodeMap.keySet().forEach(id -> adjacency.put(id, new ArrayList<>()));
            for (Edge edge : edges) {
                adjacency.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>())
                        .add(new Arc(edge, edge.fromNodeId(), edge.toNodeId(), false));
                if (edge.bidirectional()) {
                    adjacency.computeIfAbsent(edge.toNodeId(), ignored -> new ArrayList<>())
                            .add(new Arc(edge, edge.toNodeId(), edge.fromNodeId(), true));
                }
            }
            return new GraphData(Map.copyOf(nodeMap), List.copyOf(edges), adjacency);
        }
    }
}
