# API.md — v0.2.0 接口

统一响应为 `{ "code": 0, "message": "成功", "data": ... }`；错误同时使用真实 HTTP 状态码且不暴露堆栈或密钥。认证仍使用 Bearer Access Token 和 HttpOnly Refresh Cookie。

## 用户地图

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/map/datasets` | USER/ADMIN | 仅返回已启用数据集 |
| GET | `/api/map/datasets/{datasetId}/snapshot?bbox=minLng,minLat,maxLng,maxLat` | USER/ADMIN | 获取空间范围内地图快照 |

快照包含 dataset、buildings、entrances、nodes、edges、facilities 和 barriers。数据集停用后用户接口不再暴露该数据集。

## 管理地图

所有接口前缀为 `/api/admin/map`，仅 ADMIN 可访问。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/datasets` | 包含停用项的数据集列表 |
| GET | `/datasets/{datasetId}/snapshot` | 管理地图快照 |
| PATCH | `/datasets/{datasetId}` | 启用/停用数据集，Body：`{"enabled":true}` |
| POST / PUT | `/datasets/{datasetId}/nodes[/{id}]` | 新增或编辑/停用节点 |
| POST / PUT | `/datasets/{datasetId}/edges[/{id}]` | 新增或编辑道路及中间折点 |
| POST | `/datasets/{datasetId}/buildings` | 新增建筑 |
| POST | `/datasets/{datasetId}/entrances` | 新增入口 |
| POST | `/datasets/{datasetId}/facilities` | 新增设施 |
| POST | `/datasets/{datasetId}/barriers` | 新增障碍 |
| GET | `/datasets/{datasetId}/geojson` | 导出节点、道路、设施 |
| POST | `/datasets/{datasetId}/geojson` | 校验并幂等导入 Demo GeoJSON |

地图写操作会记录 `audit_log`。导入要求 `FeatureCollection`、匹配的 `datasetId`、`coordinateSystem=GCJ02`，且仅接受 node、edge、facility；道路引用的端点必须存在。

## 认证与运维

- `POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /api/auth/me`
- 健康检查：`GET /actuator/health`
- Swagger：`/swagger-ui.html`
- OpenAPI JSON：`/v3/api-docs`
