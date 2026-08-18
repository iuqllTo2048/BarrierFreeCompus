package cn.barrierfreecampus.business;

import cn.barrierfreecampus.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/business")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBusinessController {
    private final BusinessService service;

    public AdminBusinessController(BusinessService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<BusinessDtos.AdminOverview> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<BusinessDtos.FacilitySuggestionView>> suggestions() {
        return ApiResponse.ok(service.suggestions());
    }

    @PutMapping("/suggestions/{id}")
    public ApiResponse<Void> reviewSuggestion(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessDtos.SuggestionReviewRequest request,
            Authentication authentication) {
        service.reviewSuggestion(id, request.status(), authentication.getName());
        return ApiResponse.ok(null);
    }

    @GetMapping("/barriers")
    public ApiResponse<List<BusinessDtos.BarrierReportView>> barriers(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.adminBarriers(status));
    }

    @PutMapping("/barriers/{id}/review")
    public ApiResponse<BusinessDtos.BarrierReportView> reviewBarrier(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessDtos.BarrierReviewRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.reviewBarrier(id, authentication.getName(), request));
    }

    @GetMapping("/users")
    public ApiResponse<List<BusinessDtos.AdminUserView>> users() {
        return ApiResponse.ok(service.users());
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<Void> userStatus(
            @PathVariable long id,
            @RequestBody BusinessDtos.UserStatusRequest request,
            Authentication authentication) {
        service.setUserEnabled(id, request.enabled(), authentication.getName());
        return ApiResponse.ok(null);
    }

    @GetMapping("/audits")
    public ApiResponse<List<BusinessDtos.AuditView>> audits() {
        return ApiResponse.ok(service.audits());
    }

    @GetMapping("/settings")
    public ApiResponse<List<BusinessDtos.SettingView>> settings() {
        return ApiResponse.ok(service.settings());
    }

    @PutMapping("/settings/{key}")
    public ApiResponse<BusinessDtos.SettingView> setting(
            @PathVariable String key,
            @Valid @RequestBody BusinessDtos.SettingUpdateRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.updateSetting(key, request.value(), authentication.getName()));
    }

    @PatchMapping("/map/{type}/{id}")
    public ApiResponse<Void> mapObjectStatus(
            @PathVariable String type,
            @PathVariable UUID id,
            @RequestBody BusinessDtos.UserStatusRequest request,
            Authentication authentication) {
        service.setMapObjectActive(type, id, request.enabled(), authentication.getName());
        return ApiResponse.ok(null);
    }

    @PostMapping("/datasets/{id}/reset-demo")
    public ApiResponse<Void> resetDemo(@PathVariable UUID id, Authentication authentication) {
        service.resetDemo(id, authentication.getName());
        return ApiResponse.ok(null);
    }
}
