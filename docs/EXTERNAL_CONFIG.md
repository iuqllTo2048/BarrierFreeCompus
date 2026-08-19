# EXTERNAL_CONFIG.md — v1.0 外部与运行配置

真实密钥只允许进入本地未提交的 `.env` 或部署平台 Secret。仓库中的 `.env.example` 只列变量名；`scripts/security-scan.ps1` 会检查误提交的 `.env`、常见 Key、私钥和证书。

## 1. 配置总览

| 配置 | 何时获取/生成 | 填写位置 | 缺失影响 | 安全规则 |
|---|---|---|---|---|
| 高德 `AMAP_JS_KEY` | 首次启动地图前，在高德控制台申请“Web 端（JS API）”Key | 根目录 `.env`；Compose 作为前端构建参数 | Compose 构建拒绝，地图无法加载 | Key 按 JS API 机制会进入浏览器，必须配置准确域名白名单 |
| 高德 `AMAP_SECURITY_JS_CODE` | 与同一 Web JS Key 一起获取 | 根目录 `.env`；仅注入 Nginx 运行环境 | Compose 启动拒绝，高德鉴权失败 | 不进入前端构建/Git，由同源代理追加 `jscode` |
| `DB_PASSWORD` | 首次部署前随机生成 | `.env` 或部署 Secret | PostgreSQL/后端连接失败 | 不用默认口令；备份恢复时同步安全保管 |
| `JWT_SECRET` | 首次部署前生成至少 32 字节随机串 | `.env` 或部署 Secret | 后端拒绝启动 | 不复用示例、数据库或 Provider 密钥；泄露后轮换并使现有令牌失效 |
| `SECURE_COOKIE` | 按部署协议决定 | `.env` | 本地 HTTP 误设 true 会导致 Refresh Cookie 不发送 | 本地 HTTP 为 false；生产 HTTPS 必须 true |
| `AI_ENABLED` | 需要真实模型时再启用 | `.env` | 缺省 false，使用 Mock，不影响手工路线 | 自动测试和无额度环境保持 false |
| `AI_BASE_URL` | 从所选 OpenAI-compatible Provider 获取 | `.env` / Secret | AI_ENABLED=true 时缺失会拒绝启动 | 使用 HTTPS，核对 Provider 隐私和数据地域条款 |
| `AI_API_KEY` | 真实模型联调前从 Provider 获取 | `.env` / Secret | AI_ENABLED=true 时缺失会拒绝启动 | 不进入数据库、SSE、日志、截图或 Git；泄露立即吊销 |
| `AI_MODEL_NAME` | 根据 Provider 当前可用模型选择 | `.env` | AI_ENABLED=true 时缺失会拒绝启动 | 不硬编码到业务逻辑；换模型后重新验证兼容和限流 |
| OSS | v1.0 不需要申请 | 无 | 无影响；图片字段允许为空 | 不为“完整技术栈”虚增云服务或密钥 |

## 2. 高德地图

v1.0 只需一组高德 Web 端（JS API）配置，不需要 Web 服务 Key。高德负责 GCJ-02 底图、缩放、点选和覆盖物展示，不参与自建路网 A*。

浏览器加载器接收公开的 `AMAP_JS_KEY`，并把 `serviceHost` 指向同源 `/_AMapService`。Nginx 在运行时将 `AMAP_SECURITY_JS_CODE` 追加到地图样式与 REST 代理请求；本地 Vite 代理采用相同变量。部署到非 localhost 域名时必须更新高德域名白名单。

## 3. AI Provider

默认安全配置：

```dotenv
AI_ENABLED=false
AI_BASE_URL=
AI_API_KEY=
AI_MODEL_NAME=
```

启用真实兼容服务的模板：

```dotenv
AI_ENABLED=true
AI_BASE_URL=https://provider.example/v1
AI_API_KEY=<local-or-platform-secret>
AI_MODEL_NAME=<provider-model-id>
```

应用通过 LangChain4j OpenAI-compatible Gateway 调用模型。Provider 名称和模型没有写死在 Tool 或业务服务中；URL 是否需要 `/v1` 以 Provider 官方文档为准。外部模型超时、限流或失败时，助手保留白名单 Tool/A* 结果并降级，地图、手工路线、审核、统计和 CSV 不受影响。

不启用 LangChain4j 原始请求/响应日志，不保存隐藏思维链。业务调用日志只记录 request ID、Provider、模型、耗时、结果和脱敏 Tool 摘要。

## 4. 后端直连变量

Compose 已提供数据库网络配置；只有脱离 Compose 启动后端时才需要显式设置：

| 变量 | 应用默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/barrierfreecampus` | JDBC 地址 |
| `DB_USERNAME` | `barrierfree` | 数据库用户 |
| `DB_PASSWORD` | `barrierfree` | 仅开发默认；Compose 强制从 `.env` 传入 |

生产环境不得依赖应用内开发默认密码。

## 5. 内部可调参数

这些不是外部 Key：

- 建筑评分权重和 100m 道路范围位于 `application.yml` 的 `app.analytics.building-score`，权重总和必须为 100。
- 障碍匹配半径、时间窗口和调度开关位于数据库 `system_setting`，由管理员白名单接口维护。
- 主题选择只保存在浏览器 localStorage，不包含 Token 或业务数据。

## 6. 测试配置

`docker-compose.e2e.yml` 的口令、JWT、高德字段都是隔离测试专用占位值，外部 AI 固定关闭。E2E 使用 18080/18081 与 PostgreSQL tmpfs，完成后销毁；这些值不得复制到正式环境，也不会消耗模型额度。

Playwright 默认驱动本机 Microsoft Edge 的 Chromium 内核，不需要额外浏览器 Key 或服务账号。
