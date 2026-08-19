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
