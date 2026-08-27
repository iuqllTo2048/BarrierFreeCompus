# 提交说明：feat: 管理端地图编辑器支持删除节点/道路并修复建筑保存

## 提交信息
`feat: 管理端地图编辑器支持删除节点/道路并修复建筑保存`

## 背景
用户采集真实路网数据时，编辑器只能新增/编辑对象，无法删除误建的节点/建筑/入口/设施/障碍；后端也没有删除接口。

## 改动内容
- 后端：
  - `AdminMapController` 新增统一 `DELETE /api/admin/map/datasets/{datasetId}/{type}/{id}`（type 支持 nodes/edges/buildings/entrances/facilities/barriers，供节点/道路删除与后台清理使用）。
  - `MapObjectService.deleteMapObject` 级联清理引用：
    - 节点：同时删除与之相连的道路；引用该节点的路线历史一并删除（收藏随历史级联），保证删除必定成功。
    - 建筑：级联删除其入口、设施，以及这些设施的评分/评论/建议。
    - 设施：级联删除其评分/评论/建议。
    - 障碍：解除 `matched_report_id` 引用后删除。
    - 道路、入口：直接删除。
  - 删除写入对应审计（NODE_DELETE / BUILDING_DELETE 等）。
  - `MapDataService` 门面透传。
- 前端：
  - `map-api.ts` 新增 `deleteMapObject(type)`。
  - `AdminDashboardView` 仅在**节点/道路**编辑表单的保存按钮下方新增"删除XX"按钮（仅编辑已有对象时显示），样式与保存按钮一致（主色、与保存按钮左对齐、间隔 12px），**点击直接删除、无二次确认**；建筑/入口/设施/障碍按用户要求不提供删除按钮。
  - 修复删除按钮错位：Element Plus 自带 `.el-button + .el-button { margin-left: 12px }` 优先级（0-2-0）高于普通类，需用 `.admin-map-page aside .editor-delete-button`（0-3-0）覆盖，删除按钮才能与保存按钮左对齐。
  - **修复建筑保存**：保存建筑时后端要求 `center` 字段，前端误传 `coordinate`，导致建筑无法保存；现改为同时传 `center`（后端仅认 `center`）。

## 解决什么问题 / 新效果
采集路网数据时：节点/道路可删除并级联清理引用；建筑可正常保存到地图；删除按钮与保存按钮左对齐、样式统一、无多余确认。

## 验证
- 后端 39/39（MapDataIntegrationTest）通过：删除节点连带删路与历史、建筑级联删入口/设施/评分。
- 前端 typecheck / lint / 31 Vitest / format / build 通过（Node 22）。
- 冒烟：创建节点+道路 → 删除节点后节点与道路均消失；创建建筑+入口+设施 → 删除建筑后三者均消失；按前端新载荷保存建筑成功；清理用户测试标的 2 个设施与 1 个障碍。

## 注意
- 删除是物理删除且不可恢复（节点/道路按钮无二次确认，操作前请确认对象正确）。
- 节点被路线历史引用时，相关历史记录会被一并删除。
- 清理用户测试数据后，学校数据集当前剩余：1 个节点、0 道路、0 建筑、0 设施、0 障碍。
