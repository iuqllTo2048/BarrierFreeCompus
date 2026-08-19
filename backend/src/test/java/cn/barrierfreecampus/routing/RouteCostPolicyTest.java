package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.CART_LUGGAGE;
import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.CRUTCH;
import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.TEMPORARY_INJURY;
import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.WALKING;
import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode.WHEELCHAIR;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile.ACCESSIBLE;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile.BALANCED;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile.SHORTEST;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod.DAY;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod.NIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class RouteCostPolicyTest {
    private static final UUID A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private final RouteCostPolicy policy = new RouteCostPolicy();

    @ParameterizedTest
    @EnumSource(RoutingDtos.MobilityMode.class)
    void allFiveMobilityModesCanUseOrdinaryFlatRoad(RoutingDtos.MobilityMode mode) {
        RouteCostPolicy.CostEvaluation result = evaluate(edge("ordinary", false, "FLAT", "WIDE", "ASPHALT",
                "HIGH", "ACTIVE", "LOW", "HIGH", List.of()), SHORTEST, mode, DAY);

        assertThat(result.allowed()).isTrue();
        assertThat(result.breakdown().total()).isEqualTo(100);
    }

    @Test
    void stairCostIncreasesByMobilityDifficultyAndBlocksWheelchair() {
        RouteGraph.Edge stairs = edge("stairs", true, "FLAT", "WIDE", "ASPHALT",
                "HIGH", "ACTIVE", "LOW", "HIGH", List.of());

        double walking = evaluate(stairs, SHORTEST, WALKING, DAY).breakdown().stairs();
        double crutch = evaluate(stairs, SHORTEST, CRUTCH, DAY).breakdown().stairs();
        double injury = evaluate(stairs, SHORTEST, TEMPORARY_INJURY, DAY).breakdown().stairs();
        double cart = evaluate(stairs, SHORTEST, CART_LUGGAGE, DAY).breakdown().stairs();

        assertThat(crutch).isEqualTo(walking * 2);
        assertThat(injury).isEqualTo(crutch);
        assertThat(cart).isEqualTo(walking * 3);
        assertThat(evaluate(stairs, SHORTEST, WHEELCHAIR, DAY).allowed()).isFalse();
    }

    @Test
    void accessibleProfilePenalizesSteepNarrowGravelRoadMost() {
        RouteGraph.Edge difficult = edge("difficult", false, "STEEP", "NARROW", "GRAVEL",
                "HIGH", "ACTIVE", "LOW", "HIGH", List.of());

        double shortest = evaluate(difficult, SHORTEST, WALKING, DAY).breakdown().total();
        double balanced = evaluate(difficult, BALANCED, WALKING, DAY).breakdown().total();
        double accessible = evaluate(difficult, ACCESSIBLE, WALKING, DAY).breakdown().total();

        assertThat(shortest).isLessThan(balanced);
        assertThat(balanced).isLessThan(accessible);
    }

    @Test
    void nightAddsLightingRiskWhileDayDoesNot() {
        RouteGraph.Edge dark = edge("dark", false, "FLAT", "WIDE", "ASPHALT",
                "NONE", "ACTIVE", "LOW", "HIGH", List.of());

        assertThat(evaluate(dark, ACCESSIBLE, WALKING, DAY).breakdown().lighting()).isZero();
        assertThat(evaluate(dark, ACCESSIBLE, WALKING, NIGHT).breakdown().lighting()).isPositive();
    }

    @Test
    void unknownDataProducesSlopeWidthSurfaceLightingRiskAndConfidenceCosts() {
        RouteGraph.Edge unknown = edge("unknown", false, "UNKNOWN", "UNKNOWN", "UNKNOWN",
                "UNKNOWN", "ACTIVE", "UNKNOWN", "UNKNOWN", List.of());
        RoutingDtos.CostBreakdown cost = evaluate(unknown, ACCESSIBLE, WALKING, NIGHT).breakdown();

        assertThat(cost.slope()).isPositive();
        assertThat(cost.width()).isPositive();
        assertThat(cost.surface()).isPositive();
        assertThat(cost.lighting()).isPositive();
        assertThat(cost.barrier()).isPositive();
        assertThat(cost.uncertainty()).isPositive();
    }

    @ParameterizedTest
    @MethodSource("hardBlockingBarriers")
    void allHardBlockingBarrierTypesRejectEdge(String type) {
        RouteGraph.Barrier barrier = barrier(type, true);
        RouteCostPolicy.CostEvaluation result = evaluate(edge("blocked-" + type, false, "FLAT", "WIDE",
                "ASPHALT", "HIGH", "ACTIVE", "LOW", "HIGH", List.of(barrier)), ACCESSIBLE, WALKING, DAY);

        assertThat(result.allowed()).isFalse();
        assertThat(result.blockedReason()).contains("阻断");
    }

    @ParameterizedTest
    @MethodSource("softBarriers")
    void softBarriersIncreaseCostButRemainPassable(String type) {
        RouteGraph.Barrier barrier = barrier(type, true);
        RouteCostPolicy.CostEvaluation result = evaluate(edge("soft-" + type, false, "FLAT", "WIDE",
                "ASPHALT", "HIGH", "ACTIVE", "LOW", "HIGH", List.of(barrier)), ACCESSIBLE, CRUTCH, DAY);

        assertThat(result.allowed()).isTrue();
        assertThat(result.breakdown().barrier()).isPositive();
    }

    @Test
    void inactiveBarrierIsAlreadyFilteredByGraphLoaderAndDoesNotBlock() {
        RouteGraph.Barrier inactive = barrier("TEMPORARY_CLOSURE", false);
        RouteCostPolicy.CostEvaluation result = evaluate(edge("inactive", false, "FLAT", "WIDE", "ASPHALT",
                "HIGH", "ACTIVE", "LOW", "HIGH", List.of()), ACCESSIBLE, WALKING, DAY);

        assertThat(inactive.active()).isFalse();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void closedEdgeIsRejectedRegardlessOfProfile() {
        RouteGraph.Edge closed = edge("closed", false, "FLAT", "WIDE", "ASPHALT",
                "HIGH", "BLOCKED", "LOW", "HIGH", List.of());

        assertThat(evaluate(closed, SHORTEST, WALKING, DAY).allowed()).isFalse();
        assertThat(evaluate(closed, ACCESSIBLE, WALKING, DAY).allowed()).isFalse();
    }

    @Test
    void customWeightsOnlyScaleTheirOwnCostDimensions() {
        RouteGraph.Edge edge = edge("weighted", false, "MODERATE", "NARROW", "ASPHALT",
                "HIGH", "ACTIVE", "LOW", "HIGH", List.of());
        RoutingDtos.RoutePreferences base = RoutingDtos.RoutePreferences.defaults();
        RoutingDtos.RoutePreferences weighted = new RoutingDtos.RoutePreferences(false, 2.0, 0.5, 2.0, null, null);

        RoutingDtos.CostBreakdown original = policy.evaluate(arc(edge), ACCESSIBLE, WALKING, DAY, base, false).breakdown();
        RoutingDtos.CostBreakdown changed = policy.evaluate(arc(edge), ACCESSIBLE, WALKING, DAY, weighted, false).breakdown();

        assertThat(changed.distance()).isEqualTo(original.distance() * 2);
        assertThat(changed.slope()).isEqualTo(original.slope() * 0.5);
        assertThat(changed.width()).isEqualTo(original.width() * 2);
    }

    @Test
    void heuristicIsZeroForSamePointAndPositiveOtherwise() {
        RouteGraph.Node first = node(A, 112.0, 28.0);
        RouteGraph.Node same = node(B, 112.0, 28.0);
        RouteGraph.Node nearby = node(B, 112.001, 28.0);

        assertThat(policy.heuristic(first, same, RoutingDtos.RoutePreferences.defaults())).isZero();
        assertThat(policy.heuristic(first, nearby, RoutingDtos.RoutePreferences.defaults())).isPositive();
    }

    private RouteCostPolicy.CostEvaluation evaluate(
            RouteGraph.Edge edge,
            RoutingDtos.RouteProfile profile,
            RoutingDtos.MobilityMode mode,
            RoutingDtos.TravelPeriod period) {
        return policy.evaluate(arc(edge), profile, mode, period, RoutingDtos.RoutePreferences.defaults(), false);
    }

    private RouteGraph.Arc arc(RouteGraph.Edge edge) {
        return new RouteGraph.Arc(edge, A, B, false);
    }

    private RouteGraph.Node node(UUID id, double lng, double lat) {
        return new RouteGraph.Node(id, id.toString(), id.toString(), true, "HIGH", new RouteGraph.Point(lng, lat));
    }

    private RouteGraph.Edge edge(
            String name,
            boolean stairs,
            String slope,
            String width,
            String surface,
            String lighting,
            String status,
            String risk,
            String confidence,
            List<RouteGraph.Barrier> barriers) {
        return new RouteGraph.Edge(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, name, A, B, 100,
                slope, stairs, stairs ? 10 : 0, width, surface, lighting, true, status, risk, confidence,
                List.of(new RouteGraph.Point(112.0, 28.0), new RouteGraph.Point(112.001, 28.0)),
                List.of(), barriers);
    }

    private RouteGraph.Barrier barrier(String type, boolean active) {
        return new RouteGraph.Barrier(
                UUID.nameUUIDFromBytes(type.getBytes(StandardCharsets.UTF_8)), type, type, "MEDIUM", active);
    }

    private static Stream<Arguments> hardBlockingBarriers() {
        return Stream.of("TEMPORARY_CLOSURE", "CONSTRUCTION", "VEHICLE_BLOCKING", "ENTRANCE_CLOSED")
                .map(Arguments::of);
    }

    private static Stream<Arguments> softBarriers() {
        return Stream.of("STAIRS", "STEEP_SLOPE", "WATERLOGGING", "DAMAGED_SURFACE", "NARROW_PATH")
                .map(Arguments::of);
    }
}
