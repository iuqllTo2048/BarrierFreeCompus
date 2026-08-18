package cn.barrierfreecampus.agent;

import cn.barrierfreecampus.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agent")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAgentController {
    private final AgentService service;
    public AdminAgentController(AgentService service) { this.service = service; }

    @GetMapping("/invocations")
    public ApiResponse<List<AgentDtos.InvocationView>> invocations() {
        return ApiResponse.ok(service.invocations());
    }
}
