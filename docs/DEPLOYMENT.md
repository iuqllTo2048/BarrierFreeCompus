# DEPLOYMENT.md — 本地一键启动

## 前置条件

- Docker 与 Docker Compose 可用。
- 端口 `8080`（Nginx）和 `8081`（后端调试）未被占用。
- 已在高德控制台创建“Web 端（JS API）”Key，并取得对应安全密钥。

## 首次启动

1. 将 `.env.example` 复制为 `.env`。
2. 填写 `DB_PASSWORD`、至少 32 字节的 `JWT_SECRET`、`AMAP_JS_KEY` 和 `AMAP_SECURITY_JS_CODE`。
3. 本地 HTTP 保持 `SECURE_COOKIE=false`。
4. 在仓库根目录执行：

```powershell
docker compose up -d --build
```

Compose 对两个高德变量采用必填校验，缺失时会在构建前给出明确错误。Web JS Key 会按高德 JS API 的正常机制进入前端包；安全密钥只在 Nginx 容器运行时用于同源代理。

## 地址

| 用途 | 地址 |
|---|---|
| 前端登录 | `http://localhost:8080/` |
| 用户地图 | `http://localhost:8080/user` |
| 管理地图 | `http://localhost:8080/admin` |
| 健康检查 | `http://localhost:8080/actuator/health` |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| 后端直连 | `http://localhost:8081/` |

## 常用检查

```powershell
docker compose ps
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend
```

停止容器并保留数据库卷：

```powershell
docker compose down
```

不要随意添加 `-v`，它会删除本地数据库卷。

## 本地前端开发

根目录 `.env` 保持高德变量，然后在 `frontend/` 执行 `npm run dev`。Vite 会把 `/api` 和 `/_AMapService` 分别代理到后端与高德，不需要把安全密钥写入源码。

## 生产注意

- 使用 HTTPS 并设置 `SECURE_COOKIE=true`。
- 在高德控制台设置准确域名白名单。
- 使用部署平台 Secret 管理数据库密码、JWT 密钥和高德安全密钥。
- 替换或停用演示账号，并对数据库做备份。
- 当前 Compose 是比赛本地运行基线，不包含生产证书、高可用和备份调度。
