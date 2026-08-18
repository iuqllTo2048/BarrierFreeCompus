# DEPLOYMENT.md — 本地一键启动

## 前置条件

- Docker 与 Docker Compose 可用。
- 端口 `8080`（Nginx）和 `8081`（后端直连调试）未被占用。

## 首次启动

1. 将 `.env.example` 复制为 `.env`。
2. 为 `DB_PASSWORD` 设置本地数据库密码。
3. 为 `JWT_SECRET` 设置至少 32 字节的随机密钥。
4. 在仓库根目录执行：

```powershell
docker compose up -d --build
```

首次后端镜像构建需要下载 Maven 依赖；Dockerfile 已启用 BuildKit Maven 缓存，后续构建会复用。

## 地址

| 用途 | 地址 |
|---|---|
| 前端 | `http://localhost:8080/` |
| 健康检查 | `http://localhost:8080/actuator/health` |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| 后端直连调试 | `http://localhost:8081/` |

## 常用检查

```powershell
docker compose ps
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend
```

停止容器但保留数据库卷：

```powershell
docker compose down
```

不要随意添加 `-v`，因为它会删除本地数据库卷。

## 生产部署注意

- 必须使用 HTTPS，并设置 `SECURE_COOKIE=true`。
- 使用部署平台 Secret 管理数据库密码和 JWT 密钥。
- 替换或停用演示账号。
- 当前 Compose 是比赛项目本地运行基线，不包含生产证书、备份和高可用方案。
