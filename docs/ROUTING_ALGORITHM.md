# ROUTING_ALGORITHM.md — 兼容入口

v1.0 正式算法文档已迁移到 [ALGORITHM.md](ALGORITHM.md)。本文件保留以兼容早期链接。

以下为发布候选阶段说明；数值与边界以正式文档及源码为准。

## 路网与坐标

- 高德只提供 GCJ-02 底图和交互，路线计算读取启用数据集中的 `route_node`、`route_edge`、设施和生效障碍。
- 自建数据以 PostGIS SRID 0 保存，真实坐标语义由 `dataset.coordinate_system=GCJ02` 声明，不伪装为 WGS84。
- 双向道路生成两个 Arc；单向道路只生成正向 Arc。图在每次规划请求时重新读取，因此道路状态或动态障碍更新无需重启。

## A* 与候选路线

同一请求分别用 `SHORTEST`、`ACCESSIBLE`、`BALANCED` 运行 A*。启发函数是两节点球面直线距离乘距离权重，并按全图“道路标称距离 / 直线距离”的最小比例缩放，避免道路距离短于地理直线距离时高估。三次搜索完成后按有序边 ID 序列去重，并在 `equivalentProfiles` 中说明等价 Profile。

搜索输出展开节点数、访问边数、队列峰值、耗时微秒和总成本，仅用于解释与调试，不承诺跨机器性能一致。

## 硬约束

- 非 `ACTIVE`（含 `BLOCKED`）道路不可通行。
- `TEMPORARY_CLOSURE`、`CONSTRUCTION`、`VEHICLE_BLOCKING`、`ENTRANCE_CLOSED` 类型的已审核、生效且处于有效期内障碍不可通行。
- 轮椅模式遇到楼梯始终不可通行，风险降级也不会放松。
- 用户开启“避开楼梯”后先按硬约束搜索；无路时可放松该偏好并返回明确的风险最低可达路线警告。

## 等效距离成本

边成本统一在 `RouteCostPolicy` 中计算，单位理解为“等效距离米”：

`total = distance + slope + stairs + width + surface + lighting + barrier + uncertainty + facilityPreference`

- 距离、坡度和宽度分别乘用户权重，权重范围限制为 `0.5–2.0`。
- 坡度按 `GENTLE / MODERATE / STEEP / UNKNOWN` 和 Profile 设置不同倍率；`UNKNOWN` 可通行但有中等惩罚。
- 楼梯同时考虑 Profile、楼梯级数和行动方式；拐杖/临时受伤为 2 倍，推车为 3 倍，轮椅为不可通行。
- 窄路、砖石/砂石/泥土/未知路面增加成本；夜间才计算照明不足成本。
- 道路风险、生效但非阻断型障碍和 `LOW / UNKNOWN` 可信度分别计入风险与未知成本。
- 休息点、无障碍卫生间偏好通过“缺少目标设施的路段惩罚”表达，避免使用负边权，保持 A* 条件稳定。

`ACCESSIBLE` 对坡度、楼梯、窄路、路面、风险和未知数据最敏感；`SHORTEST` 主要优化距离；`BALANCED` 位于两者之间。具体常量以 `RouteCostPolicy.java` 为唯一事实来源。

## API 与结果

`POST /api/routes/plan` 接收数据集、起终点、五类行动模式、昼夜和偏好。结果包含 GeoJSON LineString、距离、估算时间、风险摘要、楼梯数、坡度汇总、沿途设施/障碍、可信度、成本明细、约束、警告、算法指标和边 ID。

时间是基于行动方式速度和楼梯的演示估算，不是医疗建议或实时导航承诺。起终点相同时返回零距离路线；所有 Profile 无路时返回空路线和中文原因。
