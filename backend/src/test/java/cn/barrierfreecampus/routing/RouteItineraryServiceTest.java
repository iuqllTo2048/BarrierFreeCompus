package cn.barrierfreecampus.routing;

import static cn.barrierfreecampus.routing.RoutingDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RouteItineraryServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoutingService routingService = mock(RoutingService.class);
    private final RouteItineraryService service = new RouteItineraryService(routingService, objectMapper);

    @Test
    void shouldMergeAdjacentSegmentsIntoOneExistingRouteResponse() {
        UUID datasetId = UUID.randomUUID();
        UUID start = UUID.randomUUID();
        UUID waypoint = UUID.randomUUID();
        UUID end = UUID.randomUUID();
        UUID sharedFacility = UUID.randomUUID();
        UUID firstEdge = UUID.randomUUID();
        UUID secondEdge = UUID.randomUUID();
        when(routingService.plan(any()))
                .thenReturn(segment(datasetId, start, waypoint, firstEdge, sharedFacility,
                                "[[112.1,28.1],[112.2,28.2]]", 120, 2, "LOW", 0),
                        segment(datasetId, waypoint, end, secondEdge, sharedFacility,
                                "[[112.2,28.2],[112.3,28.3]]", 180, 3, "MEDIUM", 1));

        RouteItineraryService.ItineraryPlan itinerary = service.plan(datasetId,
                List.of(start, waypoint, end), MobilityMode.WALKING, TravelPeriod.DAY,
                RoutePreferences.defaults());

        assertThat(itinerary.segments()).hasSize(2);
        assertThat(itinerary.combined().startNodeId()).isEqualTo(start);
        assertThat(itinerary.combined().endNodeId()).isEqualTo(end);
        assertThat(itinerary.combined().routes()).hasSize(1);
        RouteResult merged = itinerary.combined().routes().getFirst();
        assertThat(merged.equivalentProfiles()).containsExactly(
                RouteProfile.SHORTEST, RouteProfile.ACCESSIBLE, RouteProfile.BALANCED);
        assertThat(merged.geometry().path("coordinates")).hasSize(3);
        assertThat(merged.distanceM()).isEqualTo(300);
        assertThat(merged.estimatedMinutes()).isEqualTo(5);
        assertThat(merged.stairsCount()).isEqualTo(1);
        assertThat(merged.riskSummary().level()).isEqualTo("MEDIUM");
        assertThat(merged.facilities()).hasSize(1);
        assertThat(merged.edgeIds()).containsExactly(firstEdge, secondEdge);
        assertThat(merged.warnings()).containsExactly("建议现场确认");
    }

    @Test
    void shouldRejectWholeItineraryWhenAnySegmentHasNoRoute() {
        UUID datasetId = UUID.randomUUID();
        UUID start = UUID.randomUUID();
        UUID waypoint = UUID.randomUUID();
        UUID end = UUID.randomUUID();
        when(routingService.plan(any()))
                .thenReturn(segment(datasetId, start, waypoint, UUID.randomUUID(), UUID.randomUUID(),
                                "[[112.1,28.1],[112.2,28.2]]", 100, 2, "LOW", 0),
                        new RoutePlanResponse(datasetId, waypoint, end, MobilityMode.WALKING,
                                TravelPeriod.DAY, List.of(), List.of("没有可达路线"), null));

        RouteItineraryService.ItineraryPlan itinerary = service.plan(datasetId,
                List.of(start, waypoint, end), MobilityMode.WALKING, TravelPeriod.DAY,
                RoutePreferences.defaults());

        assertThat(itinerary.combined().routes()).isEmpty();
        assertThat(itinerary.combined().notices()).anyMatch(notice -> notice.contains("第 2 段"));
    }

    @Test
    void shouldPlanEightWaypointsAsNineSegments() {
        UUID datasetId = UUID.randomUUID();
        List<UUID> ordered = IntStream.range(0, 10).mapToObj(ignored -> UUID.randomUUID()).toList();
        UUID sharedFacility = UUID.randomUUID();
        when(routingService.plan(any())).thenAnswer(invocation -> {
            RoutePlanRequest request = invocation.getArgument(0);
            int index = ordered.indexOf(request.startNodeId());
            return segment(datasetId, request.startNodeId(), request.endNodeId(),
                    UUID.randomUUID(), sharedFacility,
                    "[[112." + index + ",28.1],[112." + (index + 1) + ",28.1]]",
                    100, 1, "LOW", 0);
        });

        RouteItineraryService.ItineraryPlan itinerary = service.plan(datasetId, ordered,
                MobilityMode.WALKING, TravelPeriod.DAY, RoutePreferences.defaults());

        assertThat(itinerary.segments()).hasSize(9);
        assertThat(itinerary.combined().routes()).hasSize(1);
        RouteResult route = itinerary.combined().routes().getFirst();
        assertThat(route.distanceM()).isEqualTo(900);
        assertThat(route.estimatedMinutes()).isEqualTo(9);
        assertThat(route.edgeIds()).hasSize(9);
        assertThat(route.geometry().path("coordinates")).hasSize(10);
    }

    @Test
    void shouldRejectNineWaypointsBeforeRouting() {
        List<UUID> ordered = IntStream.range(0, 11).mapToObj(ignored -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> service.plan(UUID.randomUUID(), ordered,
                MobilityMode.WALKING, TravelPeriod.DAY, RoutePreferences.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("途经点最多支持 8 个");
        verifyNoInteractions(routingService);
    }

    private RoutePlanResponse segment(UUID datasetId, UUID start, UUID end, UUID edgeId,
                                      UUID facilityId, String coordinates, double distance,
                                      long minutes, String risk, int stairs) {
        RouteResult route = new RouteResult(
                RouteProfile.SHORTEST,
                List.of(RouteProfile.SHORTEST, RouteProfile.ACCESSIBLE, RouteProfile.BALANCED),
                objectMapper.createObjectNode().put("type", "LineString")
                        .set("coordinates", read(coordinates)),
                distance,
                minutes,
                new RiskSummary(risk, 0, "MEDIUM".equals(risk) ? 1 : 0, 0, false),
                stairs,
                Map.of("FLAT", 1, "GENTLE", 0, "MODERATE", 0, "STEEP", 0, "UNKNOWN", 0),
                List.of(new RouteFacility(facilityId, "休息点", "REST_AREA", "OPEN", "HIGH", 112.2, 28.2)),
                List.of(),
                "HIGH",
                new CostBreakdown(distance, 0, stairs, 0, 0, 0, 0, 0, 0, distance + stairs),
                List.of(),
                List.of("建议现场确认"),
                new AlgorithmMetrics(3, 4, 2, 10, distance + stairs),
                List.of(edgeId));
        return new RoutePlanResponse(datasetId, start, end, MobilityMode.WALKING,
                TravelPeriod.DAY, List.of(route), List.of(), null);
    }

    private com.fasterxml.jackson.databind.JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
