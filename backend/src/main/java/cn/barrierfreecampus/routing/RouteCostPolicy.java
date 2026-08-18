package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.MobilityMode;
import static cn.barrierfreecampus.routing.RoutingDtos.RoutePreferences;
import static cn.barrierfreecampus.routing.RoutingDtos.RouteProfile;
import static cn.barrierfreecampus.routing.RoutingDtos.TravelPeriod;

import cn.barrierfreecampus.routing.RoutingDtos.CostBreakdown;
import java.util.Set;

/** 所有 A* 成本规则集中在此处，成本单位可理解为“等效距离米”。 */
public final class RouteCostPolicy {
    private static final Set<String> HARD_BLOCKING_BARRIERS = Set.of(
            "TEMPORARY_CLOSURE", "CONSTRUCTION", "VEHICLE_BLOCKING", "ENTRANCE_CLOSED");

    public CostEvaluation evaluate(
            RouteGraph.Arc arc,
            RouteProfile profile,
            MobilityMode mode,
            TravelPeriod period,
            RoutePreferences preferences,
            boolean relaxed) {
        RouteGraph.Edge edge = arc.edge();
        if (!"ACTIVE".equals(edge.status()) || hasBlockingBarrier(edge)) {
            return CostEvaluation.blocked("道路已关闭或存在生效阻断");
        }
        if (edge.hasStairs() && mode == MobilityMode.WHEELCHAIR) {
            return CostEvaluation.blocked("轮椅模式不可通过楼梯");
        }
        if (edge.hasStairs() && preferences.avoidStairs() && !relaxed) {
            return CostEvaluation.blocked("已按偏好避开楼梯");
        }

        double distance = edge.distanceM() * preferences.distanceWeightOrDefault();
        double slope = edge.distanceM() * slopeRate(profile, edge.slopeLevel())
                * preferences.slopeWeightOrDefault();
        double stairs = stairsCost(profile, mode, edge);
        double width = edge.distanceM() * widthRate(profile, edge.widthLevel())
                * preferences.widthWeightOrDefault();
        double surface = edge.distanceM() * surfaceRate(profile, edge.surfaceType());
        double lighting = period == TravelPeriod.NIGHT
                ? edge.distanceM() * lightingRate(profile, edge.lightingLevel())
                : 0;
        double barrier = riskCost(profile, edge.riskLevel()) + barrierCost(profile, mode, edge);
        double uncertainty = confidenceCost(profile, edge.confidenceLevel());
        double facilityPreference = facilityPreferenceCost(edge, profile, preferences);
        double total = distance + slope + stairs + width + surface + lighting
                + barrier + uncertainty + facilityPreference;
        return new CostEvaluation(
                true,
                null,
                new CostBreakdown(
                        distance, slope, stairs, width, surface, lighting,
                        barrier, uncertainty, facilityPreference, total));
    }

    public double heuristic(RouteGraph.Node from, RouteGraph.Node to, RoutePreferences preferences) {
        return haversineMeters(from.point(), to.point()) * preferences.distanceWeightOrDefault();
    }

    public boolean hasBlockingBarrier(RouteGraph.Edge edge) {
        return edge.barriers().stream().anyMatch(barrier -> HARD_BLOCKING_BARRIERS.contains(barrier.type()));
    }

    private double slopeRate(RouteProfile profile, String level) {
        return switch (level) {
            case "GENTLE" -> switch (profile) {
                case SHORTEST -> 0.02;
                case ACCESSIBLE -> 0.08;
                case BALANCED -> 0.04;
            };
            case "MODERATE" -> switch (profile) {
                case SHORTEST -> 0.08;
                case ACCESSIBLE -> 0.30;
                case BALANCED -> 0.16;
            };
            case "STEEP" -> switch (profile) {
                case SHORTEST -> 0.20;
                case ACCESSIBLE -> 1.20;
                case BALANCED -> 0.55;
            };
            case "UNKNOWN" -> switch (profile) {
                case SHORTEST -> 0.06;
                case ACCESSIBLE -> 0.25;
                case BALANCED -> 0.12;
            };
            default -> 0;
        };
    }

    private double stairsCost(RouteProfile profile, MobilityMode mode, RouteGraph.Edge edge) {
        if (!edge.hasStairs()) {
            return 0;
        }
        double profileCost = switch (profile) {
            case SHORTEST -> 8 + edge.stairsCount() * 2.0;
            case ACCESSIBLE -> 80 + edge.stairsCount() * 8.0;
            case BALANCED -> 30 + edge.stairsCount() * 4.0;
        };
        double modeFactor = switch (mode) {
            case CRUTCH, TEMPORARY_INJURY -> 2.0;
            case CART_LUGGAGE -> 3.0;
            case WALKING -> 1.0;
            case WHEELCHAIR -> Double.POSITIVE_INFINITY;
        };
        return profileCost * modeFactor;
    }

    private double widthRate(RouteProfile profile, String width) {
        if ("NARROW".equals(width)) {
            return switch (profile) {
                case SHORTEST -> 0.02;
                case ACCESSIBLE -> 0.35;
                case BALANCED -> 0.12;
            };
        }
        if ("UNKNOWN".equals(width)) {
            return switch (profile) {
                case SHORTEST -> 0.03;
                case ACCESSIBLE -> 0.12;
                case BALANCED -> 0.06;
            };
        }
        return 0;
    }

    private double surfaceRate(RouteProfile profile, String surface) {
        double shortest = switch (surface) {
            case "BRICK" -> 0.02;
            case "GRAVEL" -> 0.08;
            case "DIRT" -> 0.12;
            case "UNKNOWN" -> 0.04;
            default -> 0;
        };
        return switch (profile) {
            case SHORTEST -> shortest;
            case ACCESSIBLE -> shortest * 4.2;
            case BALANCED -> shortest * 2.2;
        };
    }

    private double lightingRate(RouteProfile profile, String lighting) {
        double base = switch (lighting) {
            case "NONE" -> 0.35;
            case "LOW" -> 0.15;
            case "UNKNOWN" -> 0.10;
            default -> 0;
        };
        return switch (profile) {
            case SHORTEST -> base;
            case ACCESSIBLE -> base * 1.3;
            case BALANCED -> base * 1.15;
        };
    }

    private double riskCost(RouteProfile profile, String risk) {
        return switch (risk) {
            case "MEDIUM" -> switch (profile) {
                case SHORTEST -> 15;
                case ACCESSIBLE -> 35;
                case BALANCED -> 25;
            };
            case "HIGH" -> switch (profile) {
                case SHORTEST -> 50;
                case ACCESSIBLE -> 120;
                case BALANCED -> 80;
            };
            case "UNKNOWN" -> switch (profile) {
                case SHORTEST -> 8;
                case ACCESSIBLE -> 25;
                case BALANCED -> 15;
            };
            default -> 0;
        };
    }

    private double barrierCost(RouteProfile profile, MobilityMode mode, RouteGraph.Edge edge) {
        double total = 0;
        for (RouteGraph.Barrier barrier : edge.barriers()) {
            if (HARD_BLOCKING_BARRIERS.contains(barrier.type())) {
                continue;
            }
            double severity = switch (barrier.type()) {
                case "STAIRS" -> 35;
                case "STEEP_SLOPE", "WATERLOGGING" -> 45;
                case "DAMAGED_SURFACE", "NARROW_PATH" -> 30;
                case "ELEVATOR_OUTAGE" -> 20;
                default -> 15;
            };
            double profileFactor = switch (profile) {
                case SHORTEST -> 0.7;
                case ACCESSIBLE -> 1.5;
                case BALANCED -> 1.0;
            };
            double modeFactor = mode == MobilityMode.WALKING ? 1.0 : 1.35;
            total += severity * profileFactor * modeFactor;
        }
        return total;
    }

    private double confidenceCost(RouteProfile profile, String confidence) {
        return switch (confidence) {
            case "LOW" -> switch (profile) {
                case SHORTEST -> 3;
                case ACCESSIBLE -> 10;
                case BALANCED -> 6;
            };
            case "UNKNOWN" -> switch (profile) {
                case SHORTEST -> 5;
                case ACCESSIBLE -> 20;
                case BALANCED -> 12;
            };
            default -> 0;
        };
    }

    private double facilityPreferenceCost(
            RouteGraph.Edge edge,
            RouteProfile profile,
            RoutePreferences preferences) {
        boolean hasRestArea = edge.facilities().stream().anyMatch(facility -> "REST_AREA".equals(facility.type()));
        boolean hasToilet = edge.facilities().stream()
                .anyMatch(facility -> "ACCESSIBLE_TOILET".equals(facility.type()));
        double profileFactor = profile == RouteProfile.ACCESSIBLE ? 1.25 : 1.0;
        double restPenalty = !hasRestArea
                ? edge.distanceM() * 0.04 * preferences.restAreaWeightOrDefault() * profileFactor
                : 0;
        double toiletPenalty = !hasToilet
                ? edge.distanceM() * 0.03 * preferences.accessibleToiletWeightOrDefault() * profileFactor
                : 0;
        return restPenalty + toiletPenalty;
    }

    static double haversineMeters(RouteGraph.Point first, RouteGraph.Point second) {
        double earthRadius = 6_371_000;
        double lat1 = Math.toRadians(first.lat());
        double lat2 = Math.toRadians(second.lat());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(second.lng() - first.lng());
        double value = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double boundedValue = Math.max(0, Math.min(1, value));
        return earthRadius * 2 * Math.atan2(Math.sqrt(boundedValue), Math.sqrt(1 - boundedValue));
    }

    public record CostEvaluation(boolean allowed, String blockedReason, CostBreakdown breakdown) {
        static CostEvaluation blocked(String reason) {
            return new CostEvaluation(false, reason, CostBreakdown.zero());
        }
    }
}
