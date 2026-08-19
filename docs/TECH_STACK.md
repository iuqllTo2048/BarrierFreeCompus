# TECH_STACK.md — BarrierFreeCampus 技术栈记录

## v0.6.0 实际技术栈

| 范畴 | 技术与版本 |
|---|---|
| 后端运行时 | Java 21、Spring Boot 3.5.14、Maven 3.9.x |
| Web 与安全 | Spring Web、Validation、Spring Security、BCrypt、JJWT 0.12.6 |
| 数据与空间 | PostgreSQL 17、PostGIS 3.5、Flyway、MyBatis-Plus 3.5.12、JdbcTemplate |
| API 与运维 | SpringDoc OpenAPI 2.8.9、Actuator |
| 前端 | Vue 3.5、TypeScript 5.7、Vite 6.4、Vue Router 4.5、Pinia 3、Axios、Element Plus 2.9、ECharts 6 |
| 地图 | 高德 JavaScript API 2.0、`@amap/amap-jsapi-loader` 1.0.1、GeoJSON |
| 路线算法 | 自建有向路网、可解释 A*、三 Profile 并行规划与候选去重 |
| 业务闭环 | Spring Scheduler、PostGIS 邻近匹配、评分/评论/建议、历史/收藏、审核与审计 |
| 智能体 | LangChain4j 1.18.0、OpenAI-compatible Gateway、确定性 Mock、SSE、白名单 Tool |
| 治理洞察 | PostGIS 空间聚合、JSONB 路线历史聚合、建筑加权评分、CSV、ECharts |
| 前端质量 | ESLint 9、Prettier 3、Vitest 3、vue-tsc |
| 后端测试 | JUnit 5、Spring Boot Test、Spring Security Test、Testcontainers 1.21 |
| 部署 | Docker Compose、Nginx 1.27 Alpine、Node 22 Alpine、Temurin 21 JRE |

具体补丁版本以 `backend/pom.xml` 与 `frontend/package-lock.json` 为准。

## 本机开发环境

| 工具 | 实际版本 | 状态 |
|---|---|---|
| Java | 21.0.9 LTS | 可用 |
| Maven | 3.9.12 | 可用 |
| Node.js | 26.3.0 | 可用，非 LTS 风险已记录 |
| npm | 11.16.0 | 可用 |
| Docker | 29.1.5 | 可用 |
| Docker Compose | 5.0.1 | 可用 |
| Python Launcher | 3.13.0 | 使用 `py` 调用 |
| UI UX Pro Max | 2.15.0 | 项目级 Skill 已安装 |

## 后续冻结技术

| 技术 | 计划阶段 |
|---|---:|
| Playwright 系统验收 | Stage 8 |

## 明确不使用

React、JPA、MySQL、MongoDB、微服务、Kubernetes、Redis、GraphHopper、openrouteservice、本地大模型、Tailwind 和 shadcn/ui 不在 v1.0 技术栈内。
