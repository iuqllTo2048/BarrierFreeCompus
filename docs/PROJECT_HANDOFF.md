# BarrierFreeCampus 整项目开发交接文档

> 项目：无碍智行（BarrierFreeCampus）
> 更新日期：2026-08-23
> 当前分支：`main`
> 当前代码基线：`1cf48cf feat: 实现正式地图 GeoJSON 安全协作`
> 发布标签：`v1.0`，技术版本 `1.0.0`
> 远程仓库：`git@github.com:iuqllTo2048/BarrierFreeCompus.git`

## 1. 这份文档怎么用

本文面向接手项目的组员、开发者和 Codex，目标是让接手者无需依赖历史聊天即可完成以下工作：

- 在新电脑上克隆并启动整个系统；
- 理解用户端、管理端、A*、PostGIS、AI 与治理闭环；
- 安全维护 Formal 地图数据并与组员交换 GeoJSON；
- 知道应改哪个文件、运行哪些测试、哪些操作不能擅自执行；
- 区分已经实现的能力、演示数据和当前已知限制。

事实优先级如下：运行中的代码与迁移 > `docs/PROJECT_STATUS.md` > 本文 > `prompts/stages/` 历史计划。Stage Prompt 只能说明当时需求，不能证明功能已经实现。

## 2. 接手前必须遵守的规则

首先完整阅读根目录 `AGENTS.md`，然后阅读：

1. `PROJECT_SPEC.md`
2. `docs/PROJECT_STATUS.md`
3. `docs/DESIGN_DIRECTION.md`
4. `docs/DESIGN_SYSTEM.md`
5. 本文

重要安全边界：

- 不输出、复制到文档或提交 `.env` 中的高德 Key、AI Key、数据库密码和 JWT Secret。
- 不修改已经执行的 Flyway V1–V9；后续数据库变更从 V10 新建迁移。
- 未经用户明确确认，不重置 Demo、不删除 Formal 对象、不清空数据库、不删除卷、不 force push。
- 高德坐标是 GCJ-02；PostGIS 使用 SRID 0 保存几何语义，不能改写成 WGS84/EPSG:4326。
- 高德只负责底图和交互，核心路线必须继续使用自建 `route_node`、`route_edge` 与 A*。
- AI 可以关闭，AI 故障不得影响手工路线、审核、统计或地图维护。
- 前端继续使用 Vue 3 + Element Plus，不引入 Tailwind、shadcn 或另一套 UI 框架。

## 3. 当前真实状态

### 3.1 Stage 与 Git

- Stage 0–9 和 v1.0 发布已完成。
- v2.0 Stage 1 工程补全已完成：GeoJSON 备份恢复、后端服务拆分、管理地图组件拆分、治理洞察按需加载和 Node 22 对齐。
- v2.0 Stage 2 路线助手已完成：受控 Tool Calling、逐段 A* 合并、Yen Top-K 三候选和单路线地图切换；当前途经点上限已调整为 8 个。
- 管理地图已补充统一对象删除接口，设施在当前界面可直接删除。
- 上述功能已提交并推送 `origin/main`；接手时仍应重新检查 `git status` 和远程状态。

### 3.2 当前运行状态

Docker Compose 的标准访问方式：

| 服务 | 地址 | 状态 |
|---|---|---|
| 前端 Nginx | `http://localhost:8080` | 启动后应可访问 |
| 后端直连 | `http://localhost:8081` | `docker compose ps` 应显示 healthy |
| 同源健康检查 | `http://localhost:8080/actuator/health` | 启动后应返回 `UP` |
| PostgreSQL/PostGIS | Compose 内部 `db:5432` | `docker compose ps` 应显示 healthy |

运行状态和持久卷对象数量属于机器现场状态，不能写死在交接文档中。接手时通过 `docker compose ps`、健康接口和管理端数据集快照核对；Formal 数据不得擅自清理，旧云麓 Demo 只停用并保留。

### 3.3 已验证结果

- 后端：83/83 JUnit，通过真实 Testcontainers PostGIS 与 Flyway V1–V9。
- 前端：31/31 Vitest，TypeScript、ESLint、Prettier 和生产构建通过。
- 浏览器：7/7 Playwright Microsoft Edge Chromium 通过。
- 隔离 E2E 使用 18080/18081 和 tmpfs PostGIS，结束后自动销毁。
- Secret/私钥扫描为 0。
- Formal GeoJSON 全类型测试覆盖 5 建筑、5 入口、20 节点、31 道路、15 设施、5 障碍，共 81 个对象。

完整证据见 `docs/TEST_REPORT.md`。

## 4. 新电脑从零启动

### 4.1 克隆

```powershell
git clone git@github.com:iuqllTo2048/BarrierFreeCompus.git
Set-Location BarrierFreeCompus
git status
```
若 SSH 尚未配置，可由仓库所有者提供 HTTPS 地址；不要把私钥放进项目目录。

### 4.2 准备配置

```powershell
Copy-Item .env.example .env
```

编辑本地 `.env`，至少配置：

| 变量 | 必填 | 作用 | 缺失影响 |
|---|---:|---|---|
| `DB_PASSWORD` | 是 | PostgreSQL 与后端连接 | 服务无法连接数据库 |
| `JWT_SECRET` | 是 | Access Token 签名，至少 32 字节随机串 | 后端无法安全启动 |
| `AMAP_JS_KEY` | 是 | 高德 Web 端 JS API | 前端镜像构建失败或地图不可用 |
| `AMAP_SECURITY_JS_CODE` | 是 | 高德安全密钥 | 地图鉴权失败 |
| `SECURE_COOKIE` | 否 | 本地 HTTP 为 false，生产 HTTPS 为 true | 配错会导致 Refresh Cookie 不发送 |
| `AI_ENABLED` | 否 | 是否启用外部模型，默认 false | false 时使用 Mock |
| `AI_BASE_URL` | AI 启用时 | OpenAI-compatible 地址 | AI 启用时后端拒绝启动或调用失败 |
| `AI_API_KEY` | AI 启用时 | 模型服务 Key | AI 启用时调用失败 |
| `AI_MODEL_NAME` | AI 启用时 | Provider 实际模型 ID | AI 启用时调用失败 |

只需要高德“Web 端（JS API）”Key，不需要额外申请 Web 服务 Key。真实值只能保存在 `.env` 或部署平台 Secret，禁止提交 Git。

### 4.3 启动

```powershell
docker compose up -d --build
docker compose ps
```

首次启动会下载镜像并由 Flyway 自动执行 V1–V9。不要手工导入 SQL，也不要修改旧迁移。

### 4.4 登录与入口

| 角色 | 用户名 | 本地演示密码 | 默认入口 |
|---|---|---|---|
| USER | `demo_user` | `Demo@12345` | `/user` |
| ADMIN | `demo_admin` | `Admin@12345` | `/admin` |

账号只用于本地比赛演示，公开部署前必须替换或停用。

常用地址：

- 应用：`http://localhost:8080`
- Swagger：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 健康检查：`http://localhost:8080/actuator/health`

## 5. 系统能力边界

### 5.1 用户端已经实现

- 五种行动方式：轮椅、拐杖、临时受伤、推车/行李、步行。
- 起终点选择、白天/夜间、楼梯与设施偏好、路线权重。
- 最短、无障碍优先、综合三类路线；相同边序列会合并展示。
- 路线风险、坡度、楼梯、设施、障碍、可信度、成本与算法指标解释。
- 设施详情、评分、评论、建议。
- 障碍上报、我的上报、路线历史、收藏与个人偏好。
- 智能路线助手、SSE 进度、路线结果和障碍草稿确认。

### 5.2 管理端已经实现

- Formal/Demo 数据集查看与启停。
- 建筑、入口、节点、道路、设施、管理员障碍维护。
- 道路折线绘制、拖动、增删拐点、撤销、直线重置和键盘操作。
- 用户障碍审核、实地核验、设施建议、用户启停、审计与系统设置。
- 治理统计、建筑评分、地图/图表联动、CSV 与规则/AI 摘要。
- GeoJSON v2 导出、Formal 只读预检、冲突策略和安全合并。
- 导入前快照列表、恢复影响预览和一键恢复；业务引用与用户上报受保护。

### 5.3 明确未实现

- GPS 实时导航、偏航提醒、室内导航和跨楼层路径。
- 图片上传、OSS、多模态识别、RAG/pgvector。
- 实时天气、微信小程序、Redis、消息队列、微服务、高可用部署。
- 多人实时共同编辑；当前使用文件式 GeoJSON 交换。

## 6. 总体架构与请求链

```text
浏览器 Vue 3
  ├─ Axios REST / Fetch SSE
  ↓
Nginx :8080
  ├─ 静态 SPA
  ├─ /api → Spring Boot
  └─ /_AMapService → 高德安全代理
  ↓
Spring Security + JWT
  ↓
Controller → Service → JdbcTemplate/MyBatis-Plus
  ├─ PostgreSQL 业务事务与 JSONB
  └─ PostGIS Geometry/GIST/空间过滤
```

这是单体应用，不是微服务。单体可以让比赛项目的认证、事务、A*、审核和统计共享一致的数据边界，降低部署复杂度。

### 6.1 路线完整请求链

```text
UserHomeView.vue
→ route-api.ts POST /api/routes/plan
→ RoutingController
→ RoutingService.plan
→ RoutingRepository.loadGraph
→ PostgreSQL/PostGIS 读取节点、边、沿途设施和生效障碍
→ YenTopKRouter → AStarRouter.search + RouteCostPolicy.evaluate
→ RoutingDtos.RoutePlanResponse
→ 前端路线卡片 + CampusMap LineString
```

成功规划后，`RoutingController` 通过 `BusinessService` 保存当前用户路线历史，收藏功能引用历史中的指定 Profile。

### 6.2 AI 完整请求链

```text
UserAssistantView.vue
→ agent-api.ts Fetch SSE
→ AgentController / AgentService
→ AgentSafetyPolicy + classpath 系统提示词
→ LangChain4j 受控调用 ControlledAgentTools 白名单工具
→ 地点校验 / RouteItineraryService / RoutingService / 草稿仓库
→ 已验证事实结果
→ LangChain4jAiGateway 仅做高层解释
→ SSE delta / tool_result / route_result / barrier_draft
```

模型不会直接决定路线，也没有 SQL、Shell、删除、审核、角色修改或重置 Tool。障碍写操作先生成两小时草稿，用户确认后仍通过正常业务 REST 上报并等待管理员审核。

## 7. A* 与无障碍路线核心

### 7.1 图模型

- `route_node` 是图的顶点，保存 GCJ-02 Point、类型、状态和可信度。
- `route_edge` 是图的边，保存起终点、完整 LineString、方向、距离、坡度、楼梯、宽度、路面、照明、状态、风险和可信度。
- 双向道路在 `RouteGraph.GraphData.of` 中产生正反两个 Arc；单向道路只产生正向 Arc。
- 道路保存时，后端根据完整折线逐段计算 `distance_m`，不信任前端距离。

### 7.2 A* 中的 g、h、f

- `g(n)`：从起点到当前节点的累计成本，包含距离、坡度、楼梯、宽度、路面、夜间照明、障碍、未知可信度和设施偏好。
- `h(n)`：当前节点到终点的 Haversine 直线距离乘距离权重，并用图中最小“道路距离/直线距离”比例保证可采纳性。
- `f(n)=g(n)+h(n)`：`PriorityQueue` 优先展开估计总成本最低的状态。

实现位置：

- `backend/src/main/java/cn/barrierfreecampus/routing/AStarRouter.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RouteCostPolicy.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingService.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingRepository.java`

### 7.3 三种 Profile 与安全规则

- `SHORTEST`：更偏向距离，但仍遵守硬阻断和行动方式约束。
- `ACCESSIBLE`：显著提高陡坡、楼梯、窄路、未知数据和障碍成本。
- `BALANCED`：在距离和无障碍风险之间折中。

只有 `ACTIVE` 道路进入正常搜索。施工、临时封闭、车辆阻挡、入口关闭等硬阻断会排除道路；轮椅模式强制排除楼梯。`UNKNOWN` 不等于安全，会产生适度风险惩罚。

若严格偏好没有路线，系统会尝试放宽软偏好并标记为“风险最低可达路线”；硬阻断与轮椅楼梯约束不会被放宽。

## 8. 地图、PostGIS 与 GeoJSON

### 8.1 高德与自建数据的分工

`CampusMap.vue` 使用高德 JS API 2.0 提供底图、缩放、点选、覆盖物、热力图和 `PolylineEditor`。建筑、入口、设施、障碍、道路和路线几何都来自后端数据库，不使用高德路线服务替代 A*。

默认 Formal 校园：

- 校园代码：`SCHOOL_EXAMPLE`
- 数据集代码：`SCHOOL_EXAMPLE_V1`
- 中心：经度 `104.695359`、纬度 `31.534827`
- 坐标：GCJ-02

### 8.2 GeoJSON v2 组员协作

推荐流程：

```text
管理员编辑地图
→ 导出 GeoJSON v2
→ 通过群文件/网盘/Git 数据目录传递
→ 对方选择相同 datasetCode
→ 只读预检
→ 检查新增/相同/冲突/错误
→ 选择 KEEP_TARGET 或 OVERWRITE
→ 确认安全合并
```

导出包含 BUILDING、ENTRANCE、NODE、EDGE、FACILITY、管理员地图障碍。它不包含账号、路线历史、评分、评论、AI 会话或普通用户 `USER_REPORT` 障碍。

Formal 导入安全规则：

- 仅 MERGE，文件缺失的本地对象不会删除。
- 同类型同 `externalId` 相同则跳过。
- 内容冲突默认 `KEEP_TARGET`，管理员可明确选择 `OVERWRITE`。
- 预检返回文件指纹和目标指纹；目标在预检后变化则应用返回 409。
- 应用前写入 `geojson_import_backup` JSONB 快照，并在同一事务完成合并和审计。
- 道路距离始终由后端重新计算。

旧 Demo GeoJSON v1 POST 接口保留兼容，但仍禁止写入 Formal。GeoJSON 交换数据不要求重新 clone 整个项目，也不是自动实时同步。

## 9. 用户—管理员治理闭环

1. 用户提交障碍，初始为 `PENDING`、LOW，审核前不影响路线。
2. 不同用户在限定时间和空间内提交同类障碍，可关联并进入 `NEEDS_VERIFICATION`/MEDIUM 候选。
3. 管理员审核通过后，障碍才可能激活并影响下一次 A*。
4. 只有明确实地核验才能授予 HIGH。
5. 生效障碍到期后由 `BarrierExpiryScheduler` 自动停用。
6. 全部关键写操作进入 `audit_log`。

设施评分、评论和建议同样按当前用户隔离；管理员在治理工作台处理建议、用户、设置和审核。

## 10. 数据库与 Flyway

| 迁移 | 主要内容 |
|---|---|
| V1 | 用户、Refresh Token、审计和演示账号 |
| V2 | 修复演示凭据与 Refresh Token 索引 |
| V3 | PostGIS；校园、数据集、建筑、入口、节点、边、设施、障碍和互动表 |
| V4 | 固定云麓 Demo 与五类演示场景 |
| V5 | 道路 `BLOCKED` 状态 |
| V6 | 用户偏好、路线历史/收藏、设置和障碍匹配字段 |
| V7 | AI 对话、消息、调用日志、Tool 日志与操作草稿 |
| V8 | 新建学校 Formal 空白数据集并停用旧 Demo |
| V9 | Formal GeoJSON 导入前 JSONB 备份、双指纹和冲突策略 |

核心空间表使用 GIST 索引。地图快照通过 `geom && ST_MakeEnvelope(...,0)` 做 bbox 过滤；设施和障碍通过 `ST_DWithin` 与道路关联。

数据库结构只能由 Flyway 演进。不要把导出的 GeoJSON 当作完整数据库备份；正式备份还应包含 PostgreSQL 逻辑备份或 Docker 数据卷备份。

## 11. 认证与安全

- Access Token：JWT HMAC，默认 15 分钟，前端保存在当前会话存储中并通过 Bearer 发送。
- Refresh Token：32 字节随机值，浏览器 HttpOnly/SameSite=Lax Cookie；数据库只存 SHA-256 摘要，默认 7 天并在刷新时轮换。
- `JwtAuthenticationFilter` 每次请求都会查询启用用户与实际角色，禁用用户后旧 Access Token 也不能继续获得权限。
- `/api/admin/**` Controller 使用 `@PreAuthorize("hasRole('ADMIN')")`。
- API 使用统一 `ApiResponse` 和全局异常处理，不向用户暴露 SQL、密钥或异常栈。
- 外部模型请求/响应日志关闭，业务日志只保存脱敏摘要，不保存隐藏思维链。

## 12. 前端结构与设计约束

主要路由：

| 页面 | 文件 | 作用 |
|---|---|---|
| `/login` | `LoginView.vue` | 登录 |
| `/user` | `UserHomeView.vue` | 地图优先路线规划 |
| `/user/services` | `UserServicesView.vue` | 设施、上报、历史、收藏、个人中心 |
| `/user/assistant` | `UserAssistantView.vue` | SSE 智能路线助手 |
| `/admin` | `AdminDashboardView.vue` | 地图数据维护与 GeoJSON |
| `/admin/governance` | `AdminGovernanceView.vue` | 审核、用户、设置与审计 |
| `/admin/analytics` | `AdminAnalyticsView.vue` | 治理统计、图表、CSV 与建议 |

`router/index.ts` 负责 USER/ADMIN 守卫；`stores/auth.ts` 管理认证恢复，`stores/map-data.ts` 管理数据集和地图快照。Axios 拦截器负责 Bearer 与单次 Refresh 重试；SSE 因流式读取使用 Fetch。

UI 使用“静谧导览”设计系统：系统中文字体、深青绿主色、蓝绿辅助色、克制动效、深色模式、375px 响应式、可见焦点和 `aria-live`。风险不能只用颜色表达。UI 修改前必须阅读项目 `barrier-free-ui` Skill。

ECharts 只按需注册图表，提供条形、折线和堆叠可信度图；深浅色使用语义调色板，并启用 aria 与纹理辅助。

## 13. 目录与修改入口

```text
backend/
  src/main/java/cn/barrierfreecampus/
    auth/          登录、JWT、Refresh Token
    security/      Spring Security 与 JWT Filter
    mapdata/       数据集、地图 CRUD、GeoJSON v1/v2、导入备份恢复
    routing/       图模型、成本策略、A*、Yen Top-K、途经点编排与路线接口
    business/      用户服务、审核、历史、收藏、设置（门面 + 领域服务）
    agent/         SSE、受控白名单 Tool、LangChain4j、Mock
    analytics/     统计、建筑评分、CSV、治理摘要（门面 + 领域服务）
    common/        统一响应与异常
  src/main/resources/db/migration/  Flyway V1–V9
  src/test/                         JUnit/Testcontainers

frontend/
  src/components/  CampusMap、EChartPanel、图标与状态组件
  src/views/       用户端和管理端页面
  src/services/    REST/SSE、几何、图表、主题与视觉语义
  src/stores/      Pinia 认证与地图状态
  src/types/       API TypeScript 类型
  e2e/             Playwright 发布候选流程

docs/              规格、设计、API、数据库、部署、测试、说明书
scripts/           数据库备份、隔离 E2E 与安全扫描
docker-compose.yml 正式本地演示编排
```

常见需求对应入口：

| 需求 | 先看文件 |
|---|---|
| 改路线成本 | `RouteCostPolicy.java`、相关测试 |
| 改 A* | `AStarRouter.java`、`AStarRouterTest.java`、性能测试 |
| 改地图对象/GeoJSON | `MapDataService.java`、`MapObjectService.java`、`GeoJsonImportService.java`、`AdminMapController.java`、`map-api.ts` |
| 改折线编辑 | `AdminDashboardView.vue`、`CampusMap.vue`、`map-geometry.ts` |
| 改用户上报/审核 | `BusinessService.java`、`BarrierGovernanceService.java`、两个 Business Controller |
| 改 AI | `AgentService.java`、`ControlledAgentTools.java`、`AgentTools.java`、`LangChain4jAiGateway.java`、运行时提示词 |
| 改治理统计 | `AnalyticsService.java`、`AnalyticsQueryService.java`、`AdminAnalyticsView.vue`、`analytics-charts.ts` |
| 改权限 | `SecurityConfig.java`、`JwtAuthenticationFilter.java`、router |

## 14. 开发、测试与发布命令

后端最小相关测试后再跑全量：

```powershell
Set-Location D:\BarrierFreeCampus\backend
mvn -q test
```

前端：

```powershell
Set-Location D:\BarrierFreeCampus\frontend
npm ci
npm run test
npm run typecheck
npm run lint
npm run format:check
npm run build
```

隔离浏览器与安全门禁：

```powershell
Set-Location D:\BarrierFreeCampus
powershell -ExecutionPolicy Bypass -File scripts\run-e2e.ps1
powershell -ExecutionPolicy Bypass -File scripts\security-scan.ps1
```

E2E 会构建独立 Compose 项目并使用临时数据库，不能改成直接对 8080 Formal 数据执行写操作。

发布前检查：

```powershell
docker compose up -d --build
docker compose ps
git status --short
git diff --check
```

## 15. Git 与组员协作

推荐步骤：

1. 开始前 `git pull --ff-only`，检查 `git status --short`。
2. 不覆盖组员未提交改动；一个提交只包含一个明确功能。
3. Stage 完成并由用户验收后创建中文提交。
4. 推送前确认 `.env`、Key、证书、数据库文件和构建产物没有进入暂存区。
5. 代码通过 Git 同步；地图业务数据通过 GeoJSON v2 或数据库备份同步，两者不要混淆。

不要依赖本文记录的提交同步状态。每次协作前使用 `git fetch`、`git status --short --branch` 和 `git log --oneline --decorate -5` 核对当前分支、远程差异与工作区改动。

## 16. 已知限制与接手风险

- Formal 是否形成可规划路网取决于当前持久卷数据；用户端无路线时先核对端点是否接入连通路网。
- Demo 数据是固定生成数据，不代表真实实测或无障碍认证。
- 管理端建筑创建目前使用点选位置生成简化 Polygon，不是专业测绘工具。
- GeoJSON 是离线文件协作，不提供实时多人锁或自动同步。
- Playwright 只验证 Microsoft Edge Chromium，没有宣称 Firefox/WebKit 完整兼容。
- 治理洞察已按需加载地图和 ECharts；ECharts 独立分包仍可能触发 Vite 大包提示，但不进入页面主包。
- 本地与 Docker 前端工具链统一使用 Node 22；以 `frontend/.nvmrc` 为准。
- 单机 Compose 不含 TLS、自动备份、监控、高可用和灾难恢复，不能直接等同生产部署。
- Java 21 测试存在 Mockito/Byte Buddy 对未来 JDK 动态 Agent 的提示，当前不影响测试。

## 17. Vibe Coding 审查提示

接手者应继续关注但不要擅自大改：

- `MapDataService`、`BusinessService`、`AnalyticsService` 较大，后续功能扩张时可在新 Stage 中按领域拆分，但当前不是假实现。
- 管理地图页面承担多类编辑与导入流程，继续加功能前应评估组件拆分，避免状态相互影响。
- 不要仅为了“架构高级”引入 Redis、消息队列、微服务或第三方路线引擎。
- 修改成本系数必须补充场景测试和答辩解释，不能只凭视觉结果调参。
- 文档版本号、迁移版本和测试数量变更后要同步更新，避免计划与代码冲突。
- 搜索 TODO、`any`、未用依赖和 Secret 时先审查，不要把合法说明文字误判为代码问题。

## 18. 学习优先级

### 必须学懂

- `RoutingService`、`AStarRouter`、`RouteCostPolicy`：能解释路线为何这样选。
- V3/V4/V8/V9 Flyway：能解释表结构、Demo/Formal 和安全导入。
- `SecurityConfig`、`JwtAuthenticationFilter`、`AuthService`：能解释登录和角色边界。
- `AdminDashboardView.vue`、`CampusMap.vue`：能维护地图编辑和折线路网。
- `BusinessService`：能解释上报审核为什么不会直接影响路线。

### 建议学懂

- `AgentService`、`AgentTools`：能解释 AI 只是入口和解释层。
- `AnalyticsService` 与 ECharts：能解释治理评分和可视化来源。
- `http.ts`、Pinia Store 和 Vue Router：能排查 401、状态恢复与角色页面。
- Docker Compose、Nginx 与高德安全代理：能独立启动和排查地图加载。

### 可暂不深究

- Element Plus 内部实现、ECharts 渲染器内部细节。
- JWT 加密库底层数学、PostGIS 索引内部算法。
- LangChain4j Provider SDK 内部序列化；先理解项目的 `AiGateway` 边界。

## 19. 答辩口头复述模板

“无碍智行解决的是校园普通地图缺少无障碍属性、临时障碍和治理闭环的问题。高德只作为 GCJ-02 底图，我们把建筑、节点、道路、坡度、楼梯、宽度、设施和障碍保存到 PostGIS，自建 A* 分别生成最短、无障碍优先和综合路线。成本中不只有距离，还包括坡度、楼梯、路宽、路面、夜间照明、动态障碍和数据可信度；轮椅楼梯与生效封路属于硬约束。用户上报不会直接改变路线，必须经过管理员审核，HIGH 只来自实地核验。智能助手通过白名单 Tool 调用同一套 A*，模型只负责理解和解释，失败时手工路线仍可用。Formal 地图可以导出 GeoJSON 给组员，导入前会预检冲突、校验双指纹、保存备份并且绝不自动删除本地对象。当前版本完成了比赛 v1.0，但真实校园仍需要持续测绘，系统也暂不包含室内导航、实时 GPS 和生产级高可用。”

## 20. 给下一位 Codex 的启动提示

可把以下内容作为新任务开场：

```text
你正在接手 D:\BarrierFreeCampus。先完整读取 AGENTS.md、PROJECT_SPEC.md、
docs/PROJECT_STATUS.md、docs/PROJECT_HANDOFF.md 和当前 Stage Prompt。
只以真实代码和运行状态为依据，不泄露 .env，不修改 Flyway V1–V9，
不清空 Formal 数据，不重置 Demo。开始前检查 git status、最近提交和 Docker 状态，
先给当前 Stage 的最小计划，得到确认后再编码；完成后运行匹配测试并等待验收。
```
