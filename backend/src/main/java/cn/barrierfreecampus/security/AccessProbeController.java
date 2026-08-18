package cn.barrierfreecampus.security;

import cn.barrierfreecampus.common.ApiResponse;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccessProbeController {
    @GetMapping("/user/home")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ApiResponse<Map<String, String>> userHome() {
        return ApiResponse.ok(Map.of("scope", "USER", "message", "用户端认证成功"));
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> adminDashboard() {
        return ApiResponse.ok(Map.of("scope", "ADMIN", "message", "管理端认证成功"));
    }
}
