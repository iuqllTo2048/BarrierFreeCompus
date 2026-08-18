# PROJECT_STATUS.md — BarrierFreeCampus 项目状态

## 当前阶段

- 当前版本：v0.1.0
- 当前 Stage：Stage 1（基础工程与一键启动）
- 状态：开发与自动验收已完成，等待人工验收
- 下一阶段：Stage 2（地图与基础数据），未开始

## Stage 1 已实现

- 后端：Spring Boot 3.5.14、Java 21、统一响应/异常、Spring Security、JWT Access Token、Refresh Token 轮换、USER/ADMIN 权限、Swagger、健康检查及审计日志框架。
- 数据库：PostgreSQL 17 + PostGIS 3.5、Flyway 迁移、用户/刷新令牌/审计表。
- 前端：Vue 3 + TypeScript + Vite、Pinia、Router、Axios、Element Plus、登录态恢复、401 自动刷新、角色路由、中文 USER/ADMIN 界面骨架。
- 部署：根目录 Docker Compose、后端容器、PostGIS 容器、Nginx 前端与同源 API 代理。
- UI：遵循“静谧导览”设计系统，提供系统字体、深青绿语义色、可见焦点、响应式布局、深色模式及非颜色路线图例。

## 已解决问题

- 原演示账号种子使用了无法匹配约定密码的 BCrypt 值，现通过只追加的 Flyway V2 迁移修复。
- 原 JWT 过滤器未执行验签，现已完成 Bearer Token 解析与角色注入。
- 原认证异常经受保护的 `/error` 二次转发后表现为空白 403，现统一返回真实 HTTP 状态和中文 JSON 错误。
- 刷新令牌由逐条 BCrypt 扫描改为 SHA-256 摘要精确索引，并实现单次轮换与服务端注销。
- 前端原登录按钮仅显示占位提示，现已接入真实登录、恢复、退出和权限跳转。

## 自动验收结果

| 项目 | 结果 |
|---|---|
| 后端 `mvn test` | 通过，3 个测试 |
| 前端 ESLint | 通过，0 错误/0 警告 |
| 前端 TypeScript | 通过 |
| 前端 Vitest | 通过，1 个测试 |
| 前端生产构建 | 通过，已按页面拆包 |
| Docker Compose 构建/启动 | 通过，3 个容器运行 |
| Flyway | V1、V2 均成功，当前 schema v2 |
| 健康检查 | `UP` |
| USER 登录及用户接口 | 通过 |
| USER 访问 ADMIN 接口 | 正确返回 403 JSON |
| ADMIN 登录及管理接口 | 通过 |
| 错误密码 | 正确返回 401 JSON |
| Refresh Token 轮换/旧令牌失效/注销 | 通过 |
| Swagger 入口 | 302 正常跳转至 Swagger UI |

## 演示账号

仅用于本地 Stage 1 验收：

- 用户：`demo_user` / `Demo@12345`
- 管理员：`demo_admin` / `Admin@12345`

## 已知限制

- 尚未实现地图、路网、A*、设施、事件、审核、统计和 AI；界面明确标识为后续 Stage 内容，不包含伪造业务数据。
- 本地 Compose 使用 HTTP，因此 `SECURE_COOKIE=false`；生产 HTTPS 必须设为 `true`。
- Node 26 非常规 LTS 的兼容性风险继续保留；Docker 前端构建固定使用 Node 22 Alpine。
- 本轮浏览器控制运行时在本机未能建立自动化连接，因此交互已通过真实 HTTP/API、构建和页面资源验证，最终视觉仍列入人工验收步骤。

## 下一步

等待用户验收 Stage 1；验收后创建中文 Git 提交，再根据明确的“继续”进入 Stage 2。
