package cn.barrierfreecampus.business;

import cn.barrierfreecampus.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class UserBusinessController {
    private final BusinessService service;

    public UserBusinessController(BusinessService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ApiResponse<BusinessDtos.ProfileView> profile(Authentication authentication) {
        return ApiResponse.ok(service.profile(authentication.getName()));
    }

    @PutMapping("/profile")
    public ApiResponse<BusinessDtos.ProfileView> updateProfile(
            @Valid @RequestBody BusinessDtos.ProfileUpdateRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.updateProfile(authentication.getName(), request));
    }

    @GetMapping("/facilities/{id}")
    public ApiResponse<BusinessDtos.FacilityDetail> facility(
            @PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(service.facility(id, authentication.getName()));
    }

    @PutMapping("/facilities/{id}/rating")
    public ApiResponse<Void> rate(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessDtos.RatingRequest request,
            Authentication authentication) {
        service.rateFacility(id, authentication.getName(), request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/facilities/{id}/comments")
    public ApiResponse<Map<String, Long>> comment(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessDtos.CommentRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", service.commentFacility(id, authentication.getName(), request)));
    }

    @PostMapping("/facilities/{id}/suggestions")
    public ApiResponse<Map<String, UUID>> suggest(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessDtos.SuggestionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", service.suggestFacility(id, authentication.getName(), request)));
    }

    @PostMapping("/barriers")
    public ApiResponse<BusinessDtos.BarrierReportView> reportBarrier(
            @Valid @RequestBody BusinessDtos.BarrierSubmitRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.submitBarrier(authentication.getName(), request));
    }

    @GetMapping("/barriers/mine")
    public ApiResponse<List<BusinessDtos.BarrierReportView>> myBarriers(Authentication authentication) {
        return ApiResponse.ok(service.myBarriers(authentication.getName()));
    }

    @GetMapping("/history")
    public ApiResponse<List<BusinessDtos.RouteHistoryView>> history(Authentication authentication) {
        return ApiResponse.ok(service.history(authentication.getName()));
    }

    @DeleteMapping("/history/{id}")
    public ApiResponse<Void> deleteHistory(@PathVariable UUID id, Authentication authentication) {
        service.deleteHistory(id, authentication.getName());
        return ApiResponse.ok(null);
    }

    @PostMapping("/history/{id}/favorites")
    public ApiResponse<Map<String, UUID>> favorite(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessDtos.FavoriteRequest request,
            Authentication authentication) {
        return ApiResponse.ok(Map.of("id", service.favorite(id, authentication.getName(), request)));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<BusinessDtos.FavoriteView>> favorites(Authentication authentication) {
        return ApiResponse.ok(service.favorites(authentication.getName()));
    }

    @DeleteMapping("/favorites/{id}")
    public ApiResponse<Void> removeFavorite(@PathVariable UUID id, Authentication authentication) {
        service.removeFavorite(id, authentication.getName());
        return ApiResponse.ok(null);
    }
}
