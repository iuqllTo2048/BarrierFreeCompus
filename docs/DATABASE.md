# DATABASE.md — v1.0 数据库与空间策略

## 1. 基本信息

- PostgreSQL 17 + PostGIS 3.5；schema 只由 Flyway 管理，当前版本 V7。
- 高德侧坐标为 GCJ-02。PostGIS 没有原生 GCJ-02 EPSG 编码，几何列统一使用 SRID 0。
- 坐标语义由 `dataset.coordinate_system=GCJ02` 明确保存，GeoJSON 同时携带 `coordinateSystem`，禁止声明为 EPSG:4326/WGS84。
- 地图快照通过 GIST 索引和 `geom && ST_MakeEnvelope(..., 0)` 做真实空间范围过滤。

## 2. 核心表

| 领域 | 表 | 用途 |
|---|---|---|
| 安全 | `app_user`、`refresh_token`、`audit_log` | 用户角色、SHA-256 刷新令牌摘要、认证/写操作审计 |
| 地图 | `campus`、`dataset` | 校园元数据与 Demo/Formal、坐标系、固定种子隔离 |
| 空间 | `building`、`building_entrance` | 建筑 Polygon 与入口 Point |
| 路网 | `route_node`、`route_edge` | A* 顶点与 LineString 边、方向和通行属性 |
| 设施/障碍 | `accessible_facility`、`barrier_report` | 设施 Point、审核状态、有效期与 Geometry |
| 用户闭环 | `user_accessibility_profile`、`facility_rating`、`facility_comment`、`facility_suggestion` | 出行偏好、评分、评论和建议 |
| 路线 | `route_history`、`route_favorite` | 结构化请求/结果 JSONB、用户收藏 |
| 配置 | `system_setting` | 障碍匹配和调度等白名单运行设置 |
| 智能体 | `ai_conversation`、`ai_message`、`ai_invocation_log`、`ai_tool_log`、`ai_action_draft` | 可见对话、脱敏调用/Tool 日志和两小时草稿 |

所有核心地图/业务对象都保存 `dataset_id`。Demo 与 Formal 通过 `dataset.is_demo` 与数据集外键隔离；管理 GeoJSON 导入和一键重置只允许 Demo。

## 3. 路网属性

`route_edge` 保存 `distance_m`、`slope_level`、`has_stairs`、`stairs_count`、`width_level`、`surface_type`、`lighting_level`、`bidirectional`、`status`、`risk_level`、`data_source`、`confidence_level` 和 LineString `geom`。`BLOCKED` 与其他非 ACTIVE 状态都不会进入可通行搜索。

障碍只有在 `review_status=APPROVED`、`active=true` 且未过期时影响路线；阻断型障碍会排除边，其他障碍增加成本。

## 4. Demo 数据与安全重置

- 数据集：`YUNLU_DEMO_V1`；固定种子 `20260818`。
- 种子数量：5 建筑、5 入口、20 节点、31 道路、15 设施、5 障碍。
- 来源统一为 `DEMO_GENERATED`，可信度为 `UNKNOWN`，不伪装成高可信实测。
- 五类场景：楼梯绕行、坡度冲突、动态封路、未知属性、设施偏好。

重置事务先以 `SELECT ... FOR UPDATE` 校验 `is_demo=true`；否则返回“只允许重置 Demo 数据集”。它只清理当前 Demo 的评分、评论、建议、路线历史和 `USER_REPORT` 障碍，恢复种子对象状态，并保留审计日志。Formal 数据不会进入清理 SQL。

## 5. Flyway 历史

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_security.sql` | 用户、刷新令牌和审计基础；演示账号 |
| V2 | `V2__fix_demo_credentials_and_refresh_token_index.sql` | 修复 Demo 凭据与刷新令牌索引 |
| V3 | `V3__map_data_model.sql` | PostGIS、地图/路网/设施/障碍表、约束和空间索引 |
| V4 | `V4__seed_yunlu_demo.sql` | 固定云麓校园 Demo 数据与五类场景 |
| V5 | `V5__add_blocked_route_edge_status.sql` | 增加道路动态阻断状态 |
| V6 | `V6__business_workflow.sql` | 资料、互动、上报、历史/收藏、设置与业务审计 |
| V7 | `V7__agent_assistant.sql` | 对话、消息、调用/Tool 日志和操作草稿 |

已执行迁移不允许回写；后续 schema 变更必须新增迁移。备份和恢复应包含数据库卷或 PostgreSQL 逻辑备份，不能只复制前端文件。
