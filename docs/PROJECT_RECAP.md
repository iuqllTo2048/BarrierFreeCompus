# PROJECT_RECAP.md — BarrierFreeCampus 从零到答辩复盘

> 复盘基线：v1.0 当前真实代码、Flyway V1–V7、前后端测试与 Docker 编排。`prompts/stages` 只作为需求留档，不作为“已经实现”的证据。

## 1. 一句话讲清项目

BarrierFreeCampus（无碍智行）不是“在高德地图上画几条线”，而是把校园无障碍信息变成自建路网属性，用可解释 A* 计算路线，再让用户上报、管理员审核和治理统计持续修正下一次规划结果。高德提供 GCJ-02 底图；路线决策权、数据可信度和审核权都在自己的后端与数据库里。

## 2. 当前真实能力

- 登录与权限：短期 Access JWT + 可轮换/撤销的 HttpOnly Refresh Cookie，USER/ADMIN 路由和接口双重保护；禁用用户立即失效。
- 地图数据：建筑、入口、节点、道路、设施、障碍全部持久化到 PostgreSQL/PostGIS，管理端可编辑、自定义点位和道路属性、导入/导出 Demo GeoJSON。
- 无障碍路线：五种行动方式、昼夜、避楼梯和权重偏好；输出最短、无障碍优先、综合路线，等价路径自动合并。
- 用户闭环：设施评分/评论/建议、障碍上报、历史、收藏和个人偏好。
- 管理闭环：障碍审核、可信度、用户启停、地图软停用、系统设置、审计、Demo 安全重置。
- 智能助手：自然语言调用地点搜索、真实 A*、设施/障碍查询、路线比较和障碍草稿；外部 AI 可关闭、可替换、可降级。
- 治理洞察：建筑评分、设施分布、障碍空间/趋势、路线风险、可信度、CSV 与规则/模型摘要。
- 发布质量：后端 JUnit/Testcontainers、前端 Vitest、Playwright E2E、类型/格式/构建、密钥扫描和隔离 Compose。

没有实现也不应声称实现的内容：实时室内定位、语音、多模态图片识别、正式无障碍认证、RAG/向量库、生产高可用、自动云备份、OSS、Redis、本地大模型、跨校园实测数据。

## 3. 代码目录怎么读

```text
backend/src/main/java/cn/barrierfreecampus/
  auth/          登录、JWT、Refresh Token 与 Cookie
  security/      Spring Security 过滤器和权限规则
  mapdata/       数据集、空间快照、地图 CRUD、GeoJSON
  routing/       RouteGraph、AStarRouter、RouteCostPolicy、路线 API
  business/      资料、互动、上报审核、历史收藏、Demo 重置
  agent/         对话、SSE、白名单 Tool、Gateway 与脱敏日志
  analytics/     统计 SQL、建筑评分、CSV 与治理摘要
  common/        统一响应和异常处理

frontend/src/
  views/         登录、用户路线/服务/助手、管理地图/治理/统计
  components/    CampusMap、空态、项目图标等复用组件
  stores/        认证与地图数据状态
  services/      REST/SSE、地图视觉、ECharts 和主题
  router/        懒加载路由与角色守卫
  types/         前后端契约类型

backend/src/main/resources/db/migration/   V1–V7 schema 与固定 Demo
frontend/e2e/                              发布候选浏览器流程
scripts/                                   E2E 编排与 Secret 扫描
docs/                                      设计、接口、数据、部署和交付事实
```

建议阅读顺序：`README.md` → `docker-compose.yml` → `application.yml` → Controller → Service/Repository → Flyway → 前端对应 service/view → 测试。这样能看到真实调用关系，避免先陷入 UI 细节。

## 4. 三条关键请求链

### 4.1 手工路线规划

```text
UserHomeView
  → route-api.ts POST /api/routes/plan
  → JwtAuthenticationFilter / RoutingController
  → RoutingService
  → RoutingRepository 从 PostGIS 读取节点、边、设施、生效障碍
  → RouteGraph 构图
  → AStarRouter × SHORTEST / ACCESSIBLE / BALANCED
  → RouteCostPolicy 计算成本和硬约束
  → BusinessService.recordHistory
  → GeoJSON + 风险/成本/解释
  → CampusMap 与路线结果卡片
```

重点：地图线不是高德规划接口返回的；高德只绘制后端 A* 产生的 GeoJSON。每次请求重新读图，所以管理员封路或障碍审核后，下一次规划立即变化。

### 4.2 用户上报到动态绕行

```text
UserServicesView 上报坐标/类型/描述
  → POST /api/business/barriers
  → BusinessService 做重复检测与近时空同类合并
  → PENDING/NEEDS_VERIFICATION，审核前 inactive
  → AdminGovernanceView 审核
  → APPROVED + active（HIGH 仅实地核验）
  → RoutingRepository 读取仍在有效期内的障碍
  → 阻断型排除道路，非阻断型增加 A* 成本
```

这条链是项目的业务记忆点：用户不是直接改路网，管理员审核不是孤立后台动作，而会真实影响路线。

### 4.3 智能助手

```text
UserAssistantView Fetch SSE
  → AgentController / AgentService
  → AgentSafetyPolicy
  → AgentTools 白名单
       searchCampusPlace → 数据库
       calculateAccessibleRoutes → RoutingService/A*
       compareRoutes → 确定性规则
       createBarrierReportDraft → ai_action_draft
  → AiGateway（Mock 或 LangChain4j）只负责解释
  → SSE delta/route_result/comparison/draft
```

模型不是拥有数据库权限的“超级管理员”。即使 Provider 失败，已经得到的 A* 和 Tool 结果仍保留；障碍也只生成草稿，用户确认后再走正常 REST 和审核。

## 5. A* 为什么适合这个项目

普通最短路只优化米数，但轮椅、拐杖和推车对楼梯、坡度、宽度、路面、照明和未知数据的代价不同。项目把边成本写成非负的“等效距离”：

```text
距离 + 坡度 + 楼梯 + 宽度 + 路面 + 照明
     + 障碍 + 不确定性 + 设施偏好
```

轮椅楼梯、非 ACTIVE 道路和四类生效阻断障碍是硬约束；其他风险是可解释惩罚。三 Profile 使用同一套图但不同敏感度。Haversine 启发函数再按全图最小道路/直线比例缩放，避免演示距离不严格等于地理直线时高估。

答辩时不要说“算法准确率 99%”。本项目验证的是规则、可达性和性能：固定 400 节点网格最终回归 P95 约 1.003ms；真实 Demo 只有 20 节点、31 道路。路线是否符合现实仍依赖数据采集和实地核验。

## 6. 数据和坐标最容易讲错的地方

- 高德使用 GCJ-02，但 PostGIS 没有 GCJ-02 的 EPSG 编号，所以几何列使用 SRID 0；`dataset.coordinate_system=GCJ02` 才是坐标语义。
- SRID 0 不等于“没有考虑坐标系”，更不能为了工具方便伪装成 4326/WGS84。
- `dataset.is_demo`、`data_source`、`confidence_level` 同时表达隔离、来源和可信度。Demo 来源固定为 `DEMO_GENERATED` 且不能 HIGH。
- Formal 保护在服务端：重置先锁定数据集并校验 `is_demo=true`，不是只靠前端隐藏按钮。
- Flyway V1–V7 是数据库事实历史。已经执行的 migration 不能回写；下一次结构变化必须加 V8。

## 7. 前端设计不是“套模板”

“静谧导览”把地图放在用户端首位：桌面左侧设置、中央地图、右侧结果；移动端通过按钮控制底部面板。三路线有名称和不同线型，风险同时使用文字/图标/颜色。管理端把地图作为主画布，检查器按需展开；治理图表与地图对象联动。

Element Plus 只提供基础交互，颜色、间距、圆角、深色、高德样式、Marker 和 ECharts 均按项目 Token 二次设计。没有引入 Tailwind/shadcn，也没有用装饰性特效掩盖数据不足。

## 8. Vibe Coding 里 AI 实际做了什么

代码库体现的是“AI 辅助实现 + 自动门禁”，不是“AI 自动保证正确”：

- AI 按 Stage 生成/修改 Java、Vue、SQL、Docker、测试和文档；Git 提交记录保存阶段边界。
- 设计 Skill 固化 UI 约束，复盘 Skill 强制回到真实源码，减少模板化和把计划当实现的风险。
- 可靠性来自可复现验证：Java 测试、真实 PostGIS 容器、Vitest、Playwright、类型检查、构建、Secret 扫描和人工验收。
- 仍需人负责：业务规则是否合理、地图数据是否实测、Key/域名是否安全、障碍审核是否真实、生产运维是否充分。

典型风险与对应防线：

| Vibe Coding 风险 | 本项目防线 |
|---|---|
| 接口和页面各写一套假数据 | 前端 service 调真实 REST；统计来自 PostgreSQL，空数据明确为空 |
| AI 越权写数据库 | 白名单 Tool；草稿后确认；Spring Security 和业务校验 |
| 路线看起来合理但规则错误 | `RouteCostPolicy` 集中常量，单元/集成/E2E 覆盖关键分支 |
| 坐标系混用 | dataset 显式 GCJ02，SRID 0，导入校验与文档约束 |
| 测试污染演示数据 | E2E 独立项目、端口和 tmpfs，finally 销毁 |
| 密钥进入仓库 | `.gitignore`、`.env.example`、Secret 扫描与运行时注入 |
| 文档停在旧 Stage | v1.0 最终对照 Controller、迁移、lockfile 和测试重写 |

## 9. 建议学习优先级

1. 先能画出三条请求链，理解 Controller/Service/Repository/View 各自职责。
2. 读 `RouteCostPolicy` 和 `AStarRouter`，能解释硬约束、非负成本和启发函数。
3. 读 V3、V4、V6，理解空间表、固定 Demo 与业务闭环。
4. 读 `SecurityConfig`、JWT 过滤器和 AuthService，理解 Access/Refresh 与角色边界。
5. 读 `AgentService`/`AgentTools`，区分模型解释与确定性业务工具。
6. 读 Playwright 和集成测试，学会用证据判断“功能完成”。

## 10. 三分钟答辩话术

“无碍智行解决的是校园里‘地图有路，但不一定能走’的问题。我们没有把核心路线交给通用地图服务，而是在 PostGIS 中建立带坡度、楼梯、宽度、路面、照明、风险和可信度的校园路网，再用可解释 A* 同时生成最短、无障碍优先和综合路线。轮椅遇到楼梯、封闭道路和已审核阻断障碍会被硬排除，其他风险进入成本明细，所以每条推荐都有原因。

用户可以上报临时障碍，但首次上报不会直接改路线；管理员审核通过后才生效，下一次 A* 会实时读取并绕行。管理端还能维护点和道路、查看建筑无障碍评分、障碍趋势、路线风险与数据可信度。智能助手只是自然语言入口，它只能调用地点搜索、A*、设施/障碍查询、路线比较和草稿这些白名单工具；模型限流时手工路线照常工作。

我们的 Demo 用固定种子复现楼梯绕行、坡度冲突、动态封路、未知数据和设施偏好，Demo 重置在后端校验 is_demo，Formal 数据不会受影响。发布通过了 65 个后端测试、29 个前端单元测试、6 个浏览器 E2E、Secret 扫描和隔离 Docker 验证。v1.0 是可演示的单机交付基线；真实上线还需要实地数据采集、域名与密钥配置、备份监控和高可用。”

## 11. v1.0 已知限制与下一步

- Demo 几何和风险是固定生成数据，不能当作真实校园无障碍结论。
- 只验证 Chromium Edge；Firefox/WebKit 与真实移动设备需要按部署目标补测。
- 高德真实域名白名单、外部模型限流和 Provider 合规需要部署现场验证。
- Compose 是单机基线，没有 TLS、自动备份、限流、集中日志或高可用。
- 治理洞察页面分包约 652kB（gzip 约 218kB），后续可继续按图表模块拆分。
- 下一版本最有价值的工作不是堆新框架，而是接入真实校园采集、建立核验流程、做可用性测试和生产运维加固。
