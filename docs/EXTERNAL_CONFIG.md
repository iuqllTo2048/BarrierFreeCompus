# EXTERNAL_CONFIG.md — 外部与运行配置

真实密钥只放在本地 `.env` 或部署平台 Secret 管理中，不得提交到 Git。`.env.example` 只保留变量名。

## Stage 2–3 必需配置

| 环境变量 | 是否必需 | 用途 | 未配置影响 |
|---|---|---|---|
| `AMAP_JS_KEY` | Compose 必需 | 高德“Web 端（JS API）”Key；Vite 构建时写入公开前端包 | Compose 构建直接拒绝；地图无法加载 |
| `AMAP_SECURITY_JS_CODE` | Compose 必需 | 同一高德 Web JS Key 对应的安全密钥；仅供 Nginx 代理使用 | Compose 启动直接拒绝；高德鉴权失败 |
| `DB_PASSWORD` | Compose 必需 | PostgreSQL 用户密码，同时传给后端 | 数据库或后端连接失败 |
| `JWT_SECRET` | 必需 | JWT HMAC 密钥，至少 32 字节 | 后端拒绝启动 |
| `SECURE_COOKIE` | 否 | 本地 HTTP 为 `false`；生产 HTTPS 必须为 `true` | HTTP 下设为 true 会导致浏览器不发送 Cookie |

高德当前使用的是 Web JS API 2.0 配置，不需要额外申请 Web 服务 Key。部署到非 `localhost` 域名时，必须在高德控制台为该 Key 配置正确的域名白名单。

Stage 3 路线规划完全使用后端自建路网与 A*，不调用高德路径规划服务，因此没有新增外部 Key 或第三方接口配置。

## 高德安全代理

- 浏览器加载器只接收 `AMAP_JS_KEY`，并将 `serviceHost` 指向同源 `/_AMapService`。
- Nginx 在运行时把 `AMAP_SECURITY_JS_CODE` 追加为 `jscode`，分别代理高德地图样式与 REST 请求。
- 安全密钥不参与前端构建、不出现在 Git；Web JS Key 本身按高德设计会出现在浏览器资源中，仍需域名白名单保护。
- 本地 Vite 开发代理从仓库根目录 `.env` 读取同名变量，行为与 Nginx 代理一致。

## 独立后端运行

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/barrierfreecampus` |
| `DB_USERNAME` | 数据库用户名 | `barrierfree` |
| `DB_PASSWORD` | 数据库密码 | `barrierfree`（仅应用默认，Compose 要求显式配置） |

## Stage 5 预留

`AI_ENABLED`、`AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL_NAME` 仅为后续智能体预留；当前不调用外部 AI。v1.0 不使用 OSS。
