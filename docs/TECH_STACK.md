# TECH_STACK.md — BarrierFreeCampus 技术栈记录

本文件记录已冻结的目标技术栈与 Stage 0 实际环境；业务依赖将在对应 Stage 实际引入后补充准确版本。

## 开发环境

| 工具 | 实际版本 | 状态 |
|---|---|---|
| Java | 21.0.9 LTS | 已检测 |
| Maven | 3.9.12 | 已检测 |
| Node.js | 26.3.0 | 已检测，非 LTS 风险已记录 |
| npm | 11.16.0 | 已检测 |
| Docker | 29.1.5 | 已检测 |
| Docker Compose | 5.0.1 | 已检测 |
| Python Launcher | 3.13.0 | 已检测，使用 `py` 调用 |
| UI UX Pro Max | 2.15.0 | 已安装于 `.agents/skills/ui-ux-pro-max/`，用于后续 UI 设计与实现指导 |

## 冻结技术栈

| 范畴 | 技术 | 引入阶段 |
|---|---|---:|
| 后端 | Java 21、Spring Boot 3.5.x、Maven、Spring Web、Validation、Spring Security、JWT、BCrypt | Stage 1 |
| 数据 | PostgreSQL、PostGIS、Flyway、MyBatis-Plus | Stage 1-2 |
| API 与调度 | SpringDoc OpenAPI、Spring Scheduler | Stage 1 |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Element Plus | Stage 1 |
| 地图与图表 | 高德 JS API、`@amap/amap-jsapi-loader`、GeoJSON、ECharts | Stage 2 / 6 |
| AI | LangChain4j、SSE | Stage 5 |
| 测试 | JUnit 5、Spring Boot Test、MockMvc、Vitest、Playwright | 按 Stage 引入 |
| 部署 | Docker Compose、Nginx | Stage 1 / 9 |

## 约束

- 不使用 React、JPA、MySQL、MongoDB、微服务、Kubernetes、Redis、GraphHopper、openrouteservice、本地大模型、Tailwind 或 shadcn/ui。
- Node 26 能执行 Stage 0 所需操作，但后续前端依赖出现兼容性问题时，先报告并等待决定，不自行切换版本。
