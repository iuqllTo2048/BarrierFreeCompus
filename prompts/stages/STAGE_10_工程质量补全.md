# STAGE_10 — v2.0 Stage 1 工程质量补全

> 目标：在 v1.0 与 Formal GeoJSON 协作基础上，补齐文档点名的工程缺口，不引入新业务大功能。
> 状态：子任务 ①②③④ 全部完成，等待用户统一验收。
> 技术版本：保持 `1.0.0`（工程补全不升主版本）；Git 分支：`main`。

## 范围（4 个子任务，逐个提交、逐个验收）

### ① GeoJSON 备份一键恢复（已完成）

- 后端：管理员接口
  - `GET /api/admin/map/datasets/{datasetId}/geojson/backups` — 备份列表
  - `POST /api/admin/map/datasets/{datasetId}/geojson/backups/{backupId}/preview` — 恢复预览
  - `POST /api/admin/map/datasets/{datasetId}/geojson/backups/{backupId}/restore` — 执行恢复
- 恢复语义（用户已确认）：用导入前快照整体替换该数据集的六类地图对象（建筑、入口、节点、道路、设施、管理员障碍）；
  用户上报（USER_REPORT）、评分、评论、建议、路线历史等业务数据不动。
- 保护规则：被业务数据外键引用的设施/节点及其关联建筑保留，预览与结果中明确列出；USER_REPORT 障碍永不删除。
- 并发保护：预检返回当前指纹，恢复时指纹不一致返回 409，要求重新预检。
- 前端：管理端地图编辑器新增「导入备份」入口，备份列表 → 预览恢复影响 → 二次确认 → 执行。
- 测试：MapDataIntegrationTest 新增 3 条（列表/预览/恢复、409 并发、业务数据保留），后端 74 条全绿。

### ② 后端服务拆分（待开始）

### ② 后端服务拆分（已完成，待验收）

- `MapDataService`（原 1334 行）拆为门面 + `MapSnapshotService`（快照查询）+ `MapObjectService`（对象保存）+ `GeoJsonExportService`（导出）+ `GeoJsonImportService`（导入/预检/备份恢复）+ `MapDataSupport`（共享工具）。
- `BusinessService`（原 620 行）拆为门面 + `UserProfileService` + `FacilityInteractionService` + `BarrierGovernanceService` + `UserDataService` + `AdminGovernanceService` + `BusinessSupport`。
- `AnalyticsService`（原 439 行）拆为门面 + `AnalyticsQueryService`（统计查询）+ `BuildingScoreService`（建筑评分）+ `AnalyticsCsvService`（CSV）。
- Controller 调用与 DTO 完全不变；重构后 74 条 JUnit 全绿，Docker 重建后后端 healthy。

### ③ 管理地图页面组件拆分（待开始）

### ③ 管理地图页面组件拆分（已完成，待验收）

- `AdminDashboardView.vue`（约 1130 行 → 942 行）拆分出：
  - `GeoJsonImportDialog.vue`（文件选择、预检、合并导入对话框）
  - `GeoJsonBackupDialog.vue`（备份列表、恢复预览、一键恢复对话框）
- 地图编辑核心（节点/道路/对象表单）仍保留在页面内，与地图交互强耦合，继续加功能前再评估是否需要进一步拆分。
- 视觉与交互完全不变；已按 `barrier-free-ui` Skill 约束实现。

### ④ AdminAnalyticsView 分包优化（待开始）

### ④ AdminAnalyticsView 分包优化（已完成，待验收）

- 根因：治理洞察页静态引入 `CampusMap`（高德地图）与 `EChartPanel`（ECharts），全部打进首包。
- 改为 `defineAsyncComponent` 按需异步加载后：`AdminAnalyticsView` 主包 651.66kB → 111.55kB（gzip 218.20 → 33.75kB），ECharts 与地图拆为独立分包按需加载。
- 验证：typecheck/lint/test/build 全绿，无头浏览器打开治理洞察页渲染正常、无 JS 错误。

## 硬约束

- 不修改 Flyway V1–V9；不重置 Demo、不删除 Formal 数据；不引入 Redis/队列/微服务/第三方路线引擎。
- `.env` 与密钥不进入 Git；推送前确认暂存区。
- 每个子任务完成即停，等待用户验收后再继续。
