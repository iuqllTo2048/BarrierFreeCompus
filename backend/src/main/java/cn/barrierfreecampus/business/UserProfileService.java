package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.ProfileUpdateRequest;
import static cn.barrierfreecampus.business.BusinessDtos.ProfileView;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户个人资料与偏好。
 */
@Component
public class UserProfileService {
    private final BusinessSupport support;

    public UserProfileService(BusinessSupport support) {
        this.support = support;
    }

    public ProfileView profile(String username) {
        ensureProfile(username);
        return support.jdbc().queryForObject(
                """
                SELECT u.username,COALESCE(p.display_name,u.username) display_name,p.default_mobility_mode,
                  p.avoid_stairs,p.distance_weight,p.slope_weight,p.width_weight,
                  p.prefer_rest_area,p.prefer_accessible_toilet
                FROM app_user u JOIN user_profile p ON p.user_id=u.id WHERE u.username=?
                """,
                (rs, row) -> new ProfileView(
                        rs.getString("username"), rs.getString("display_name"),
                        rs.getString("default_mobility_mode"), rs.getBoolean("avoid_stairs"),
                        rs.getDouble("distance_weight"), rs.getDouble("slope_weight"),
                        rs.getDouble("width_weight"), rs.getBoolean("prefer_rest_area"),
                        rs.getBoolean("prefer_accessible_toilet")),
                username);
    }

    @Transactional
    public ProfileView updateProfile(String username, ProfileUpdateRequest request) {
        long userId = support.userId(username);
        support.jdbc().update(
                """
                INSERT INTO user_profile(user_id,display_name,default_mobility_mode,avoid_stairs,
                  distance_weight,slope_weight,width_weight,prefer_rest_area,prefer_accessible_toilet)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET display_name=EXCLUDED.display_name,
                  default_mobility_mode=EXCLUDED.default_mobility_mode,avoid_stairs=EXCLUDED.avoid_stairs,
                  distance_weight=EXCLUDED.distance_weight,slope_weight=EXCLUDED.slope_weight,
                  width_weight=EXCLUDED.width_weight,prefer_rest_area=EXCLUDED.prefer_rest_area,
                  prefer_accessible_toilet=EXCLUDED.prefer_accessible_toilet,updated_at=CURRENT_TIMESTAMP
                """,
                userId, support.blankToNull(request.displayName()), request.defaultMobilityMode(), request.avoidStairs(),
                request.distanceWeight(), request.slopeWeight(), request.widthWeight(),
                request.preferRestArea(), request.preferAccessibleToilet());
        support.audit(userId, "PROFILE_UPDATE", "USER", Long.toString(userId), null);
        return profile(username);
    }

    private void ensureProfile(String username) {
        long id = support.userId(username);
        support.jdbc().update(
                "INSERT INTO user_profile(user_id,display_name) VALUES (?,?) ON CONFLICT(user_id) DO NOTHING",
                id, username);
    }
}
