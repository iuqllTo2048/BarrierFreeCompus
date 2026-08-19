# DEPLOYMENT.md — v1.0 本地发布与运维

## 1. 前置条件

- Docker Engine/Desktop 和 Docker Compose 可用。
- 主机端口 `8080`（Nginx）与 `8081`（后端直连）未占用。
- 已申请高德“Web 端（JS API）”Key 和同一 Key 对应的安全密钥。
- 首次构建需要访问 Maven、npm 和 Docker 镜像仓库；后续优先复用缓存。

## 2. 首次启动

```powershell
Copy-Item .env.example .env
# 编辑 .env：填写 DB_PASSWORD、JWT_SECRET、AMAP_JS_KEY、AMAP_SECURITY_JS_CODE
docker compose up -d --build
docker compose ps
```

`JWT_SECRET` 至少 32 字节；本地 HTTP 保持 `SECURE_COOKIE=false`。AI 可选：保持 `AI_ENABLED=false` 时无需 Provider Key，应用使用确定性 Mock。

Compose 启动顺序为 PostgreSQL 健康 → Spring Boot 健康 → Nginx。全新数据库由 Flyway 自动执行 V1–V7，并安全初始化固定 Demo；已有 `postgres-data` 卷不会重新创建 schema 或覆盖用户数据。

## 3. 地址与端口

| 服务 | 容器端口 | 主机地址 |
|---|---:|---|
| Nginx / 前端 | 80 | `http://localhost:8080` |
| Spring Boot 直连 | 8080 | `http://localhost:8081` |
| PostgreSQL/PostGIS | 5432 | 不映射到主机 |

常用入口：

- 登录：`http://localhost:8080/`
- 用户路线：`http://localhost:8080/user`
- 管理地图：`http://localhost:8080/admin`
- 健康检查：`http://localhost:8080/actuator/health`
- Swagger：`http://localhost:8080/swagger-ui.html`

Nginx 将 `/api/` 同源代理到后端，浏览器无需直连 8081；`/api/agent/` 关闭缓冲以支持 SSE。`/_AMapService/` 在容器运行时附加高德安全密钥，密钥不进入前端包。

## 4. 日常命令

```powershell
# 启动已有构建
docker compose up -d

# 代码或依赖变化后重建
docker compose up -d --build

# 查看状态和最近日志
docker compose ps
docker compose logs --tail 100 db
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend

# 持续跟随后端日志，Ctrl+C 仅退出跟随
docker compose logs -f backend

# 停止并保留数据库
docker compose down
```

不要把 `docker compose down -v` 当作普通停止命令：`-v` 会删除 `postgres-data`，本地数据库数据不可由 Compose 自动恢复。

## 5. 数据卷、初始化与 Demo 重置

命名卷 `postgres-data` 挂载到 PostgreSQL 数据目录。第一次创建卷时 Flyway 初始化 schema 与固定种子；正常重启、重建应用镜像或 `docker compose down` 都保留卷。

管理员需要恢复演示状态时，应在治理工作台点击“安全重置 Demo”并完成二次确认。后端只接受 `is_demo=true` 的数据集，只清理当前 Demo 业务数据并恢复种子对象；Formal 会被拒绝，审计日志保留。该操作与删除整个数据库卷不同。

正式备份应使用 PostgreSQL 备份方案或部署平台卷快照。本地比赛 Compose 没有自动备份调度。

## 6. 健康检查与排障

```powershell
docker compose ps
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
Invoke-WebRequest -UseBasicParsing http://localhost:8080/
```

预期健康 JSON 包含 `"status":"UP"`。常见排查顺序：

1. `docker compose config --quiet` 检查 `.env` 必填变量和 YAML。
2. `docker compose ps` 确认 db/backend 为 healthy，frontend 为 running。
3. 查 `db` 日志判断卷、口令或迁移问题；查 `backend` 日志判断 JWT/AI 配置；查 `frontend` 日志判断 Nginx 模板。
4. 地图空白但 API 正常时，检查高德 Key 类型、域名白名单、`AMAP_SECURITY_JS_CODE` 和浏览器网络请求。
5. AI 限流不属于健康失败；手工路线仍应可用。需要时设 `AI_ENABLED=false` 后重建后端。

## 7. 独立开发

后端可在 `backend/` 使用 `mvn spring-boot:run`，通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `JWT_SECRET` 连接 PostgreSQL。前端在 `frontend/` 使用 `npm run dev`；Vite 从仓库根 `.env` 读取高德配置，并把 `/api` 与 `/_AMapService` 代理到正确目标。

## 8. 隔离 E2E

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-e2e.ps1
```

脚本使用 Compose 项目 `barrierfreecampus-e2e`、端口 18080/18081 和 PostgreSQL `tmpfs`，并在 `finally` 中销毁容器和卷。它不读取、不重置 8080 演示环境的 `postgres-data`。

## 9. 生产化边界

- 使用 HTTPS，设置 `SECURE_COOKIE=true`，配置准确的高德域名白名单。
- 通过部署平台 Secret 管理数据库、JWT、高德安全密钥和 AI Key；替换/停用 Demo 账号。
- 增加数据库备份恢复、证书、日志留存、监控、限流和灾难恢复。
- 当前 Compose 是单机比赛交付基线，不包含高可用、滚动发布、云 OSS 或 Kubernetes。
