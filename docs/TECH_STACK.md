# TECH_STACK.md — v1.0 实际技术栈

版本以 `pom.xml`、`package-lock.json` 和容器镜像为准。本表记录 v1.0 当前解析到的实际版本，不把未实现计划列为已使用技术。

## 1. 后端与数据

| 名称 / 版本 | 作用 | 项目位置 | 选择原因 |
|---|---|---|---|
| Java 21 LTS | 后端运行时 | `backend/src/main/java`、后端镜像 | 长期支持、record 与现代语言特性，符合比赛冻结栈 |
| Spring Boot 3.5.14 | Web、依赖管理与应用装配 | `backend/pom.xml`、`application.yml` | 单体项目启动快，安全、校验、运维生态完整 |
| Spring Web MVC 3.5.14 | REST 与 SSE | Controller、`AgentService` | 与 Spring Security/Validation 集成，SSE 无需额外网关 |
| Spring Security 6.5.10 | 认证授权与方法权限 | `security/`、各管理员 Controller | 统一 USER/ADMIN 边界，可每次请求复核用户状态 |
| Jakarta Validation 3.x（Boot 管理） | DTO 入参约束 | DTO record 与 Controller | 在 API 边界阻止空值和越界权重 |
| Spring Actuator 3.5.14 | 健康检查 | `/actuator/health`、Compose healthcheck | 为容器启动顺序与人工排障提供稳定探针 |
| JJWT 0.12.6 | JWT 签发和验签 | `auth/JwtService.java` | 适合无状态 Access Token，API 清晰且支持 HMAC |
| BCrypt（Spring Security） | 密码哈希 | `AuthService`、Flyway Demo 凭据 | 成熟的单向口令哈希，避免保存明文密码 |
| MyBatis-Plus 3.5.12 | 数据集 Mapper 基础 | `mapdata/DatasetMapper.java` | 保留显式 SQL 与轻量 Mapper，不引入 JPA 隐式行为 |
| JdbcTemplate 6.2.x（Boot 管理） | 复杂业务/空间/统计 SQL | routing、business、analytics、agent Repository/Service | PostGIS、JSONB 和批量聚合需要可审计的显式 SQL |
| Flyway 11.7.2 | Schema 迁移与固定种子 | `backend/src/main/resources/db/migration` | 空库可复现，历史迁移可审计且禁止手改 |
| PostgreSQL 17 | 关系数据与 JSONB | `docker-compose.yml`、Repository SQL | 同时承载事务业务、审计和路线结果 JSONB |
| PostGIS 3.5 | Geometry、GIST 与空间过滤 | V3–V7、地图/统计 SQL | 支持校园对象、bbox、邻近与热力点等空间查询 |
| PostgreSQL JDBC 42.7.10 | 后端数据库驱动 | `backend/pom.xml` | Spring Boot 管理的 PostgreSQL 官方 JDBC 驱动 |
| SpringDoc OpenAPI 2.8.9 | OpenAPI 与 Swagger UI | `/v3/api-docs`、`/swagger-ui.html` | 便于验收和联调，不维护另一套接口运行器 |
| LangChain4j 1.18.0 | OpenAI-compatible 模型网关 | `agent/LangChain4jAiGateway.java` | 隔离 Provider，业务层只依赖自有 `AiGateway` |
| Spring Scheduler 3.5.14 | 障碍自动过期 | `business/BarrierExpiryScheduler.java` | v1.0 单实例足够，无需引入 Redis/消息队列 |

## 2. 前端、地图与可视化

| 名称 / 版本 | 作用 | 项目位置 | 选择原因 |
|---|---|---|---|
| Vue 3.5.41 | 组件与响应式 UI | `frontend/src` | Composition API 与 TypeScript 适合单页地图应用 |
| TypeScript 5.7.3 | 静态类型 | `frontend/src/**/*.ts`、Vue script | 接口/地图对象类型明确，原则禁用 `any` |
| Vite 6.4.3 | 开发服务与生产构建 | `frontend/vite.config.ts` | Vue 构建快，支持环境变量和本地代理 |
| Vue Router 4.6.4 | 懒加载路由与角色守卫 | `frontend/src/router` | 用户端/管理端单页导航和权限回退集中管理 |
| Pinia 3.0.4 | 认证和地图共享状态 | `frontend/src/stores` | 比自建事件总线更易测试，保持状态来源清晰 |
| Axios 1.19.0 | 常规 REST 客户端 | `frontend/src/services/http.ts` | 统一 Bearer、刷新和错误处理；SSE 单独使用 Fetch |
| Element Plus 2.14.4 | 表单、表格、弹窗等基础组件 | Vue views、全局主题 | 组件成熟；通过项目 Token 二次主题化，不再引入 UI 框架 |
| 项目内联 SVG | 通用与地图语义图标 | `services/app-icons.ts`、组件 | 无额外图标框架，保持一致线性风格并区分地图类型 |
| 高德 JS API 2.0 | GCJ-02 底图和地图交互 | `components/CampusMap.vue` | 中国境内校园底图适配；不承担核心路线计算 |
| `@amap/amap-jsapi-loader` 1.0.1 | 按需加载高德 JS API | `CampusMap.vue` | 官方加载方式，支持 Key 与安全代理配置 |
| ECharts 6.1.0 | 治理图表 | `AdminAnalyticsView.vue`、`analytics-charts.ts` | 条形/折线/堆叠图可按需注册，并支持深色与 aria |
| GeoJSON | 地图交换与路线几何 | Map API、A* 返回、管理导入导出 | 标准结构易与高德覆盖物和后端几何衔接 |

## 3. 测试与工程质量

| 名称 / 版本 | 作用 | 项目位置 | 选择原因 |
|---|---|---|---|
| JUnit 5 / Spring Boot Test 3.5.14 | 后端单元与集成测试 | `backend/src/test` | 与 Spring 上下文、MockMvc 和断言生态一致 |
| Spring Security Test 6.5.10 | 角色与认证测试 | `MapDataIntegrationTest` | 精确验证未登录、USER、ADMIN 边界 |
| Testcontainers 1.21.4 | 真实 PostGIS 集成环境 | 后端集成测试 | 验证 Flyway、PostGIS 和 SQL，不用内存库伪装 |
| Vitest 3.2.7 | 前端单元测试 | `frontend/src/**/*.test.ts` | 与 Vite 配置共享，适合纯函数和 Store |
| Playwright 1.62.1 | Chromium E2E | `frontend/e2e`、`scripts/run-e2e.ps1` | 覆盖真实登录、路线、权限、XSS 和移动端流程 |
| vue-tsc 2.2.12 | Vue 类型检查 | `npm run build/typecheck` | 检查模板与 `<script setup>` 的联合类型 |
| ESLint 9.39.5 | TS/Vue 静态规则 | `frontend/eslint.config.js` | 防止未使用代码和不安全写法 |
| Prettier 3.9.6 | 前端格式门禁 | `npm run format:check` | 保持 Vue/TS/CSS 一致，减少无关差异 |

## 4. 构建与部署

| 名称 / 版本 | 作用 | 项目位置 | 选择原因 |
|---|---|---|---|
| Maven 3.9（容器） | 后端依赖与打包 | `backend/Dockerfile` | 固定 Java 21 构建环境并复用 Maven 缓存 |
| Node 22 Alpine（容器） | 前端 `npm ci` 与构建 | `frontend/Dockerfile` | 发布构建固定 LTS，不受本机 Node 26 非 LTS 影响 |
| Eclipse Temurin 21 JRE | 后端运行镜像 | `backend/Dockerfile` | 只带运行时，保持 Java LTS 与较小镜像 |
| Nginx 1.27 Alpine | 静态资源、API/SSE 与高德代理 | `frontend/Dockerfile`、`default.conf.template` | 单一入口、SPA fallback 和密钥运行时代理 |
| Docker Compose | db/backend/frontend 编排 | `docker-compose.yml` | 比赛本地一键启动，健康依赖和持久卷清晰 |

## 5. 自研核心

| 名称 | 作用 | 项目位置 | 选择原因 |
|---|---|---|---|
| 有向路网 + 可解释 A* | 三 Profile 路线与成本说明 | `backend/.../routing` | 无障碍属性和审核障碍必须由项目规则控制，不能交给通用地图路线 |
| 地图—业务—治理闭环 | 上报、审核、动态绕行与统计 | business、analytics、前端三类页面 | 比单纯地图 Demo 更能证明公共服务与治理价值 |
| `AiGateway` + 白名单 Tool | 可关闭智能入口 | `backend/.../agent` | 模型可替换、失败可降级，权限与事实仍掌握在应用内 |
| “静谧导览”设计系统 | 响应式、深色和可访问语义 | `docs/DESIGN_SYSTEM.md`、前端样式 | 地图优先且避免模板化视觉，风险不只靠颜色 |

## 6. 明确不使用

v1.0 不使用 React、JPA、MySQL、MongoDB、微服务、Kubernetes、Redis、GraphHopper、openrouteservice、OSS、本地大模型、Tailwind 或 shadcn/ui。它们不会出现在发布架构图中。

本机开发环境曾使用 Java 21.0.9、Maven 3.9.12、Node 26.3.0、npm 11.16.0、Docker 29.1.5 和 Compose 5.0.1；这些是开发机事实，不替代容器发布版本。Node 26 非 LTS 风险通过 Docker 固定 Node 22 规避。
