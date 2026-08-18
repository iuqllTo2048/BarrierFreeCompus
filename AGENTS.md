# AGENTS.md — BarrierFreeCampus 项目级 Codex 约束

## 项目目标
本仓库是 `BarrierFreeCampus`（无碍智行）。核心是高德底图 + 自建路网 + A* 无障碍寻路 + 用户/管理员闭环 + LangChain4j 智能体 + 可视化。

## 工作方式
- 一次只做当前 Stage。
- 开始前读 `PROJECT_SPEC.md`、`docs/PROJECT_STATUS.md` 和当前 Stage Prompt。
- 先给计划，用户确认后编码。
- 当前 Stage 完成后停止。
- 用户没有明确说“继续”时，不进入下一 Stage。
- 不自行扩大需求。

## 危险操作
删除文件、大范围覆盖、清空/重置数据库、Drop 表、删除 Flyway 历史迁移、`git reset --hard`、force push、删除分支、重置 Demo、删除 Formal 数据前必须获得用户确认。

## 冻结技术栈
后端：Java 21、Spring Boot 3.5.x、Maven、Spring Security、JWT、MyBatis-Plus、PostgreSQL、PostGIS、Flyway、SpringDoc OpenAPI、LangChain4j（Stage 5）、JUnit、Spring Boot Test。

前端：Vue 3、TypeScript、Vite、npm、Pinia、Vue Router、Axios、Element Plus、高德 JS API、`@amap/amap-jsapi-loader`、GeoJSON、ECharts、Vitest、Playwright。

部署：Docker Compose、Nginx。

v1.0 不使用 Redis、不使用 OSS、不部署本地大模型。

不要自行换 React/JPA/MySQL/MongoDB/微服务/Tailwind/shadcn/GraphHopper。

## 代码规范
- 用户界面中文。
- 标识符英文。
- 关键注释中文。
- DTO 优先 Java record。
- Lombok 限制使用。
- 不用 MapStruct。
- TS 原则禁用 any。
- ESLint + Prettier。
- 统一 API 响应和异常。
- 业务模块化。
- 验收范围不允许假实现。

## 地图/坐标
- 高德只负责底图和交互，不作为核心无障碍路线算法。
- 自建 route_node + route_edge。
- A*。
- 高德侧坐标按 GCJ-02。
- dataset 记录 coordinate_system。
- 禁止把 GCJ-02 错误声明为 WGS84。

## Demo
- Demo 和 Formal 分离。
- Demo 来源 `DEMO_GENERATED`。
- Demo 可信度不能 HIGH。
- 随机模拟固定种子并持久化。
- 至少保证楼梯绕行、坡度冲突、动态封路、未知数据、设施偏好五种场景。
- 重置只影响 Demo。

## AI
- AI 可关闭。
- AI 失败不影响手工路线。
- AI 只调用白名单 Tool。
- 不提供任意 SQL/Shell/删除/角色 Tool。
- 写操作草稿 + 用户确认 + 正常 REST。
- 不展示/保存模型隐藏思维链。
- Key 只通过配置。

## UI
- 正式编码前 Design Gate。
- 用户端地图优先。
- 禁止明显 AI 模板感。
- 不新增 UI 框架。
- 主要控件有可见焦点与语义标签。
- 风险不能只靠颜色。

## 测试
每 Stage 做与变更匹配的构建/测试。Stage 8 系统补全。不要凑数量。

## 文档
每 Stage 更新 PROJECT_STATUS 和相关文档。大版本更新 TECH_STACK 和 EXTERNAL_CONFIG。

## Git
用户验收 Stage 后中文提交。不提交 `.env`、Key、密码、证书、构建产物。

## Stage 完成输出
必须报告：
1. 实现内容；
2. 关键文件；
3. 数据库迁移；
4. 新依赖；
5. 新配置/Key；
6. 自动测试；
7. 人工验收；
8. 已知限制；
9. 当前版本；
10. 下一 Stage。

然后停止等待“继续”。
