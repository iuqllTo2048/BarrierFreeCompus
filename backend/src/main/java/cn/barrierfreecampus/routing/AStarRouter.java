package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import static cn.barrierfreecampus.routing.RoutingDtos.RoutePreferences;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod;

import cn.barrierfreecampus.routing.RoutingDtos.AlgorithmMetrics;
import cn.barrierfreecampus.routing.RoutingDtos.CostBreakdown;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

public final class AStarRouter {
    private static final double EPSILON = 0.000_001;
    private final RouteCostPolicy costPolicy;

    public AStarRouter(RouteCostPolicy costPolicy) {
        this.costPolicy = costPolicy;
    }

    SearchOutcome search(
            RouteGraph.GraphData graph,
            UUID startNodeId,
            UUID endNodeId,
            RouteProfile profile,
            MobilityMode mode,
            TravelPeriod period,
            RoutePreferences preferences,
            boolean relaxed) {
        long started = System.nanoTime();
        RouteGraph.Node start = graph.nodes().get(startNodeId);
        RouteGraph.Node end = graph.nodes().get(endNodeId);
        if (start == null || end == null || !start.active() || !end.active()) {
            return SearchOutcome.notFound(metrics(0, 0, 0, started, 0), relaxed);
        }
        if (startNodeId.equals(endNodeId)) {
            return SearchOutcome.found(List.of(), CostBreakdown.zero(), metrics(0, 0, 1, started, 0), relaxed);
        }

        double heuristicScale = admissibleDistanceScale(graph);
        PriorityQueue<State> open = new PriorityQueue<>(Comparator.comparingDouble(State::estimatedTotal));
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, RouteGraph.Arc> previous = new HashMap<>();
        scores.put(startNodeId, 0.0);
        open.add(new State(
                startNodeId,
                0,
                costPolicy.heuristic(start, end, preferences) * heuristicScale));
        int expandedNodes = 0;
        int visitedEdges = 0;
        int queuePeak = 1;

        while (!open.isEmpty()) {
            State current = open.poll();
            double bestKnown = scores.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY);
            if (current.costFromStart() > bestKnown + EPSILON) {
                continue;
            }
            if (current.nodeId().equals(endNodeId)) {
                List<RouteGraph.Arc> path = reconstruct(previous, startNodeId, endNodeId);
                CostBreakdown breakdown = evaluatePath(path, profile, mode, period, preferences, relaxed);
                return SearchOutcome.found(
                        path,
                        breakdown,
                        metrics(expandedNodes, visitedEdges, queuePeak, started, breakdown.total()),
                        relaxed);
            }

            expandedNodes++;
            for (RouteGraph.Arc arc : graph.adjacency().getOrDefault(current.nodeId(), List.of())) {
                visitedEdges++;
                RouteGraph.Node target = graph.nodes().get(arc.toNodeId());
                if (target == null || !target.active()) {
                    continue;
                }
                RouteCostPolicy.CostEvaluation evaluation =
                        costPolicy.evaluate(arc, profile, mode, period, preferences, relaxed);
                if (!evaluation.allowed()) {
                    continue;
                }
                double candidate = bestKnown + evaluation.breakdown().total();
                if (candidate + EPSILON < scores.getOrDefault(target.id(), Double.POSITIVE_INFINITY)) {
                    scores.put(target.id(), candidate);
                    previous.put(target.id(), arc);
                    double heuristic = costPolicy.heuristic(target, end, preferences) * heuristicScale;
                    open.add(new State(target.id(), candidate, candidate + heuristic));
                    queuePeak = Math.max(queuePeak, open.size());
                }
            }
        }
        return SearchOutcome.notFound(
                metrics(expandedNodes, visitedEdges, queuePeak, started, 0), relaxed);
    }

    private double admissibleDistanceScale(RouteGraph.GraphData graph) {
        Optional<Double> minimum = graph.edges().stream()
                .filter(edge -> "ACTIVE".equals(edge.status()))
                .map(edge -> {
                    RouteGraph.Node from = graph.nodes().get(edge.fromNodeId());
                    RouteGraph.Node to = graph.nodes().get(edge.toNodeId());
                    if (from == null || to == null) {
                        return 0.0;
                    }
                    double direct = RouteCostPolicy.haversineMeters(from.point(), to.point());
                    return direct <= EPSILON ? 0.0 : edge.distanceM() / direct;
                })
                .filter(value -> value > 0)
                .min(Double::compareTo);
        return Math.min(1.0, minimum.orElse(0.0));
    }

    private List<RouteGraph.Arc> reconstruct(
            Map<UUID, RouteGraph.Arc> previous,
            UUID startNodeId,
            UUID endNodeId) {
        List<RouteGraph.Arc> path = new ArrayList<>();
        UUID cursor = endNodeId;
        while (!cursor.equals(startNodeId)) {
            RouteGraph.Arc arc = previous.get(cursor);
            if (arc == null) {
                return List.of();
            }
            path.add(arc);
            cursor = arc.fromNodeId();
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private CostBreakdown evaluatePath(
            List<RouteGraph.Arc> path,
            RouteProfile profile,
            MobilityMode mode,
            TravelPeriod period,
            RoutePreferences preferences,
            boolean relaxed) {
        CostBreakdown result = CostBreakdown.zero();
        for (RouteGraph.Arc arc : path) {
            result = result.plus(costPolicy.evaluate(arc, profile, mode, period, preferences, relaxed).breakdown());
        }
        return result;
    }

    private AlgorithmMetrics metrics(
            int expandedNodes,
            int visitedEdges,
            int queuePeak,
            long started,
            double totalCost) {
        return new AlgorithmMetrics(
                expandedNodes,
                visitedEdges,
                queuePeak,
                (System.nanoTime() - started) / 1_000,
                totalCost);
    }

    private record State(UUID nodeId, double costFromStart, double estimatedTotal) {
    }

    record SearchOutcome(
            boolean found,
            List<RouteGraph.Arc> path,
            CostBreakdown costBreakdown,
            AlgorithmMetrics metrics,
            boolean relaxed) {
        static SearchOutcome found(
                List<RouteGraph.Arc> path,
                CostBreakdown breakdown,
                AlgorithmMetrics metrics,
                boolean relaxed) {
            return new SearchOutcome(true, path, breakdown, metrics, relaxed);
        }

        static SearchOutcome notFound(AlgorithmMetrics metrics, boolean relaxed) {
            return new SearchOutcome(false, List.of(), CostBreakdown.zero(), metrics, relaxed);
        }
    }
}
