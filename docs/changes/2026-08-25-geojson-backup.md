# 提交说明：feat: 新增 GeoJSON 导入备份与一键恢复

## 提交信息
`feat: 新增 GeoJSON 导入备份与一键恢复`

## 背景
交接文档点名：V9 迁移保存了导入前 JSONB 快照，但没有"一键恢复"界面，恢复需要设计和权限确认。用户已确认恢复语义。

## 改动内容
- 后端（mapdata 包）：
  - `AdminMapController` 新增 3 个管理员接口：备份列表、恢复预览、执行恢复。
  - `MapDtos` 新增 GeoJsonBackupView / RestorePreview / RestoreApplyRequest / RestoreResult。
  - 恢复逻辑：用导入前快照整体替换六类地图对象；USER_REPORT 障碍不删除；被评分/评论/建议/路线历史外键引用的设施、节点及其建筑保留并提示；预检指纹不一致返回 409。
  - 为支撑恢复功能，地图数据服务同步按领域拆分：MapDataService 门面 + MapSnapshotService（快照查询）+ MapObjectService（对象保存）+ GeoJsonExportService（导出）+ GeoJsonImportService（导入/预检/备份恢复）+ MapDataSupport（共享工具）。拆分与恢复同属本提交（恢复逻辑落在拆分后的 GeoJsonImportService）。
- 前端：
  - 管理端地图数据页新增「导入备份」入口与对话框：备份列表 → 预览恢复影响 → 确认恢复。
  - GeoJSON 导入流程抽为 `GeoJsonImportDialog.vue`，备份恢复抽为 `GeoJsonBackupDialog.vue`；`map-api.ts`、`types/map.ts` 增加对应 API 与类型。
  - 确认恢复按钮使用项目主色（绿色）、去掉第二层重复确认弹窗。
- 测试：MapDataIntegrationTest 新增 3 条（备份列表/预览/恢复、并发 409、业务数据保留）。

## 解决什么问题 / 新效果
管理端可以一键回滚"导入把地图数据改坏"的情况，且不会误删用户上报、评分、评论、路线历史等业务数据；导入/恢复全程有预览与二次确认。

## 验证
- 后端 74/74 JUnit 通过；Testcontainers 执行 Flyway V1–V9。
- 前端 typecheck / lint / test / build 通过。
- 无头浏览器冒烟：管理员地图数据页三个按钮（导出/导入/导入备份）正常。

## 注意
- `style.css` 与 `AdminDashboardView.vue` 同时承载了备份面板样式、顶栏折叠样式与按钮样式调整，因同一文件多主题无法按文件拆分，统一归入本提交。
- 不修改 Flyway V1–V9；不重置 Demo；不动 Formal 数据。
