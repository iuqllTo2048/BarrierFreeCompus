# ALGORITHM.md — v1.0 可解释 A* 无障碍寻路

## 1. 数据与职责边界

高德 JS API 只提供 GCJ-02 底图和交互，核心路线不调用高德路径规划。后端每次规划都从启用数据集读取 `route_node`、`route_edge`、沿途设施，以及已审核、生效且未过期的障碍，构建有向图。双向道路生成两个 Arc，单向道路只生成正向 Arc，因此道路或障碍状态修改后不需要重启。

PostGIS 几何统一使用 SRID 0，真实语义由 `dataset.coordinate_system=GCJ02` 保存，绝不声明为 WGS84。

## 2. 三类路线与 Yen Top-K

同一请求分别以 `SHORTEST`、`ACCESSIBLE`、`BALANCED` 权重生成候选：

- `SHORTEST`：距离优先，对风险与障碍保留较低但非零惩罚。
- `ACCESSIBLE`：对坡度、楼梯、窄路、粗糙路面、风险和未知数据最敏感。
- `BALANCED`：在距离和无障碍成本之间折中。

单次 A* 只求当前权重下的最优路线。`YenTopKRouter` 在其上按根路径逐点偏离，临时禁用已采用的有向 Arc 和根路径节点，再调用 A* 求剩余 Spur Path，从而为每个 Profile 生成最多 3 条按总成本排序的不同无环候选。

最终选择仍按有序边 ID 序列去重：最短、无障碍优先、综合三个 Profile 依次选择各自成本最低且尚未展示的候选。如果某个 Profile 的第一候选与已有路线相同，会选择其下一条真实候选；若路网没有其他可达路线，才通过 `equivalentProfiles` 合并，不复制路线或制造绕圈。因此接口返回 1–3 条真实路线，路网存在足够替代路径时返回三张可切换路线卡。

## 3. 硬约束与风险降级

- 非 `ACTIVE` 道路不可通行。
- `TEMPORARY_CLOSURE`、`CONSTRUCTION`、`VEHICLE_BLOCKING`、`ENTRANCE_CLOSED` 生效障碍不可通行。
- 轮椅模式永远不能通过楼梯；风险降级也不会放松。
- 用户启用“避开楼梯”时先按硬偏好搜索；若无路，可放松该偏好并返回“风险最低可达路线”警告。
- 停用或不存在的起终点、断开图会返回无路，不伪造连线。

## 4. 成本模型

每条边在 `RouteCostPolicy` 中计算非负的等效距离成本：

```text
total = distance + slope + stairs + width + surface
      + lighting + barrier + uncertainty + facilityPreference
```

- 距离、坡度、宽度分别乘用户权重，接口限制为 `0.5–2.0`。
- 坡度区分 `FLAT / GENTLE / MODERATE / STEEP / UNKNOWN`。
- 楼梯结合 Profile、级数和行动方式；拐杖/临时受伤为 2 倍，推车/行李为 3 倍。
- 窄路、砖石/砂石/泥土/未知路面计入成本；照明不足只在夜间计入。
- 道路风险、非阻断型障碍和 `LOW / UNKNOWN` 可信度分别计入风险或未知惩罚。
- 设施偏好通过“缺少休息点/无障碍卫生间的路段惩罚”实现，不使用负边权。

具体数值常量以 `backend/src/main/java/cn/barrierfreecampus/routing/RouteCostPolicy.java` 为唯一事实来源。

## 5. 启发函数与正确性

启发函数为两节点 Haversine 球面距离 × 距离权重，再乘全图有效边中最小的“道路标称距离 / 端点直线距离”比例，并将比例上限限制为 1。这样即使演示道路的标称距离小于地理直线距离，也不会高估剩余成本。

A* 以当前最小估计总代价出队，维护节点最佳 `gScore` 和前驱 Arc；到达终点后回溯有序路径。起终点相同时返回零距离 LineString 和零成本结果。

## 6. 返回与解释

`POST /api/routes/plan` 返回 GeoJSON LineString、距离、估算分钟、风险摘要、楼梯数、坡度汇总、沿途设施/障碍、可信度、成本明细、约束、警告、展开节点数、访问边数、队列峰值、耗时微秒和边 ID。

时间只按行动方式速度与楼梯作演示估算，不是医疗建议、无障碍认证或实时导航承诺。

## 7. 验证基线

发布测试覆盖五种行动方式、楼梯/坡度/宽度/路面/夜间照明、UNKNOWN、四种硬阻断、五种软障碍、单向、同点、断路、偏好放松、多 Profile、Yen 三条无环候选、真实路线不足时不凑数及权重边界。固定 20×20 双向网格（400 节点）性能基线仍单独测量核心 A*；Top-K 会按候选偏离点多次调用 A*，当前校园小型路网由集成测试和真实页面验收覆盖。
