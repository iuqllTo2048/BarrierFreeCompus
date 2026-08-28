# TEST_REPORT.md — v1.0 发布验收报告

> 执行日期：2026-08-19
>
> 技术版本：`1.0.0`
>
> 结论：自动化发布门禁全部通过，正式 8080 演示环境健康，用户人工验收通过。

## 1. 测试矩阵

| 层级 | 覆盖 | v1.0 结果 |
|---|---|---|
| 路由算法 | 五种行动模式、楼梯、坡度、窄路、路面、UNKNOWN、夜间、封路、硬/软障碍、单向、无路、同点、Profile、偏好和权重 | 31 个路由/性能样例；归入 65 个 JUnit，全部通过 |
| 后端集成 | JWT 篡改、Refresh 轮换/重放/撤销、权限、校验、Flyway 空库、PostGIS、Demo/Formal、审核、AI Tool、统计与 CSV | 65 通过，0 失败、0 错误、0 跳过 |
| 前端单元 | Session、地图 Store、主题、SVG、设施/障碍视觉、路线语义、ECharts aria | 29 通过 |
| 前端静态 | TypeScript/Vue 模板、ESLint、Prettier、Vite production build | 全部通过 |
| 浏览器 E2E | USER 登录/路线、轮椅、XSS、ADMIN 权限、Mock AI、375px 导航/主题/面板 | Chromium Edge 6/6 通过 |
| 安全与依赖 | Git 可见 Secret/私钥/证书、npm 官方漏洞库、TODO/`any`/假实现、直接依赖使用 | Secret 0；漏洞 0；未发现阻塞项；移除 1 个冗余直接依赖 |
| 容器发布 | 全新 PostGIS、Flyway V1–V7、后端 health、Nginx `/api`、production image | 隔离环境通过并销毁 |
| 正式演示 | 保留数据库卷重建、健康启动顺序、Nginx/API、数据集可见 | db/backend healthy；前端 200；health UP；未登录 401 |

## 2026-08-27 用户端路线助手增量验证

- 后端全量 78/78 通过：包含真实 PostGIS、A*、受控工具可信上下文、工具审计、最近设施、简称“体健中心”、错别字“图叔馆”、草稿不写正式障碍和提示词注入拒绝。
- 前端 31/31 Vitest、TypeScript、ESLint、生产构建和本次助手页面 Prettier 检查通过；路线卡切换地图高亮使用现有结构化 `route_result`，不采用模型生成坐标。
- 用户路线页浏览器冒烟验证通过：初始隐藏路网和地点；开关打开后显示完整路网；地图选点仅显示可选地点；规划结果地图只显示当前选中路线，路线卡仍保留比较信息。
- 全仓 Prettier 检查仍报告 17 个既有格式偏差；其中本轮触及的 `CampusMap.vue`、`UserHomeView.vue` 和 `style.css` 在修改前已位于该列表，本轮未做无关的全文件批量格式化。
- 自动测试使用 `AI_ENABLED=false`，不会调用或伪造 DeepSeek。真实 Provider 的 5–10 秒目标、Tool Calling 兼容和降级提示列入人工验收。
- 本地已配置的 `deepseek-v4-flash` 完成真实联调：地点不存在时不猜测并请求确认；多轮确认后由 A* 返回无路线事实；连通端点“古茗 → 露天电影院”成功返回 663 米路线并直接绘制到地图。组合工具优化后整轮调用日志为 8,369ms，工具链为两次地点确认 + 一次 A*/比较/设施组合查询。
- 本轮统一的数据驱动地点模糊检索已按需求暂缓；现有基础别名兼容不作为模糊检索验收结论。
- 代码先通过本地 Vite（5173，代理现有 8081 后端）完成浏览器验收；Docker Engine 恢复后已使用保留原数据库卷的 `docker compose up -d --build` 重建正式环境。数据库与后端 healthy、8080 返回 200，正式 8080 再次通过路网开关、地图选点和单路线结果冒烟验收。
- 管理端设施删除入口增量验证：前端 31/31 Vitest、TypeScript、ESLint 和生产构建通过；正式 8080 点击已有设施后显示可用的“删除设施”按钮，已有设施字段为只读且不显示创建按钮。本次浏览器验收未删除现有设施。

## 2. A* 性能基线

固定 20×20 双向网格，共 400 节点；轮椅 + 无障碍优先，预热 20 次后采样 100 次。门槛 P95 < 250,000µs。

| 指标 | RC 复测 | v1.0 复测 |
|---|---:|---:|
| P50 | 620µs | 413µs |
| P95 | 2,067µs | 1,003µs |
| 最大值 | 8,309µs | 3,034µs |
| 结论 | 通过 | 通过 |

这些是当前机器的算法耗时，不是模型准确率、真实路线耗时或跨机器承诺。Demo 路网种子只有 20 节点、31 道路，显著小于基线。

## 3. 安全验证

- 未登录访问受保护 API 返回 401；USER 访问管理员接口和 Demo 重置返回 403。
- Refresh Cookie 为 HttpOnly、SameSite=Lax；刷新后旧令牌失效，退出后不可重放。
- 禁用用户会撤销 Refresh，JWT 过滤器每次请求复核数据库状态。
- 错误不回显密码、哈希、SQL、Key 或堆栈；XSS payload 只按文本展示。
- CSV 对 `= + - @` 开头内容添加单引号，防止公式注入。
- AI 默认关闭，白名单 Tool 不包含 SQL、Shell、删除、审核、角色或 Demo 重置；草稿不直接写正式障碍。
- `scripts/security-scan.ps1` 对 Git 可见文件扫描，结果 `SECURITY_SCAN_OK tracked secrets/private keys: 0`。
- npm 镜像站审计端点返回 404 后，改用官方 `https://registry.npmjs.org` 重新执行，结果 0 vulnerabilities。

## 4. Demo 与 Formal 保护

- 集成测试在真实 PostGIS 中验证 Formal 数据集调用重置会被拒绝，且原状态保持不变。
- Demo 重置测试验证清理业务数据、恢复种子对象、保留审计；前端操作前有明确二次确认。
- 本轮没有对 8080 持久卷执行重置。正式 Compose 重建发现 Demo 被此前手工停用后，只通过现有管理员 API 重新启用；已有 21 节点、6 障碍等用户修改全部保留。
- 五类固定场景的种子仍由 V4 定义；需要完全恢复时必须由管理员人工确认“安全重置 Demo”。

## 5. 容器隔离与正式部署

E2E 使用 `barrierfreecampus-e2e`、18080/18081 和 PostgreSQL tmpfs；脚本在 `finally` 执行 `down --volumes`。正式环境使用 `postgres-data`，`docker compose down` 不删除数据。

正式 Compose 已以 v1.0 镜像重建：PostgreSQL 和 backend 显示 healthy；`http://localhost:8080/` 返回 200，Nginx 与 8081 直连 health 均返回 `{"status":"UP"}`，未登录 `/api/map/datasets` 返回 401。数据库 schema 仍为 V7。

## 6. 构建观察与非阻塞限制

## 7. v2.0 Stage 1 子任务 ① 验证（2026-08-24）

### 新增覆盖

| 项目 | 结果 |
|---|---|
| 后端 JUnit | 74/74 通过（新增 GeoJSON 备份列表/恢复预览/执行恢复、409 并发保护、业务数据保留 3 条） |
| 前端 Vitest | 31/31 通过；vue-tsc、ESLint、Prettier、Vite production build 全部通过 |
| 恢复语义 | 快照整体替换六类地图对象；USER_REPORT 障碍不删除；有业务引用的设施/节点/建筑保留并提示 |
| 并发安全 | 预检后数据集指纹变化时恢复返回 409，不写入任何数据 |
| 审计 | 恢复写入 `audit_log`（`GEOJSON_BACKUP_RESTORE`） |

### 说明

- 本轮同时将前端 51 个文件同步到项目自身 Prettier 配置（此前仓库已存在格式偏差，与功能无关）。
- A* 性能基线、安全扫描、E2E 与正式 Compose 验收在子任务 ① 中未做回归改动，保留 v1.0 结果；②③④ 完成后统一回归。

## 8. v2.0 Stage 1 子任务 ② 验证（2026-08-24）

### 服务拆分

- `MapDataService` → `MapSnapshotService` + `MapObjectService` + `GeoJsonExportService` + `GeoJsonImportService` + `MapDataSupport`。
- `BusinessService` → `UserProfileService` + `FacilityInteractionService` + `BarrierGovernanceService` + `UserDataService` + `AdminGovernanceService` + `BusinessSupport`。
- `AnalyticsService` → `AnalyticsQueryService` + `BuildingScoreService` + `AnalyticsCsvService`。

### 结果

| 项目 | 结果 |
|---|---|
| 后端 JUnit | 74/74 通过（纯重构，无行为变化） |
| Controller/DTO | 公开方法签名不变，Controller 未改动 |
| 运行验证 | Docker 重建后 db/backend healthy，health `UP` |

### 附带修复：管理员根路径跳转

- `/` 根路径由固定 `/user` 改为按角色跳转（管理员 `/admin`，用户 `/user`），会话在挂载前恢复。
- 无头浏览器实测：`demo_admin` 打开根地址 → `/admin`；`demo_user` → `/user`。

## 9. v2.0 Stage 1 子任务 ③ 验证（2026-08-24）

### 组件拆分

- `AdminDashboardView.vue`（1130 → 942 行）拆分出 `GeoJsonImportDialog.vue` 与 `GeoJsonBackupDialog.vue`。
- 页面视觉与交互不变，导出/导入/备份入口保持不变。

### 结果

| 项目 | 结果 |
|---|---|
| 前端 Vitest | 31/31 通过 |
| 静态门禁 | vue-tsc、ESLint、Prettier、Vite production build 全部通过 |
| 冒烟验证 | 无头浏览器登录管理员后，导出/导入/导入备份三个按钮均存在 |
| 运行验证 | Docker 重建后前端 200 |

## 10. v2.0 Stage 1 子任务 ④ 验证（2026-08-24）

### 分包优化

- 治理洞察页 `CampusMap`（高德）与 `EChartPanel`（ECharts）改为 `defineAsyncComponent` 按需加载。
- `AdminAnalyticsView` 主包：651.66kB → 111.55kB（gzip 218.20kB → 33.75kB）。
- ECharts 拆为独立分包（539.89kB / gzip 184.63kB）按需加载，不再进入治理洞察首包。

### 结果

| 项目 | 结果 |
|---|---|
| 前端 Vitest | 31/31 通过 |
| 静态门禁 | vue-tsc、ESLint、Prettier、Vite production build 全部通过 |
| 冒烟验证 | 无头浏览器打开 `/admin/analytics` 渲染正常，0 JS 错误 |

- `AdminAnalyticsView` 为 651.66kB，gzip 218.20kB，Vite 发出 >500kB 警告；页面已路由懒加载，v1.0 后可按 ECharts 模块继续拆包。
- `@vueuse/core` 的第三方 PURE 注释位置触发 Rollup 清理提示，不影响产物。
- Mockito/Byte Buddy 提示未来 JDK 将限制动态 Agent；Java 21 当前测试通过。
- 本机 Node 26 非 LTS，但 Docker 构建固定 Node 22 Alpine。
- 高德真实 Key/目标域名白名单和外部模型限流不适合自动化伪造，需要人工现场验收。

## 7. 人工验收清单

1. USER 登录，确认路线页能看到 `YUNLU_DEMO_V1`，用轮椅规划 `N-02 → N-03`，核对路线线型、楼梯硬约束、风险文字和地图 Marker。
2. 提交一条临时障碍；ADMIN 审核通过；重新规划确认路线联动。测试后按需要手工改回或在明确确认后重置 Demo。
3. 在管理地图新增一个测试点并编辑道路属性，确认地图可自行设置点/边且审计可见；验收后可软停用测试对象。
4. 在 375px 和桌面宽度切换浅/深色，确认导航、路线设置/结果按钮、焦点和无白色加载闪屏。
5. 使用真实高德配置检查底图和 `/_AMapService`；以当前 AI 配置验证一次 SSE，限流时确认手工路线仍可用。
6. 查看治理洞察筛选、地图—图表—检查器联动、CSV 和规则/AI 摘要。
7. 点击“安全重置 Demo”只检查二次确认文案；除非确实希望清理当前 Demo 业务数据，否则取消，不要执行。
8. 检查 `/actuator/health`、Swagger、日志和 `docker compose ps`，确认服务稳定。

## 8. 2026-08-23 地图编辑升级回归

本节是 v1.0 发布后的增量验证，不改写上方 v1.0 发布时的历史结果。

| 层级 | 本轮结果 |
|---|---|
| 数据迁移 | Flyway V8 在空库测试和已有正式 Compose 持久卷均成功执行；新数据集中心严格为 `104.695359,31.534827`，0 个地图对象 |
| 历史保护 | `YUNLU_DEMO_V1` 仅停用，既有 22 节点、32 道路保持不变，管理员仍可查看 |
| 后端 | 67/67 JUnit 通过，覆盖 V8 状态与服务端完整 LineString 距离复算 |
| 前端 | 31/31 Vitest、TypeScript、ESLint、Prettier、production build 全部通过 |
| 浏览器 | Chromium Edge 6/6 通过；包含用户端隐藏旧 Demo、管理员折线添加/撤销、XSS 与 375px 流程 |
| 容器 | db/backend healthy，frontend 监听 8080；实际底图定位在新校园中心 |

本轮人工验收重点：管理员在空白数据集中创建两个道路节点，使用地图点击和拖动形成多拐点道路，完成后保存并刷新；确认折线形状不变、距离为只读且由服务端复算。点选节点、入口、设施或障碍时，应立即看到十字定位标记、坐标提示和检查器字段同步变化。

## 9. 2026-08-23 Formal GeoJSON 安全协作回归

| 层级 | 本轮结果 |
|---|---|
| 数据迁移 | Testcontainers 空库成功执行 Flyway V1–V9；V9 创建导入前 JSONB 备份表与索引 |
| 后端 | 71/71 JUnit 通过；覆盖只读预检、双指纹过期保护、KEEP_TARGET/OVERWRITE、备份审计与六类共 81 个对象持久化 |
| 前端 | 31/31 Vitest、TypeScript、ESLint、Prettier、production build 全部通过 |
| 浏览器 | 隔离 Chromium Edge 7/7 通过；新增 Formal 文件选择、预检、安全提示与确认合并流程 |
| 数据安全 | E2E 使用独立 tmpfs PostGIS 并自动销毁；未对正式 `SCHOOL_EXAMPLE_V1` 执行测试导入 |
| 正式 Compose | Flyway V9，db/backend healthy，frontend 200、代理 health `UP`；验证前后保留正式卷已有 2 个节点 |

人工验收重点：在管理地图导出 `SCHOOL_EXAMPLE_V1`，重新选择该文件，确认预览统计、默认“保留本地”、不删除提示和成功后的 `backupId`；然后由另一环境修改同编号对象，分别检查保留与覆盖策略。

## 11. 2026-08-27 Stage 11 途经点路线回归

| 层级 | 本轮结果 |
|---|---|
| 后端 | 83/83 JUnit 通过；0 failure、0 error |
| Yen Top-K 单测 | 验证三条不同无环路线按 100/120/140 加权成本排序；只有一条真实通路时不复制或凑数 |
| 多段编排单测 | 验证相邻坐标去重、距离/时间/楼梯/风险汇总、设施去重、edgeIds 合并及任一段不可达时整段失败 |
| 受控 Tool 集成测试 | 普通路线与含 1 个途经点的 2 段路线均返回 3 条不同完整路线；`ai_tool_call_log` 仍仅记录 1 次路线 Tool |
| 前端 | Node 22.23.2；31/31 Vitest、vue-tsc production build、ESLint 通过 |
| Docker | `docker compose up -d --build` 成功；db/backend healthy，frontend 监听 8080 |
| 真实 DeepSeek | `西苑5栋 → 龙山体育场 → 北苑2号楼A座` 正确产生 `waypointPlaces=[龙山体育场]`、`segmentCount=2`、`routeCount=3`；返回最短 1034 米、无障碍优先 1503 米、综合 1047 米三条完整路线 |
| 三路线交互 | 默认选中后端推荐的无障碍优先路线；依次点击最短和综合卡片后，pressed 状态、图例和地图折线同步切换，地图始终只显示当前一条 |
| 多轮页面 | 继续询问“这条路线主要风险是什么”时不重复计算，路线卡和地图折线保持显示 |

非阻塞说明：全仓 Prettier 检查仍报告 17 个此前已存在格式偏差的文件；本轮修改的 `UserAssistantView.vue` 不在报告中。Vite 仍有既有 ECharts 分包大于 500kB 和第三方 PURE 注释提示，不影响构建产物。
