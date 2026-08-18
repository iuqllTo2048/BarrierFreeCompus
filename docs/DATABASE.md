# DATABASE.md — v0.2.0 数据库与空间策略

## 基本信息

- PostgreSQL 17 + PostGIS 3.5；仅由 Flyway 管理 schema，当前版本 v4。
- 高德侧坐标为 GCJ-02。PostGIS 原生没有 GCJ-02 EPSG 编码，因此几何列统一使用 SRID 0。
- 坐标语义由 `dataset.coordinate_system=GCJ02` 明确保存，GeoJSON 导出同时携带 `coordinateSystem`；不得把这些数据声明为 SRID 4326/WGS84。
- 地图快照使用几何列 GIST 索引和 `geom && ST_MakeEnvelope(..., 0)` 执行真实空间范围过滤。

## 表结构

| 表 | 用途 | 关键点 |
|---|---|---|
| `app_user` | 用户和角色 | 用户名唯一，角色为 USER/ADMIN |
| `refresh_token` | 刷新会话 | SHA-256 摘要唯一，可撤销/过期 |
| `audit_log` | 安全与数据审计 | 登录、令牌和地图写操作 |
| `campus` | 校园元数据 | 中心点与坐标系 |
| `dataset` | Demo/Formal 数据隔离 | 类型、启用状态、坐标系、Demo 标记、固定种子 |
| `building` | 建筑轮廓 | Polygon，绑定数据集 |
| `building_entrance` | 建筑入口 | Point，可达性与开放状态 |
| `route_node` | 自建路网节点 | Point，Stage 3 A* 顶点 |
| `route_edge` | 自建路网道路 | LineString，包含坡度、楼梯、宽度、路面、照明、状态和风险 |
| `accessible_facility` | 无障碍设施 | Point，类型与开放状态 |
| `barrier_report` | 障碍事件 | Geometry，类型、审核状态和有效状态 |
| `facility_rating` | 设施评分基础 | 用户/设施唯一评分 |
| `facility_comment` | 设施评论基础 | 审核状态 |
| `facility_suggestion` | 设施建议基础 | 工作流状态 |

所有核心地图对象都含 `dataset_id`；对象外部编号在数据集内唯一。Demo 与 Formal 通过数据集隔离，Stage 2 的 GeoJSON 写入只允许 Demo 数据集。

## 道路属性

`distance_m`、`slope_level`、`has_stairs`、`stairs_count`、`width_level`、`surface_type`、`lighting_level`、`bidirectional`、`status`、`risk_level`、`data_source`、`confidence_level` 和 LineString `geom` 均已落库。

## Demo 数据

- 数据集：`YUNLU_DEMO`，固定种子 `20260818`。
- 数量：5 建筑、5 入口、20 节点、31 边、15 设施、5 障碍。
- 来源：`DEMO_GENERATED`；可信度：`UNKNOWN`，不会伪装为高可信实测数据。
- 预置：楼梯绕行、陡坡冲突、道路临时关闭、未知属性和设施偏好五类场景。

## Flyway 历史

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_security.sql` | 安全表与演示账号 |
| V2 | `V2__fix_demo_credentials_and_refresh_token_index.sql` | 修复演示凭据与刷新令牌索引 |
| V3 | `V3__map_data_model.sql` | PostGIS 扩展、地图业务表、约束及空间索引 |
| V4 | `V4__seed_yunlu_demo.sql` | 云麓校园固定 Demo 数据 |

历史迁移一经执行不回写；后续变更必须新增迁移。
