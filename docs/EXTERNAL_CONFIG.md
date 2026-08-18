# EXTERNAL_CONFIG.md — 外部与运行配置

真实密钥只放在本地 `.env` 或部署平台的 Secret 管理中，不得提交到 Git。`.env.example` 只保留变量名。

## Stage 1 运行配置

| 环境变量 | 是否必需 | 说明 | 未配置影响 |
|---|---|---|---|
| `DB_PASSWORD` | Compose 必需 | PostgreSQL 用户密码，同时传给后端 | Compose 配置或数据库连接失败 |
| `JWT_SECRET` | 必需 | JWT HMAC 密钥，至少 32 字节 | 后端拒绝启动 |
| `SECURE_COOKIE` | 否 | 本地 HTTP 为 `false`；生产 HTTPS 必须为 `true` | HTTP 下设为 true 会导致浏览器不发送 Cookie |
| `DB_URL` | 仅独立后端运行 | JDBC URL；Compose 已注入 | 后端使用本地默认 URL |
| `DB_USERNAME` | 仅独立后端运行 | 数据库用户名 | 默认 `barrierfree` |

## 后续外部服务

| 服务 | Stage | 环境变量 | 是否必需 | 未配置影响 |
|---|---:|---|---|---|
| 高德 Web JS API | 2 | `AMAP_JS_KEY` | 真实地图时必需 | 地图无法完整加载 |
| 高德 JS 安全配置 | 2 | `AMAP_SECURITY_JS_CODE` | 以高德要求为准 | 可能鉴权失败 |
| AI 开关 | 5 | `AI_ENABLED` | 否，默认 `false` | AI 关闭，手工路线不受影响 |
| AI 网关 | 5 | `AI_BASE_URL` | 开启 AI 时必需 | AI 不可用 |
| AI 密钥 | 5 | `AI_API_KEY` | 开启 AI 时必需 | AI 不可用 |
| AI 模型 | 5 | `AI_MODEL_NAME` | 开启 AI 时必需 | AI 不可用 |

v1.0 不使用 OSS，不配置 OSS 变量。
