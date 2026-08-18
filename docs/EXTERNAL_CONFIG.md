# EXTERNAL_CONFIG.md — 外部配置说明

真实密钥仅放在本地 `.env`，不得提交到 Git。仓库中的 `.env.example` 只保留变量名和安全默认值。

| 服务 | Stage | 环境变量 | 是否必需 | 获取位置 | 未配置影响 |
|---|---:|---|---|---|---|
| 高德 Web JS API | 2 | `AMAP_JS_KEY` | 真实地图时必需 | 高德开放平台 | 地图无法完整加载 |
| 高德 JS 安全配置 | 2 | `AMAP_SECURITY_JS_CODE` | 以高德当时要求为准 | 高德开放平台 | 可能鉴权失败 |
| AI 开关 | 5 | `AI_ENABLED` | 否，默认 `false` | 本地 `.env` | AI 功能关闭，手工 A* 不受影响 |
| AI 网关 | 5 | `AI_BASE_URL` | 开启 AI 时必需 | 所选模型服务商 | AI 功能不可用 |
| AI 密钥 | 5 | `AI_API_KEY` | 开启 AI 时必需 | 所选模型服务商 | AI 功能不可用 |
| AI 模型 | 5 | `AI_MODEL_NAME` | 开启 AI 时必需 | 所选模型服务商 | AI 功能不可用 |

v1.0 不使用 OSS，不配置 OSS 变量。
