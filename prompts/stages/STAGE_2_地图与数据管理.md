# Stage 2 — 校园地图与数据管理

目标：地图、路网、设施、障碍、Demo 数据都能管理；暂不实现 A*。

先输出计划，等待确认。

## 外部配置
先检查高德当前官方 Web JS API 配置要求。
主动列出用户需申请/填写的变量，写入 `docs/EXTERNAL_CONFIG.md`。
不得硬编码真实 Key。

## Flyway 数据模型
增加：
- campus
- dataset
- building
- building_entrance
- route_node
- route_edge
- accessible_facility
- barrier_report
- facility rating/comment 基础表
- facility_suggestion
- 必要枚举/状态

所有核心地图业务数据绑定 dataset_id。

dataset：DEMO / FORMAL、enabled、coordinate_system、is_demo。

## 坐标
- 高德侧按 GCJ02
- 不错误标成 WGS84
- GeoJSON 伴随坐标元信息
- PostGIS/坐标策略写入 DATABASE.md

## 后台地图编辑
管理员可：
- 地图点击新增节点
- 编辑/停用节点
- 两节点创建道路
- 道路折线中间点
- 编辑道路属性
- 新增建筑/入口/设施/障碍
- 启停数据集

## Edge 属性
distance_m、slope_level、has_stairs、stairs_count、width_level、surface_type、lighting_level、bidirectional、status、risk_level、data_source、confidence_level、geometry/polyline。

## Demo
- 5 建筑
- 约 30 边
- 约 15 设施
- 若干障碍
- 固定随机种子坡度
- DEMO_GENERATED
- UNKNOWN confidence
- 人为保证后续 A* 五类演示场景

## GeoJSON
实现基础导出/导入；至少能导出当前 dataset 的节点/道路/设施，并安全重新导入 Demo 测试数据。导入必须校验格式和 dataset。

## 不做
A*、AI、GPS、图片、OSS。

## 验收
Demo 地图可见；管理员能新增节点/边/设施且刷新后仍存在；dataset 启停生效；GeoJSON 基础导入导出成功；PostGIS 至少有一项真实空间用途并有测试。

完成后停止。
