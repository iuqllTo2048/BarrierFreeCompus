package cn.barrierfreecampus.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AStarPerformanceTest {
    private final AStarRouter router = new AStarRouter(new RouteCostPolicy());

    @Test
    void recordsStableP95OnFourHundredNodeGrid() {
        int side = 20;
        RouteGraph.GraphData graph = grid(side);
        UUID start = id(0);
        UUID end = id(side * side - 1);

        for (int index = 0; index < 20; index++) {
            assertThat(search(graph, start, end).found()).isTrue();
        }

        long[] samples = new long[100];
        for (int index = 0; index < samples.length; index++) {
            AStarRouter.SearchOutcome outcome = search(graph, start, end);
            assertThat(outcome.found()).isTrue();
            assertThat(outcome.path()).hasSize((side - 1) * 2);
            samples[index] = outcome.metrics().elapsedMicros();
        }
        Arrays.sort(samples);
        long p50 = samples[samples.length / 2];
        long p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1];
        long max = samples[samples.length - 1];

        System.out.printf("A_STAR_PERF nodes=%d samples=%d p50_us=%d p95_us=%d max_us=%d%n",
                side * side, samples.length, p50, p95, max);
        assertThat(p95).as("400 节点固定网格的 P95 应低于宽松发布门槛").isLessThan(250_000);
    }

    private AStarRouter.SearchOutcome search(RouteGraph.GraphData graph, UUID start, UUID end) {
        return router.search(graph, start, end, RoutingDtos.RouteProfile.ACCESSIBLE,
                RoutingDtos.MobilityMode.WHEELCHAIR, RoutingDtos.TravelPeriod.DAY,
                RoutingDtos.RoutePreferences.defaults(), false);
    }

    private RouteGraph.GraphData grid(int side) {
        List<RouteGraph.Node> nodes = new ArrayList<>();
        List<RouteGraph.Edge> edges = new ArrayList<>();
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                int index = row * side + column;
                nodes.add(new RouteGraph.Node(id(index), "N" + index, "节点" + index, true, "HIGH",
                        new RouteGraph.Point(112.0 + column * 0.0001, 28.0 + row * 0.0001)));
                if (column > 0) edges.add(edge(index - 1, index));
                if (row > 0) edges.add(edge(index - side, index));
            }
        }
        return RouteGraph.GraphData.of(nodes, edges);
    }

    private RouteGraph.Edge edge(int from, int to) {
        String name = "E" + from + "-" + to;
        return new RouteGraph.Edge(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, name,
                id(from), id(to), 12, "FLAT", false, 0, "WIDE", "ASPHALT", "HIGH", true,
                "ACTIVE", "LOW", "HIGH", List.of(point(from), point(to)), List.of(), List.of());
    }

    private RouteGraph.Point point(int index) {
        int side = 20;
        return new RouteGraph.Point(112.0 + (index % side) * 0.0001, 28.0 + (index / side) * 0.0001);
    }

    private UUID id(int index) {
        return new UUID(0, index + 1L);
    }
}
