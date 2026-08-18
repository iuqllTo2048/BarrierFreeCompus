package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.WALKING;
import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.WHEELCHAIR;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile.ACCESSIBLE;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile.SHORTEST;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod.DAY;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod.NIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AStarRouterTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final AStarRouter router = new AStarRouter(new RouteCostPolicy());

    @Test
    void shortestMayUseStairsWhileAccessibleAndWheelchairDetour() {
        RouteGraph.GraphData graph = graphWithStairShortcut(true);

        AStarRouter.SearchOutcome shortest = router.search(
                graph, A, B, SHORTEST, WALKING, DAY, RoutingDtos.RoutePreferences.defaults(), false);
        AStarRouter.SearchOutcome accessible = router.search(
                graph, A, B, ACCESSIBLE, WALKING, DAY, RoutingDtos.RoutePreferences.defaults(), false);
        AStarRouter.SearchOutcome wheelchair = router.search(
                graph, A, B, SHORTEST, WHEELCHAIR, DAY, RoutingDtos.RoutePreferences.defaults(), false);

        assertThat(shortest.found()).isTrue();
        assertThat(shortest.path()).hasSize(1);
        assertThat(shortest.path().getFirst().edge().hasStairs()).isTrue();
        assertThat(accessible.path()).hasSize(3).allMatch(arc -> !arc.edge().hasStairs());
        assertThat(wheelchair.path()).hasSize(3).allMatch(arc -> !arc.edge().hasStairs());
    }

    @Test
    void avoidStairsCanBeRelaxedButWheelchairConstraintCannot() {
        RouteGraph.GraphData graph = RouteGraph.GraphData.of(
                List.of(node(A, 112.0, 28.0), node(B, 112.001, 28.0)),
                List.of(edge("stairs-only", A, B, 100, true, true, "ACTIVE", "MODERATE", "HIGH", "MEDIUM")));
        RoutingDtos.RoutePreferences avoidStairs = new RoutingDtos.RoutePreferences(
                true, 1.0, 1.0, 1.0, null, null);

        assertThat(router.search(graph, A, B, SHORTEST, WALKING, DAY, avoidStairs, false).found()).isFalse();
        assertThat(router.search(graph, A, B, SHORTEST, WALKING, DAY, avoidStairs, true).found()).isTrue();
        assertThat(router.search(graph, A, B, SHORTEST, WHEELCHAIR, DAY, avoidStairs, true).found()).isFalse();
    }

    @Test
    void respectsOneWayBlockedAndUnknownNightCost() {
        RouteGraph.GraphData oneWay = graphWithStairShortcut(false);
        assertThat(router.search(
                        oneWay, B, A, SHORTEST, WALKING, DAY, RoutingDtos.RoutePreferences.defaults(), false)
                .path())
                .hasSize(3);

        RouteGraph.GraphData blocked = RouteGraph.GraphData.of(
                List.of(node(A, 112.0, 28.0), node(B, 112.001, 28.0)),
                List.of(edge("blocked", A, B, 100, false, true, "BLOCKED", "UNKNOWN", "UNKNOWN", "UNKNOWN")));
        assertThat(router.search(
                        blocked, A, B, SHORTEST, WALKING, DAY, RoutingDtos.RoutePreferences.defaults(), false)
                .found())
                .isFalse();

        RouteGraph.Edge unknownEdge = edge(
                "unknown", A, B, 100, false, true, "ACTIVE", "UNKNOWN", "UNKNOWN", "UNKNOWN");
        RouteGraph.GraphData unknown = RouteGraph.GraphData.of(
                List.of(node(A, 112.0, 28.0), node(B, 112.001, 28.0)), List.of(unknownEdge));
        AStarRouter.SearchOutcome result = router.search(
                unknown, A, B, ACCESSIBLE, WALKING, NIGHT, RoutingDtos.RoutePreferences.defaults(), false);
        assertThat(result.costBreakdown().slope()).isPositive();
        assertThat(result.costBreakdown().lighting()).isPositive();
        assertThat(result.costBreakdown().uncertainty()).isPositive();
    }

    @Test
    void sameNodeReturnsZeroCostRoute() {
        RouteGraph.GraphData graph = graphWithStairShortcut(true);
        AStarRouter.SearchOutcome result = router.search(
                graph, A, A, SHORTEST, WALKING, DAY, RoutingDtos.RoutePreferences.defaults(), false);

        assertThat(result.found()).isTrue();
        assertThat(result.path()).isEmpty();
        assertThat(result.costBreakdown().total()).isZero();
    }

    @Test
    void boundedFacilityPreferenceCanSelectSlightlyLongerUsefulRoute() {
        RouteGraph.Edge plain = edge(
                "plain", A, B, 100, false, true, "ACTIVE", "FLAT", "LOW", "HIGH");
        RouteGraph.Facility restArea = new RouteGraph.Facility(
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                "休息点", "REST_AREA", "OPEN", "MEDIUM", nodePoint(A));
        RouteGraph.Edge withRestArea = new RouteGraph.Edge(
                UUID.nameUUIDFromBytes("with-rest".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "with-rest", "with-rest", A, B, 105, "FLAT", false, 0, "WIDE", "ASPHALT", "HIGH",
                true, "ACTIVE", "LOW", "HIGH", List.of(nodePoint(A), nodePoint(B)), List.of(restArea), List.of());
        RouteGraph.GraphData graph = RouteGraph.GraphData.of(
                List.of(node(A, 112.0, 28.0), node(B, 112.001, 28.0)), List.of(plain, withRestArea));

        AStarRouter.SearchOutcome defaultRoute = router.search(
                graph, A, B, SHORTEST, WALKING, DAY, RoutingDtos.RoutePreferences.defaults(), false);
        RoutingDtos.RoutePreferences preferRest = new RoutingDtos.RoutePreferences(
                false, 1.0, 1.0, 1.0, 2.0, null);
        AStarRouter.SearchOutcome preferredRoute = router.search(
                graph, A, B, SHORTEST, WALKING, DAY, preferRest, false);

        assertThat(defaultRoute.path().getFirst().edge().externalId()).isEqualTo("plain");
        assertThat(preferredRoute.path().getFirst().edge().externalId()).isEqualTo("with-rest");
    }

    private RouteGraph.GraphData graphWithStairShortcut(boolean shortcutBidirectional) {
        return RouteGraph.GraphData.of(
                List.of(
                        node(A, 112.0000, 28.0000),
                        node(B, 112.0010, 28.0000),
                        node(C, 112.0000, 28.0010),
                        node(D, 112.0010, 28.0010)),
                List.of(
                        edge("shortcut", A, B, 100, true, shortcutBidirectional, "ACTIVE", "MODERATE", "HIGH", "HIGH"),
                        edge("detour-1", A, C, 90, false, true, "ACTIVE", "FLAT", "LOW", "HIGH"),
                        edge("detour-2", C, D, 90, false, true, "ACTIVE", "FLAT", "LOW", "HIGH"),
                        edge("detour-3", D, B, 90, false, true, "ACTIVE", "FLAT", "LOW", "HIGH")));
    }

    private RouteGraph.Node node(UUID id, double lng, double lat) {
        return new RouteGraph.Node(id, id.toString(), id.toString(), true, "HIGH", new RouteGraph.Point(lng, lat));
    }

    private RouteGraph.Edge edge(
            String name,
            UUID from,
            UUID to,
            double distance,
            boolean stairs,
            boolean bidirectional,
            String status,
            String slope,
            String risk,
            String confidence) {
        return new RouteGraph.Edge(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name,
                name,
                from,
                to,
                distance,
                slope,
                stairs,
                stairs ? 12 : 0,
                stairs ? "NARROW" : "WIDE",
                "ASPHALT",
                "UNKNOWN".equals(slope) ? "NONE" : "HIGH",
                bidirectional,
                status,
                risk,
                confidence,
                List.of(nodePoint(from), nodePoint(to)),
                List.of(),
                List.of());
    }

    private RouteGraph.Point nodePoint(UUID id) {
        return switch (id.toString().charAt(id.toString().length() - 1)) {
            case '1' -> new RouteGraph.Point(112.0000, 28.0000);
            case '2' -> new RouteGraph.Point(112.0010, 28.0000);
            case '3' -> new RouteGraph.Point(112.0000, 28.0010);
            default -> new RouteGraph.Point(112.0010, 28.0010);
        };
    }
}
