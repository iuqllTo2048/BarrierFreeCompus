package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.*;

import cn.barrierfreecampus.routing.AStarRouter.ArcKey;
import cn.barrierfreecampus.routing.AStarRouter.SearchOutcome;
import cn.barrierfreecampus.routing.AStarRouter.SearchRestrictions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * 使用 Yen 算法在现有加权 A* 之上生成最多 K 条不同的无环路线。
 */
final class YenTopKRouter {
    private final AStarRouter router;

    YenTopKRouter(AStarRouter router) {
        this.router = router;
    }

    List<SearchOutcome> search(
            RouteGraph.GraphData graph,
            UUID startNodeId,
            UUID endNodeId,
            RouteProfile profile,
            MobilityMode mode,
            TravelPeriod period,
            RoutePreferences preferences,
            boolean relaxed,
            int limit) {
        if (limit < 1) return List.of();
        SearchOutcome first = router.search(graph, startNodeId, endNodeId, profile, mode,
                period, preferences, relaxed);
        if (!first.found()) return List.of();

        List<SearchOutcome> accepted = new ArrayList<>();
        accepted.add(first);
        Set<List<ArcKey>> acceptedSignatures = new LinkedHashSet<>();
        acceptedSignatures.add(signature(first.path()));
        Set<List<ArcKey>> candidateSignatures = new HashSet<>();
        PriorityQueue<SearchOutcome> candidates = new PriorityQueue<>(Comparator
                .comparingDouble((SearchOutcome outcome) -> outcome.costBreakdown().total())
                .thenComparing(outcome -> signatureText(outcome.path())));

        while (accepted.size() < limit) {
            List<RouteGraph.Arc> previousPath = accepted.getLast().path();
            for (int spurIndex = 0; spurIndex < previousPath.size(); spurIndex++) {
                List<RouteGraph.Arc> rootPath = List.copyOf(previousPath.subList(0, spurIndex));
                UUID spurNodeId = spurIndex == 0
                        ? startNodeId : previousPath.get(spurIndex - 1).toNodeId();
                Set<ArcKey> blockedArcs = new HashSet<>();
                for (SearchOutcome route : accepted) {
                    if (route.path().size() > spurIndex && hasPrefix(route.path(), rootPath)) {
                        blockedArcs.add(ArcKey.of(route.path().get(spurIndex)));
                    }
                }
                Set<UUID> blockedNodes = rootNodeIds(startNodeId, rootPath);
                blockedNodes.remove(spurNodeId);
                SearchOutcome spur = router.search(graph, spurNodeId, endNodeId, profile, mode,
                        period, preferences, relaxed,
                        new SearchRestrictions(blockedNodes, blockedArcs));
                if (!spur.found()) continue;

                List<RouteGraph.Arc> wholePath = new ArrayList<>(rootPath);
                wholePath.addAll(spur.path());
                if (!isLoopless(startNodeId, wholePath)) continue;
                List<ArcKey> wholeSignature = signature(wholePath);
                if (acceptedSignatures.contains(wholeSignature)
                        || !candidateSignatures.add(wholeSignature)) continue;
                CostBreakdown breakdown = router.evaluatePath(
                        wholePath, profile, mode, period, preferences, relaxed);
                AlgorithmMetrics spurMetrics = spur.metrics();
                candidates.add(SearchOutcome.found(
                        List.copyOf(wholePath), breakdown,
                        new AlgorithmMetrics(
                                spurMetrics.expandedNodes(), spurMetrics.visitedEdges(),
                                spurMetrics.queuePeak(), spurMetrics.elapsedMicros(), breakdown.total()),
                        relaxed));
            }

            SearchOutcome next = candidates.poll();
            if (next == null) break;
            candidateSignatures.remove(signature(next.path()));
            accepted.add(next);
            acceptedSignatures.add(signature(next.path()));
        }
        return List.copyOf(accepted);
    }

    private boolean hasPrefix(List<RouteGraph.Arc> path, List<RouteGraph.Arc> prefix) {
        if (path.size() < prefix.size()) return false;
        for (int index = 0; index < prefix.size(); index++) {
            if (!ArcKey.of(path.get(index)).equals(ArcKey.of(prefix.get(index)))) return false;
        }
        return true;
    }

    private Set<UUID> rootNodeIds(UUID startNodeId, List<RouteGraph.Arc> rootPath) {
        Set<UUID> result = new HashSet<>();
        result.add(startNodeId);
        rootPath.forEach(arc -> result.add(arc.toNodeId()));
        return result;
    }

    private boolean isLoopless(UUID startNodeId, List<RouteGraph.Arc> path) {
        Set<UUID> visited = new HashSet<>();
        visited.add(startNodeId);
        for (RouteGraph.Arc arc : path) {
            if (!visited.add(arc.toNodeId())) return false;
        }
        return true;
    }

    private List<ArcKey> signature(List<RouteGraph.Arc> path) {
        return path.stream().map(ArcKey::of).toList();
    }

    private String signatureText(List<RouteGraph.Arc> path) {
        return path.stream().map(arc -> ArcKey.of(arc).toString())
                .reduce("", (left, right) -> left + "|" + right);
    }
}
