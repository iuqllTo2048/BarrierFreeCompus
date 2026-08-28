package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class YenTopKRouterTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final AStarRouter aStar = new AStarRouter(new RouteCostPolicy());
    private final YenTopKRouter yen = new YenTopKRouter(aStar);

    @Test
    void shouldReturnThreeDifferentLooplessRoutesInWeightedOrder() {
        RouteGraph.GraphData graph = RouteGraph.GraphData.of(
                List.of(node(A, 112.0000, 28.0000), node(B, 112.0020, 28.0000),
                        node(C, 112.0010, 28.0005), node(D, 112.0010, 27.9995)),
                List.of(edge("direct", A, B, 100), edge("upper-1", A, C, 60),
                        edge("upper-2", C, B, 60), edge("lower-1", A, D, 70),
                        edge("lower-2", D, B, 70)));

        List<AStarRouter.SearchOutcome> routes = yen.search(
                graph, A, B, RouteProfile.SHORTEST, MobilityMode.WALKING, TravelPeriod.DAY,
                RoutePreferences.defaults(), false, 3);

        assertThat(routes).hasSize(3);
        assertThat(routes).extracting(route -> route.costBreakdown().total())
                .containsExactly(100.0, 120.0, 140.0);
        assertThat(routes).extracting(route -> route.path().stream()
                        .map(arc -> arc.edge().externalId()).toList())
                .containsExactly(
                        List.of("direct"),
                        List.of("upper-1", "upper-2"),
                        List.of("lower-1", "lower-2"));
        assertThat(routes).allSatisfy(route -> assertThat(nodeIds(route.path())).doesNotHaveDuplicates());
    }

    @Test
    void shouldReturnOnlyRealRoutesWhenGraphHasNoAlternative() {
        RouteGraph.GraphData graph = RouteGraph.GraphData.of(
                List.of(node(A, 112.0000, 28.0000), node(B, 112.0020, 28.0000)),
                List.of(edge("only", A, B, 100)));

        assertThat(yen.search(graph, A, B, RouteProfile.ACCESSIBLE, MobilityMode.WALKING,
                TravelPeriod.DAY, RoutePreferences.defaults(), false, 3)).hasSize(1);
    }

    private List<UUID> nodeIds(List<RouteGraph.Arc> path) {
        java.util.ArrayList<UUID> result = new java.util.ArrayList<>();
        if (!path.isEmpty()) result.add(path.getFirst().fromNodeId());
        path.forEach(arc -> result.add(arc.toNodeId()));
        return result;
    }

    private RouteGraph.Node node(UUID id, double lng, double lat) {
        return new RouteGraph.Node(id, id.toString(), id.toString(), true, "HIGH",
                new RouteGraph.Point(lng, lat));
    }

    private RouteGraph.Edge edge(String name, UUID from, UUID to, double distance) {
        return new RouteGraph.Edge(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, name,
                from, to, distance, "FLAT", false, 0, "WIDE", "ASPHALT", "HIGH",
                true, "ACTIVE", "LOW", "HIGH", List.of(point(from), point(to)), List.of(), List.of());
    }

    private RouteGraph.Point point(UUID id) {
        if (id.equals(A)) return new RouteGraph.Point(112.0000, 28.0000);
        if (id.equals(B)) return new RouteGraph.Point(112.0020, 28.0000);
        if (id.equals(C)) return new RouteGraph.Point(112.0010, 28.0005);
        return new RouteGraph.Point(112.0010, 27.9995);
    }
}
