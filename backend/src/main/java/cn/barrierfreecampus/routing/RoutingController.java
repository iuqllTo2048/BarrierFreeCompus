package cn.barrierfreecampus.routing;

import cn.barrierfreecampus.common.ApiResponse;
import cn.barrierfreecampus.business.BusinessService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class RoutingController {
    private final RoutingService routingService;
    private final BusinessService businessService;

    public RoutingController(RoutingService routingService, BusinessService businessService) {
        this.routingService = routingService;
        this.businessService = businessService;
    }

    @PostMapping("/plan")
    public ApiResponse<RoutingDtos.RoutePlanResponse> plan(
            @Valid @RequestBody RoutingDtos.RoutePlanRequest request,
            Authentication authentication) {
        RoutingDtos.RoutePlanResponse result = routingService.plan(request);
        UUID historyId = businessService.recordHistory(authentication.getName(), request, result);
        return ApiResponse.ok(new RoutingDtos.RoutePlanResponse(
                result.datasetId(), result.startNodeId(), result.endNodeId(), result.mobilityMode(),
                result.travelPeriod(), result.routes(), result.notices(), historyId));
    }
}
