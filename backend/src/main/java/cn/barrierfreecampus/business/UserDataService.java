package cn.barrierfreecampus.business;

import static cn.barrierfreecampus.business.BusinessDtos.FavoriteRequest;
import static cn.barrierfreecampus.business.BusinessDtos.FavoriteView;
import static cn.barrierfreecampus.business.BusinessDtos.RouteHistoryView;

import cn.barrierfreecampus.routing.RoutingDtos;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 路线历史、收藏与记录。
 */
@Component
public class UserDataService {
    private final BusinessSupport support;

    public UserDataService(BusinessSupport support) {
        this.support = support;
    }

    @Transactional
    public UUID recordHistory(String username, RoutingDtos.RoutePlanRequest request, RoutingDtos.RoutePlanResponse result) {
        long userId = support.userId(username);
        UUID id = UUID.randomUUID();
        support.jdbc().update(
                """
                INSERT INTO route_history(id,user_id,dataset_id,start_node_id,end_node_id,mobility_mode,
                  travel_period,request_json,result_json) VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                """,
                id, userId, request.datasetId(), request.startNodeId(), request.endNodeId(),
                request.mobilityMode().name(), request.travelPeriod().name(), support.json(request), support.json(result));
        return id;
    }

    public List<RouteHistoryView> history(String username) {
        return support.jdbc().query(
                """
                SELECT h.id,h.dataset_id,h.start_node_id,h.end_node_id,
                  COALESCE(s.name,s.external_id) start_name,COALESCE(e.name,e.external_id) end_name,
                  h.mobility_mode,h.travel_period,h.result_json::text result_json,h.created_at
                FROM route_history h JOIN route_node s ON s.id=h.start_node_id JOIN route_node e ON e.id=h.end_node_id
                WHERE h.user_id=? ORDER BY h.created_at DESC LIMIT 100
                """,
                (rs, row) -> historyRow(rs), support.userId(username));
    }

    @Transactional
    public void deleteHistory(UUID id, String username) {
        int changed = support.jdbc().update("DELETE FROM route_history WHERE id=? AND user_id=?", id, support.userId(username));
        if (changed == 0) throw support.notFound("路线历史不存在");
    }

    @Transactional
    public UUID favorite(UUID historyId, String username, FavoriteRequest request) {
        long userId = support.userId(username);
        Integer owned = support.jdbc().queryForObject(
                "SELECT COUNT(*) FROM route_history WHERE id=? AND user_id=?", Integer.class, historyId, userId);
        if (owned == null || owned != 1) throw support.notFound("路线历史不存在");
        UUID id = UUID.randomUUID();
        UUID saved = support.jdbc().queryForObject(
                """
                INSERT INTO route_favorite(id,user_id,history_id,route_profile,name) VALUES (?,?,?,?,?)
                ON CONFLICT(user_id,history_id,route_profile) DO UPDATE SET name=EXCLUDED.name
                RETURNING id
                """,
                UUID.class, id, userId, historyId, request.routeProfile(), request.name().trim());
        support.audit(userId, "ROUTE_FAVORITE", "ROUTE_HISTORY", historyId.toString(), request.routeProfile());
        return saved;
    }

    public List<FavoriteView> favorites(String username) {
        return support.jdbc().query(
                """
                SELECT f.id,f.history_id,f.route_profile,f.name,h.result_json::text result_json,f.created_at
                FROM route_favorite f JOIN route_history h ON h.id=f.history_id
                WHERE f.user_id=? ORDER BY f.created_at DESC
                """,
                (rs, row) -> new FavoriteView(
                        rs.getObject("id", UUID.class), rs.getObject("history_id", UUID.class),
                        rs.getString("route_profile"), rs.getString("name"),
                        support.readJson(rs.getString("result_json")), rs.getObject("created_at", OffsetDateTime.class)),
                support.userId(username));
    }

    @Transactional
    public void removeFavorite(UUID id, String username) {
        int changed = support.jdbc().update("DELETE FROM route_favorite WHERE id=? AND user_id=?", id, support.userId(username));
        if (changed == 0) throw support.notFound("收藏不存在");
    }

    private RouteHistoryView historyRow(ResultSet rs) throws SQLException {
        return new RouteHistoryView(
                rs.getObject("id", UUID.class), rs.getObject("dataset_id", UUID.class),
                rs.getObject("start_node_id", UUID.class), rs.getObject("end_node_id", UUID.class),
                rs.getString("start_name"), rs.getString("end_name"), rs.getString("mobility_mode"),
                rs.getString("travel_period"), support.readJson(rs.getString("result_json")),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
