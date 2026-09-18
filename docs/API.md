# API.md — v1.0 接口

## 1. 通用约定

- Nginx 同源前缀：`/api`；后端直连为 `http://localhost:8081/api`。
- 成功与业务错误统一为 `{ "code": number, "message": string, "data": ... }`，同时使用真实 HTTP 状态码。
- Access Token 使用 `Authorization: Bearer <token>`；Refresh Token 使用 HttpOnly、SameSite=Lax Cookie。
- 除登录、刷新、退出、健康检查和 OpenAPI 外，接口都需要认证。`/api/admin/**` 只允许 ADMIN。
- 校验错误返回稳定中文提示，不暴露 SQL、密钥或异常栈。接口细节可在 `/swagger-ui.html` 查看。

## 2. 认证

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | 用户名/密码登录，返回 Access Token 并设置 Refresh Cookie |
| POST | `/api/auth/refresh` | Refresh Cookie | 轮换刷新令牌并签发新 Access Token |
| POST | `/api/auth/logout` | 公开 | 撤销当前 Refresh Token 并清理 Cookie |
| GET | `/api/auth/me` | USER/ADMIN | 当前用户名和角色 |

## 3. 地图、路网与路线

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/map/datasets` | USER/ADMIN | 仅列出启用数据集 |
| GET | `/api/map/datasets/{datasetId}/snapshot` | USER/ADMIN | 地图快照；可用 `bbox=minLng,minLat,maxLng,maxLat` 空间过滤 |
| POST | `/api/routes/plan` | USER/ADMIN | 在自建路网上规划并记录历史 |

路线请求包含 `datasetId`、`startNodeId`、`endNodeId`、`mobilityMode`、`travelPeriod` 和可选 `preferences`。行动方式为 `WHEELCHAIR / CRUTCH / TEMPORARY_INJURY / CART_LUGGAGE / WALKING`；时段为 `DAY / NIGHT`。后端使用 Yen Top-K 为三个 Profile 寻找不同的真实无环候选，去重后返回 1–3 条路线；路网不足时不会复制路线凑数。结果详见 [ALGORITHM.md](ALGORITHM.md)。

管理地图前缀 `/api/admin/map`：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/datasets` | 列出全部数据集，含停用项 |
| GET | `/datasets/{datasetId}/snapshot` | 管理快照，含停用对象 |
| PATCH | `/datasets/{datasetId}` | 启用/停用数据集，Body：`{"enabled":true}` |
| POST / PUT | `/datasets/{datasetId}/nodes[/{id}]` | 新增或编辑/停用节点 |
| POST / PUT | `/datasets/{datasetId}/edges[/{id}]` | 新增或编辑道路及中间折点 |
| POST | `/datasets/{datasetId}/buildings` | 新增建筑 |
| POST | `/datasets/{datasetId}/entrances` | 新增入口 |
| POST | `/datasets/{datasetId}/facilities` | 新增设施 |
| POST | `/datasets/{datasetId}/barriers` | 新增障碍 |
| DELETE | `/datasets/{datasetId}/{type}/{id}` | 删除地图对象；`type` 支持 nodes/edges/buildings/entrances/facilities/barriers，关联数据按服务端规则处理 |
| GET / POST | `/datasets/{datasetId}/geojson` | 导出 GeoJSON v2；POST 保留旧 Demo 幂等导入兼容 |
| POST | `/datasets/{datasetId}/geojson/preview` | 只读预检 Formal GeoJSON v2，返回统计、错误、冲突样例和双指纹 |
| POST | `/datasets/{datasetId}/geojson/apply` | 使用预检双指纹与 `KEEP_TARGET/OVERWRITE` 策略安全合并 |
| GET | `/datasets/{datasetId}/geojson/backups` | 列出该数据集的导入前快照 |
| POST | `/datasets/{datasetId}/geojson/backups/{backupId}/preview` | 预览恢复将删除、恢复及因业务引用而保留的对象 |
| POST | `/datasets/{datasetId}/geojson/backups/{backupId}/restore` | 校验当前指纹后恢复快照；变化冲突返回 409 |

GeoJSON v2 导出六类可共享地图对象，不包含普通用户上报与用户业务数据。Formal 文件必须为 `FeatureCollection`、`schemaVersion=2`、匹配 `datasetCode` 且声明 `coordinateSystem=GCJ02`；道路端点和建筑引用必须可解析。应用为不删除本地缺失对象的单事务 MERGE，应用前保存 JSONB 快照并校验目标没有在预检后变化。地图写操作写入审计日志。

## 4. 用户业务

前缀 `/api/business`，USER 和 ADMIN 均可访问，但历史、收藏和上报按当前用户隔离。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / PUT | `/profile` | 读取或保存默认出行偏好 |
| GET | `/facilities/{id}` | 设施详情 |
| PUT | `/facilities/{id}/rating` | 新增或更新当前用户评分 |
| POST | `/facilities/{id}/comments` | 发表评论 |
| POST | `/facilities/{id}/suggestions` | 提交信息/设施/维护建议 |
| POST | `/barriers` | 上报障碍；审核前不影响路线 |
| GET | `/barriers/mine` | 当前用户上报 |
| GET | `/history` | 路线历史 |
| DELETE | `/history/{id}` | 删除自己的历史及关联收藏 |
| POST | `/history/{id}/favorites` | 收藏历史中的指定 Profile |
| GET | `/favorites` | 当前用户收藏 |
| DELETE | `/favorites/{id}` | 取消自己的收藏 |

## 5. 管理业务

前缀 `/api/admin/business`，仅 ADMIN。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/overview` | 治理工作台概要 |
| GET | `/suggestions` | 设施建议列表 |
| PUT | `/suggestions/{id}` | 采纳或拒绝建议 |
| GET | `/barriers` | 障碍审核队列，可按状态筛选 |
| PUT | `/barriers/{id}/review` | 审核决定、实地核验与备注 |
| GET | `/users` | 用户列表 |
| PATCH | `/users/{id}` | 启用/禁用用户；禁用会撤销 Refresh Token |
| GET | `/audits` | 审计日志 |
| GET | `/settings` | 业务运行设置 |
| PUT | `/settings/{key}` | 更新白名单设置 |
| PATCH | `/map/{type}/{id}` | 软启停 building/entrance/node/facility/edge |
| POST | `/datasets/{id}/reset-demo` | 安全重置 Demo；Formal 请求被拒绝 |

## 6. 智能路线助手

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/agent/status` | USER/ADMIN | AI 是否启用、模式、Provider 与模型 |
| POST / GET | `/api/agent/conversations` | USER/ADMIN | 创建/列出自己的对话 |
| GET | `/api/agent/conversations/{id}` | USER/ADMIN | 读取自己的对话与消息 |
| POST | `/api/agent/conversations/{id}/messages/stream` | USER/ADMIN | SSE 消息与 Tool/路线事件 |
| PUT | `/api/agent/drafts/{id}/confirmed` | USER/ADMIN | 在正式上报成功后标记自己的草稿已确认 |
| GET | `/api/admin/agent/invocations` | ADMIN | 脱敏调用和 Tool 日志 |

助手路线 Tool 可接收起点、终点和最多 3 个有序途经点。模型只提交地点名称；后端完成歧义校验、逐段 A* 和完整 GeoJSON 合并。SSE 事件与安全边界见 [AGENT.md](AGENT.md)。

## 7. 治理洞察

前缀 `/api/admin/analytics`，仅 ADMIN。查询参数为 `datasetId`（必需）、`buildingId`、`from`、`to`、`facilityType`、`barrierType`、`confidenceLevel`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/overview` | 概要、建筑评分、设施分布、障碍空间/趋势、路线风险和可信度 |
| GET | `/export.csv` | 导出当前筛选统计的 UTF-8 BOM CSV |
| POST | `/ai-summary` | AI 解释已计算结果；关闭/失败时返回规则摘要 |

统计口径见 [ANALYTICS_METRICS.md](ANALYTICS_METRICS.md)。

## 8. 运维

| 路径 | 说明 |
|---|---|
| `/actuator/health` | 容器和人工健康检查 |
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | OpenAPI JSON |

前端 Nginx 同源转发 `/api`、`/actuator`、Swagger 和高德安全代理；智能体路径关闭代理缓冲以支持 SSE。
