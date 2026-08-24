package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.CommentRequest;
import static cn.barrierfreecampus.business.BusinessDtos.FacilityCommentView;
import static cn.barrierfreecampus.business.BusinessDtos.FacilityDetail;
import static cn.barrierfreecampus.business.BusinessDtos.RatingRequest;
import static cn.barrierfreecampus.business.BusinessDtos.SuggestionRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设施详情、评分、评论与建议。
 */
@Component
public class FacilityInteractionService {
    private final BusinessSupport support;

    public FacilityInteractionService(BusinessSupport support) {
        this.support = support;
    }

    public FacilityDetail facility(UUID facilityId, String username) {
        long userId = support.userId(username);
        FacilityDetail base = support.jdbc().query(
                        """
                        SELECT f.id,f.name,f.facility_type,MAX(b.name) building_name,f.floor_label,f.open_status,f.description,
                          f.data_source,f.confidence_level,f.photo_url,f.updated_at,ST_X(f.geom) lng,ST_Y(f.geom) lat,
                          COALESCE(AVG(r.rating),0) average_rating,COUNT(r.id) rating_count,
                          MAX(CASE WHEN r.user_id=? THEN r.rating END) my_rating
                        FROM accessible_facility f LEFT JOIN facility_rating r ON r.facility_id=f.id
                        LEFT JOIN building b ON b.id=f.building_id
                        WHERE f.id=? AND f.active=TRUE
                        GROUP BY f.id
                        """,
                        (rs, row) -> new FacilityDetail(
                                rs.getObject("id", UUID.class), rs.getString("name"),
                                rs.getString("facility_type"), rs.getString("building_name"), rs.getString("floor_label"),
                                rs.getString("open_status"), rs.getString("description"),
                                rs.getString("data_source"), rs.getString("confidence_level"),
                                rs.getString("photo_url"), rs.getObject("updated_at", OffsetDateTime.class),
                                rs.getDouble("lng"), rs.getDouble("lat"),
                                Math.round(rs.getDouble("average_rating") * 10.0) / 10.0,
                                rs.getInt("rating_count"), (Integer) rs.getObject("my_rating"), List.of()),
                        userId, facilityId)
                .stream().findFirst().orElseThrow(() -> support.notFound("设施不存在或已停用"));
        List<FacilityCommentView> comments = support.jdbc().query(
                """
                SELECT c.id,u.username,c.content,c.created_at FROM facility_comment c
                JOIN app_user u ON u.id=c.user_id
                WHERE c.facility_id=? AND c.status='VISIBLE' ORDER BY c.created_at DESC LIMIT 50
                """,
                (rs, row) -> new FacilityCommentView(
                        rs.getLong("id"), rs.getString("username"), rs.getString("content"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                facilityId);
        return new FacilityDetail(
                base.id(), base.name(), base.facilityType(), base.buildingName(), base.floorLabel(), base.openStatus(),
                base.description(), base.dataSource(), base.confidenceLevel(), base.photoUrl(), base.updatedAt(),
                base.lng(), base.lat(),
                base.averageRating(), base.ratingCount(), base.myRating(), comments);
    }

    @Transactional
    public void rateFacility(UUID facilityId, String username, RatingRequest request) {
        long userId = support.userId(username);
        UUID datasetId = support.facilityDataset(facilityId);
        support.jdbc().update(
                """
                INSERT INTO facility_rating(dataset_id,facility_id,user_id,rating) VALUES (?,?,?,?)
                ON CONFLICT(facility_id,user_id) DO UPDATE SET rating=EXCLUDED.rating,updated_at=CURRENT_TIMESTAMP
                """,
                datasetId, facilityId, userId, request.rating());
        support.audit(userId, "FACILITY_RATE", "ACCESSIBLE_FACILITY", facilityId.toString(), "rating=" + request.rating());
    }

    @Transactional
    public long commentFacility(UUID facilityId, String username, CommentRequest request) {
        long userId = support.userId(username);
        UUID datasetId = support.facilityDataset(facilityId);
        Long id = support.jdbc().queryForObject(
                """
                INSERT INTO facility_comment(dataset_id,facility_id,user_id,content)
                VALUES (?,?,?,?) RETURNING id
                """,
                Long.class, datasetId, facilityId, userId, request.content().trim());
        support.audit(userId, "FACILITY_COMMENT", "ACCESSIBLE_FACILITY", facilityId.toString(), null);
        return id == null ? 0 : id;
    }

    @Transactional
    public UUID suggestFacility(UUID facilityId, String username, SuggestionRequest request) {
        long userId = support.userId(username);
        UUID datasetId = support.facilityDataset(facilityId);
        UUID id = UUID.randomUUID();
        support.jdbc().update(
                "INSERT INTO facility_suggestion(id,dataset_id,facility_id,user_id,suggestion_type,content) VALUES (?,?,?,?,?,?)",
                id, datasetId, facilityId, userId, request.suggestionType(), request.content().trim());
        support.audit(userId, "FACILITY_SUGGEST", "ACCESSIBLE_FACILITY", facilityId.toString(), request.suggestionType());
        return id;
    }
}
