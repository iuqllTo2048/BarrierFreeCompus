# Stage 1 — 基础工程与一键启动

目标：把项目真正跑起来，但不做地图业务和 A*。

先输出计划，等待确认。

## Backend
- Java 21
- Spring Boot 3.5.x 当前稳定 patch，固定版本
- Maven
- Spring Web
- Validation
- MyBatis-Plus
- PostgreSQL/PostGIS
- Flyway
- Spring Security
- 用户名密码登录
- USER / ADMIN
- BCrypt
- JWT Access Token
- Refresh Token
- 统一响应
- 统一异常
- SpringDoc OpenAPI / Swagger UI
- 基础审计日志框架
- 健康检查

## Frontend
- Vue 3 + TypeScript + Vite + npm
- Router
- Pinia
- Axios
- Element Plus
- ESLint + Prettier
- USER/ADMIN 双布局骨架
- 登录页
- 登录态恢复
- 401 刷新 Token
- 中文 UI

## Token
优先让 refresh token 使用 HttpOnly Cookie 或等效安全方式；Access Token 不长期裸存不安全位置；Nginx 同源代理简化 CORS。

## DB / Flyway
- V1 初始安全相关表
- 不用自动 schema update
- 从空库可自动建立

## Docker
- PostgreSQL + PostGIS
- backend
- frontend/nginx
- root compose
- `docker compose up -d` 能启动 v0.1

## docs
更新 TECH_STACK、EXTERNAL_CONFIG、DATABASE、API、DEPLOYMENT、PROJECT_STATUS。

## 验收
1. Compose 启动；
2. 前端打开；
3. USER/ADMIN 可登录；
4. 权限生效；
5. Swagger 可访问；
6. Flyway 从空库建表；
7. 无 secret；
8. 后端测试；
9. 前端 lint/typecheck/build。

完成后停止，不进 Stage 2。
