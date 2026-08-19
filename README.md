# BarrierFreeCampus（无碍智行）

面向校园无障碍出行与治理的地图优先 Web 应用。系统使用高德 JS API 展示 GCJ-02 底图，自建 PostGIS 路网并通过可解释 A* 生成最短、无障碍优先、综合三类路线；用户上报、管理员审核、动态绕行、治理统计和可关闭的智能路线助手构成完整闭环。

## v1.0 能做什么

- 用户：按五种行动方式和个人偏好规划路线，查看风险、坡度、楼梯、设施与可信度；维护历史/收藏，评价设施并上报障碍。
- 管理员：在地图上维护节点、道路、建筑、入口、设施和障碍，审核用户上报，管理用户与设置，查看治理统计及 CSV。
- 路线：高德只负责底图和交互；核心路线读取自建 `route_node` / `route_edge`，动态封路和已审核障碍会实时影响下一次 A*。
- 智能助手：自然语言入口调用白名单 Tool，真实路线仍由 A* 计算；AI 关闭、超时或限流时，手工路线不受影响。
- Demo：固定种子提供楼梯绕行、坡度冲突、动态封路、未知数据、设施偏好五类可复现演示场景。

## 一键启动

前置条件：Docker、Docker Compose，以及高德 Web 端（JS API）Key 与对应安全密钥。

```powershell
Copy-Item .env.example .env
# 编辑 .env，至少填写 DB_PASSWORD、JWT_SECRET、AMAP_JS_KEY、AMAP_SECURITY_JS_CODE
docker compose up -d --build
docker compose ps
```

打开 [http://localhost:8080](http://localhost:8080)。后端健康检查为 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)。完整命令、端口、数据卷和故障排查见 [部署文档](docs/DEPLOYMENT.md)。

## 本地演示账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 用户 | `demo_user` | `Demo@12345` |
| 管理员 | `demo_admin` | `Admin@12345` |

账号只用于本地比赛演示。公开或生产部署前必须替换或停用，并使用部署平台 Secret 管理所有密钥。

## 目录

```text
backend/                   Spring Boot、A*、业务、智能体与 Flyway
frontend/                  Vue 3 用户端、管理端、地图和 ECharts
docs/                      设计、接口、数据、部署、测试和交付文档
infra/                     基础设施预留目录
scripts/                   E2E 与敏感信息扫描脚本
prompts/stages/            项目分阶段需求留档（不是实现证据）
.agents/skills/            项目级 UI 与复盘 Skill
docker-compose.yml         本地正式演示编排
docker-compose.e2e.yml     隔离的发布测试编排
```

## 文档导航

- [快速使用指南](docs/USER_GUIDE.md) · [用户与管理员详细使用说明书](docs/USER_ADMIN_MANUAL.md)
- [API](docs/API.md) · [数据库](docs/DATABASE.md) · [部署](docs/DEPLOYMENT.md)
- [A* 算法](docs/ALGORITHM.md) · [智能体边界](docs/AGENT.md)
- [技术栈](docs/TECH_STACK.md) · [外部配置](docs/EXTERNAL_CONFIG.md)
- [测试报告](docs/TEST_REPORT.md) · [项目复盘](docs/PROJECT_RECAP.md) · [项目状态](docs/PROJECT_STATUS.md)

## 开发验证

```powershell
cd backend
mvn test

cd ..\frontend
npm ci
npm run test
npm run build
npm run lint
npm run format:check
```

完整浏览器验收运行 `powershell -ExecutionPolicy Bypass -File scripts/run-e2e.ps1`。它使用 18080/18081 和临时 PostGIS，不会重置 8080 的演示数据卷。

## 安全与数据边界

- 不提交 `.env`、API Key、密码、证书或构建产物；仓库只提供 `.env.example`。
- Demo 与 Formal 由 `dataset.is_demo` 隔离；重置接口只接受 Demo，Formal 请求会被后端拒绝。
- 高德坐标按 GCJ-02 保存，PostGIS 几何使用 SRID 0，并由 `dataset.coordinate_system=GCJ02` 声明语义，禁止冒充 WGS84。
- v1.0 不使用 Redis、OSS、本地大模型、微服务或第三方路线服务。

当前发布版本：`1.0.0`。本项目是校园比赛演示基线，不构成医疗建议，也不应替代实地安全判断或正式生产运维方案。
