# PROJECT_STATUS.md — BarrierFreeCampus 项目状态

## 当前阶段

- 当前版本：v1.0（技术版本 `1.0.0`）
- 当前 Stage：v1.0 后续改进（Formal GeoJSON 安全协作）
- 状态：已实现、验收并提交（`1cf48cf`）
- Git Tag：`v1.0`（用户已明确确认创建）

## v1.0 交付范围

| 阶段 | 已交付结果 |
|---|---|
| Stage 0 / Design Gate | 项目治理、环境与项目 Skills；冻结“静谧导览”设计系统 |
| Stage 1 | Spring Boot/Vue 基础、JWT + Refresh、角色权限、Compose |
| Stage 2 | PostGIS 地图模型、固定 Demo、管理地图 CRUD、GCJ-02/GeoJSON |
| Stage 3 | 自建路网 A*、三 Profile、五种行动方式、风险/成本解释 |
| Stage 4 | 用户资料/设施互动/上报/历史收藏与管理员审核治理闭环 |
| Stage 5 | 可关闭智能路线助手、白名单 Tool、SSE、草稿确认与降级 |
| Stage 6 | 治理统计、建筑评分、地图图表联动、CSV 与规则/AI 摘要 |
| Stage 7 | 全站响应式、深色、地图 Marker/路线语义与基础可访问性精修 |
| Stage 8 | 65 JUnit、29 Vitest、6 Playwright、性能/安全/隔离发布门禁 |
| Stage 9 | v1.0 版本、正式文档、复盘、依赖清理、Compose 健康门禁与发布验收 |

## Stage 9 变更

- 后端、前端与 lockfile 版本提升到 `1.0.0`。
- 正式 Compose 增加后端 healthcheck，Nginx 等待后端健康后启动；保留 `postgres-data` 持久卷。
- 新增产品 `README.md`、`USER_GUIDE.md`、`ALGORITHM.md`、`AGENT.md` 和基于真实源码的 `PROJECT_RECAP.md`。
- 重写 API、数据库、部署、技术栈与外部配置，修复旧文档停在 v0.2/v0.6/RC 的事实偏差。
- 审计 TODO、TS `any`、假实现、Secret 与依赖；移除未直接使用的 `@element-plus/icons-vue` 依赖。
- 通过正式 Compose 保留卷重建；发现持久卷 Demo 曾被停用后，仅通过管理员 API 重新启用，没有执行重置或删除数据。

## 地图编辑升级

- 新增中心为 `104.695359,31.534827`（GCJ-02）的“学校示例校园数据集”，初始不包含任何地图对象。
- `YUNLU_DEMO_V1` 迁移为停用状态，用户端不再读取；历史数据保留，管理员仍可查看和恢复。
- 管理端道路支持地图折线绘制、拖动、增删拐点、撤销、重置、完成与取消，并保留坐标输入和按钮操作。
- 节点、建筑、入口、设施和障碍点选增加地图定位标记、操作说明、坐标反馈和辅助技术状态播报。
- 道路距离按完整 LineString 逐段计算，服务端保存值为 A* 权威距离。

## Formal GeoJSON 安全协作

- GeoJSON v2 可共享建筑、入口、节点、折线路径、设施及管理员维护的障碍；不包含账号、业务记录、AI 会话或普通用户上报。
- Formal 导入先做只读预检，展示六类对象的新增、相同和冲突统计；错误存在时不能应用。
- 合并导入不会删除文件中缺失的本地对象；冲突默认保留本地，也可由管理员明确选择覆盖。
- 预检和应用之间使用文件指纹、目标数据指纹防止并发覆盖；目标变化时返回 409 并要求重新预检。
- 每次应用前在同一事务保存目标 GeoJSON JSONB 备份并记录审计；道路距离仍由服务端按完整 LineString 复算。
- 旧 Demo GeoJSON 导入接口保持兼容，不放宽其 Demo 隔离边界。

## 发布验证

| 项目 | v1.0 结果 |
|---|---|
| 后端 | 71/71 JUnit 通过；Testcontainers 空库执行 Flyway V1–V9 |
| A* 性能 | 400 节点、100 次最终回归：P50 413µs、P95 1,003µs、最大 3,034µs |
| 前端 | 31/31 Vitest；vue-tsc、ESLint、Prettier、Vite production build 通过 |
| 浏览器 | Playwright Chromium Edge 7/7 通过，含 Formal GeoJSON 预检合并、折线拐点撤销与 375px 移动端 |
| 安全/依赖 | Secret 扫描 0；npm 官方漏洞库 0；未发现 TS `any` 或业务 TODO |
| Docker E2E | 独立 18080/18081 + PostGIS tmpfs 全流程通过并自动销毁 |
| 正式 Compose | db/backend healthy，Nginx 200，代理 health `UP`，未登录 API 401 |
| 数据集 | `SCHOOL_EXAMPLE_V1` 启用，正式卷现有 2 个节点且其余对象为 0；`YUNLU_DEMO_V1` 停用并保留 |

完整测试证据见 [TEST_REPORT.md](TEST_REPORT.md)。

## 演示账号

- USER：`demo_user / Demo@12345`
- ADMIN：`demo_admin / Admin@12345`

只用于本地比赛演示；公开部署前必须替换或停用。

## 已知限制

- Demo 数据是固定生成数据，不代表真实校园实测或无障碍认证。
- Playwright 仅覆盖 Microsoft Edge Chromium；未宣称 Firefox/WebKit 兼容。
- 高德真实域名白名单与外部模型可用性需要在目标域名人工验证。
- `AdminAnalyticsView` 分包 651.66kB（gzip 218.20kB），不阻塞 v1.0，后续可拆分。
- Compose 是单机比赛基线，不含 TLS、自动备份、集中监控、高可用或灾难恢复。
- Mockito/Byte Buddy 在 Java 21 有未来 JDK 动态 Agent 提示，当前测试不受影响。

## 下一步

Formal GeoJSON 安全协作已完成验收并提交。下一步由用户决定是否推送远程或创建新的 v2.0 Stage；整项目接手说明见 [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md)。
