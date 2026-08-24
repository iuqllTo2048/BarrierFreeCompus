package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.AdminOverview;
import static cn.barrierfreecampus.business.BusinessDtos.AdminUserView;
import static cn.barrierfreecampus.business.BusinessDtos.AuditView;
import static cn.barrierfreecampus.business.BusinessDtos.BarrierReportView;
import static cn.barrierfreecampus.business.BusinessDtos.BarrierReviewRequest;
import static cn.barrierfreecampus.business.BusinessDtos.BarrierSubmitRequest;
import static cn.barrierfreecampus.business.BusinessDtos.CommentRequest;
import static cn.barrierfreecampus.business.BusinessDtos.FacilityDetail;
import static cn.barrierfreecampus.business.BusinessDtos.FacilitySuggestionView;
import static cn.barrierfreecampus.business.BusinessDtos.FavoriteRequest;
import static cn.barrierfreecampus.business.BusinessDtos.FavoriteView;
import static cn.barrierfreecampus.business.BusinessDtos.ProfileUpdateRequest;
import static cn.barrierfreecampus.business.BusinessDtos.ProfileView;
import static cn.barrierfreecampus.business.BusinessDtos.RatingRequest;
import static cn.barrierfreecampus.business.BusinessDtos.RouteHistoryView;
import static cn.barrierfreecampus.business.BusinessDtos.SettingView;
import static cn.barrierfreecampus.business.BusinessDtos.SuggestionRequest;

import cn.barrierfreecampus.routing.RoutingDtos;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 业务门面：用户资料、设施互动、上报审核、路线历史与治理的统一入口。
 * 具体实现拆分到 UserProfileService、FacilityInteractionService、BarrierGovernanceService、
 * UserDataService 与 AdminGovernanceService，本类保持原有公开方法签名，Controller 调用不变。
 */
@Service
public class BusinessService {
    private final UserProfileService profileService;
    private final FacilityInteractionService interactionService;
    private final BarrierGovernanceService barrierService;
    private final UserDataService userDataService;
    private final AdminGovernanceService adminService;

    public BusinessService(
            UserProfileService profileService,
            FacilityInteractionService interactionService,
            BarrierGovernanceService barrierService,
            UserDataService userDataService,
            AdminGovernanceService adminService) {
        this.profileService = profileService;
        this.interactionService = interactionService;
        this.barrierService = barrierService;
        this.userDataService = userDataService;
        this.adminService = adminService;
    }

    public ProfileView profile(String username) {
        return profileService.profile(username);
    }

    public ProfileView updateProfile(String username, ProfileUpdateRequest request) {
        return profileService.updateProfile(username, request);
    }

    public FacilityDetail facility(UUID facilityId, String username) {
        return interactionService.facility(facilityId, username);
    }

    public void rateFacility(UUID facilityId, String username, RatingRequest request) {
        interactionService.rateFacility(facilityId, username, request);
    }

    public long commentFacility(UUID facilityId, String username, CommentRequest request) {
        return interactionService.commentFacility(facilityId, username, request);
    }

    public UUID suggestFacility(UUID facilityId, String username, SuggestionRequest request) {
        return interactionService.suggestFacility(facilityId, username, request);
    }

    public BarrierReportView submitBarrier(String username, BarrierSubmitRequest request) {
        return barrierService.submitBarrier(username, request);
    }

    public List<BarrierReportView> myBarriers(String username) {
        return barrierService.myBarriers(username);
    }

    public List<BarrierReportView> adminBarriers(String status) {
        return barrierService.adminBarriers(status);
    }

    public BarrierReportView reviewBarrier(UUID id, String admin, BarrierReviewRequest request) {
        return barrierService.reviewBarrier(id, admin, request);
    }

    public UUID recordHistory(String username, RoutingDtos.RoutePlanRequest request, RoutingDtos.RoutePlanResponse result) {
        return userDataService.recordHistory(username, request, result);
    }

    public List<RouteHistoryView> history(String username) {
        return userDataService.history(username);
    }

    public void deleteHistory(UUID id, String username) {
        userDataService.deleteHistory(id, username);
    }

    public UUID favorite(UUID historyId, String username, FavoriteRequest request) {
        return userDataService.favorite(historyId, username, request);
    }

    public List<FavoriteView> favorites(String username) {
        return userDataService.favorites(username);
    }

    public void removeFavorite(UUID id, String username) {
        userDataService.removeFavorite(id, username);
    }

    public AdminOverview overview() {
        return adminService.overview();
    }

    public List<FacilitySuggestionView> suggestions() {
        return adminService.suggestions();
    }

    public void reviewSuggestion(UUID id, String status, String admin) {
        adminService.reviewSuggestion(id, status, admin);
    }

    public List<AdminUserView> users() {
        return adminService.users();
    }

    public void setUserEnabled(long id, boolean enabled, String admin) {
        adminService.setUserEnabled(id, enabled, admin);
    }

    public List<AuditView> audits() {
        return adminService.audits();
    }

    public List<SettingView> settings() {
        return adminService.settings();
    }

    public SettingView updateSetting(String key, String value, String admin) {
        return adminService.updateSetting(key, value, admin);
    }

    public void setMapObjectActive(String type, UUID id, boolean active, String admin) {
        adminService.setMapObjectActive(type, id, active, admin);
    }

    public void resetDemo(UUID datasetId, String admin) {
        adminService.resetDemo(datasetId, admin);
    }

    public int expireBarriers() {
        return barrierService.expireBarriers();
    }
}
