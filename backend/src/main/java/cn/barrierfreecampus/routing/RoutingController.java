package cn.barrierfreecampus.routing;

import cn.barrierfreecampus.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class RoutingController {
    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/plan")
    public ApiResponse<RoutingDtos.RoutePlanResponse> plan(
            @Valid @RequestBody RoutingDtos.RoutePlanRequest request) {
        return ApiResponse.ok(routingService.plan(request));
    }
}
