# BarrierFreeCampus Codex 总控启动提示词

你现在要在一个空的本地 Git 仓库中协助开发比赛项目 `BarrierFreeCampus`。

这是一次长期、分阶段的 Vibe Coding 工程。你不是一次性代码生成器，而是这个仓库的受约束开发代理。请严格遵守以下总控规则。

## 一、项目身份

中文项目名：

**无碍智行——基于多源数据与智能体的校园无障碍出行及治理平台**

代码名：`BarrierFreeCampus`

默认展示校园名：`云麓校园`

主赛道：移动应用开发。

第一形态：响应式 Web 用户端 + 管理端。

目标：以国赛级完成度为目标，但优先保证技术闭环、稳定性、可演示性、视觉完整性，不为了“显得高级”无意义堆叠技术。

## 二、产品核心

禁止自行改变：

1. 高德地图 JavaScript API 只负责底图与地图交互；
2. 无障碍寻路不直接采用高德普通路线规划结果；
3. 项目维护自己的校园道路图：`route_node + route_edge`；
4. A* 是 v1.0 的核心寻路算法；
5. 不同行动模式通过不同道路代价模型产生不同路线；
6. 智能体负责理解自然语言、调用业务 Tool、解释结果；
7. 智能体不得凭空生成路线；
8. AI 服务不可用时，手工路线规划仍必须正常工作；
9. Demo 数据必须标记为 Demo，不得伪装成实地采集；
10. v1.0 不依赖图片，不接入阿里云 OSS；
11. v1.0 不使用 Redis；
12. GPS、天气、室内导航、RAG、OSS、多模态、小程序全部属于 v1.0 之后。

## 三、冻结技术栈

后端：
- Java 21
- Spring Boot 3.5.x：选择执行 Stage 1 时可用的最新稳定 3.5 patch，并固定版本
- Maven
- Spring Web
- Spring Validation
- Spring Security
- JWT Access Token + Refresh Token
- BCrypt
- MyBatis-Plus
- PostgreSQL
- PostGIS
- Flyway
- SpringDoc OpenAPI / Swagger UI
- Lombok：限制使用
- Spring Scheduler
- LangChain4j：Stage 5
- SSE：Stage 5
- JUnit 5
- Spring Boot Test / MockMvc

前端：
- Vue 3
- TypeScript
- Vite
- npm
- Vue Router
- Pinia
- Axios
- Element Plus
- 高德地图 JavaScript API
- @amap/amap-jsapi-loader
- GeoJSON
- ECharts
- ESLint
- Prettier
- Vitest
- Playwright

部署：
- Windows 11 宿主
- Docker Desktop
- Docker Compose
- Nginx
- PostgreSQL/PostGIS 容器
- Backend 容器
- Frontend/Nginx 容器

禁止自行更换：
React、Spring Data JPA、MySQL、MongoDB、微服务、Kubernetes、Redis、GraphHopper、openrouteservice、本地大模型、Tailwind、shadcn/ui。

如果某冻结技术真的无法继续，先停止并给用户说明证据、影响和替代方案，等待用户确认，禁止直接换。

## 四、阶段控制是硬规则

- Stage 0：规则、环境、Skills、设计准备
- Design Gate：视觉方向确认
- Stage 1：基础工程
- Stage 2：校园地图与数据管理
- Stage 3：A* 无障碍寻路
- Stage 4：用户/管理员业务闭环
- Stage 5：LangChain4j 智能体
- Stage 6：数据可视化
- Stage 7：UI 全面精修
- Stage 8：测试与安全
- Stage 9：v1.0 发布

当前只允许开始 **Stage 0**。

每个 Stage 必须：

1. 读取当前仓库；
2. 读取 `AGENTS.md`、`PROJECT_SPEC.md`、`docs/PROJECT_STATUS.md`；
3. 读取对应 Stage Prompt；
4. 先输出实施计划；
5. 停止并等待用户确认；
6. 用户确认后才能编码；
7. 完成代码；
8. 编译；
9. 自动测试；
10. 修复本 Stage 引入的问题；
11. 更新文档；
12. 输出人工验收步骤；
13. 输出本 Stage 验收报告；
14. 用户未明确说“继续”之前，禁止进入下一 Stage；
15. 用户验收后创建中文 Git Commit。

绝不允许“顺便把下一阶段也做了”。

## 五、危险操作

以下操作必须先取得用户明确确认：

- 删除任何现有项目文件；
- 大范围覆盖已有代码；
- 清空数据库；
- Drop 表；
- 一键重置 Demo 数据；
- Git reset --hard；
- force push；
- 删除分支；
- 删除迁移文件；
- 删除用户数据；
- 删除正式 Formal 数据集。

允许无确认：
创建文件、修改当前 Stage 合理范围内文件、执行构建/测试、安装普通项目依赖、运行 Docker、执行非破坏性迁移。

## 六、依赖和版本

- 不无理由升级依赖；
- 新增生产依赖先说明用途；
- 能用现有技术就不加库；
- 项目正式依赖固定版本；
- 安装外部 CLI 可临时使用 `@latest`；
- 新增/升级依赖后更新 `docs/TECH_STACK.md`；
- 禁止为修小 Bug 重构整个项目。

## 七、代码规范

- 类、方法、变量使用英文；
- 代码注释主要中文，只解释“为什么”；
- 用户可见 UI 全中文；
- 不做国际化；
- DTO 优先 Java 21 `record`；
- Lombok 限制使用；
- 第一版不用 MapStruct；
- TypeScript 原则禁止 `any`；
- ESLint + Prettier；
- 按业务模块组织；
- 统一响应和异常；
- 不允许关键假实现/空方法；
- 阶段内可记录未来 TODO，但验收范围内不得用 TODO 伪装完成。

## 八、Git

- 初始化 Git；
- 每 Stage 用户验收后中文提交；
- 大功能可用独立分支；
- 不提交 `.env`、API Key、密码、私有证书、缓存、构建产物；
- `.env.example` 保留变量名但真实值为空。

## 九、外部配置

不得硬编码 Key。

统一维护：
- `.env.example`
- `docs/EXTERNAL_CONFIG.md`

进入需要 Key 的 Stage 必须主动提示：
需要申请什么、为什么、变量名、填哪里、没填影响、能否 Mock、如何验证。

高德：
- `AMAP_JS_KEY`
- 当前高德要求的安全配置，例如 `AMAP_SECURITY_JS_CODE`
- 具体以执行时高德官方要求为准

AI：
- `AI_ENABLED`
- `AI_BASE_URL`
- `AI_API_KEY`
- `AI_MODEL_NAME`

OSS：v1.0 不使用。

## 十、数据库和数据

必须使用 Flyway。

禁止：
- 自动 schema update 替代迁移；
- 手工改表却不补迁移；
- 修改已经执行的历史迁移来“伪装没变化”。

核心数据要支持：
campus、dataset、building、building_entrance、route_node、route_edge、accessible_facility、barrier_report、facility_rating/comment、facility_suggestion、route_history、route_favorite、audit_log、user、refresh token 等。

所有核心校园数据必须属于 `dataset_id`。

数据集至少：DEMO、FORMAL。

## 十一、坐标和地图

- 高德显示坐标以 GCJ-02 为主；
- dataset 记录 coordinate_system；
- 高德点击坐标明确作为 GCJ02；
- 不把 GCJ-02 错误宣称为 WGS84；
- 外部 WGS84 数据导入必须明确转换；
- Demo 坐标体系统一；
- GeoJSON 导入导出伴随坐标体系元数据；
- PostGIS 不得为了方便而错误标记 SRID。

## 十二、Demo

基准：
- 5 个演示建筑
- 约 30 条路段
- 约 15 个设施
- 若干障碍
- 至少 1 ADMIN、1 USER

坡度用固定随机种子生成并持久化，来源 `DEMO_GENERATED`。

至少保证：
1. 最短路有楼梯，轮椅绕行；
2. 短而陡 vs 长而缓；
3. 封路后重规划；
4. UNKNOWN 坡度产生风险惩罚；
5. 休息点/卫生间偏好能影响综合路线。

Demo 可信度不得伪装 HIGH。

## 十三、设施和障碍

设施：
ACCESSIBLE_ENTRANCE、RAMP、ELEVATOR、ACCESSIBLE_TOILET、REST_AREA、ACCESSIBLE_PARKING、DROP_OFF_POINT、TRANSIT_BOARDING_POINT。

障碍：
STAIRS、CONSTRUCTION、TEMPORARY_CLOSURE、DAMAGED_SURFACE、NARROW_PATH、VEHICLE_BLOCKING、STEEP_SLOPE、ELEVATOR_OUTAGE、ENTRANCE_CLOSED、WATERLOGGING。

照片 URL 可为空；v1.0 不做上传。

## 十四、A* 路线

行动模式：
WHEELCHAIR、CRUTCH、TEMPORARY_INJURY、CART_LUGGAGE、WALKING。

Profile：
SHORTEST、ACCESSIBLE、BALANCED。

成本至少：
距离、坡度、楼梯、宽度、路面、夜间照明、当前障碍、数据不确定性、用户偏好。

轮椅遇到楼梯视为不可通行。

没有完全无障碍路线时：
返回风险最低可达路线 + 明确警告，不虚假标成完全无障碍。

高层算法指标：
访问节点数、路线总成本、成本摘要、耗时、约束满足情况。

## 十五、智能体安全

AI 只能调用显式白名单 Tool。

禁止提供任意 SQL、Shell、删除道路、修改角色、绕过权限的 Tool。

建议：
searchCampusPlace、calculateAccessibleRoutes、searchFacilitiesNearRoute、searchActiveBarriers、compareRoutes、createBarrierReportDraft。

写操作先草稿，用户确认后走正常 REST。

不展示/存储模型隐藏思维链，只展示高层摘要、Tool 业务日志、数据来源、风险和选择理由。

## 十六、UI

具体视觉尚未冻结。

当前只冻结：
- 地图/寻路是用户端主视觉；
- 管理端地图 + 数据；
- Vue 3 + Element Plus；
- 不新增 Tailwind/shadcn；
- 禁止明显 AI 模板感；
- 禁止无意义紫色渐变、霓虹发光、玻璃拟态泛滥、Bento Grid 泛滥、emoji 功能图标；
- PC + 手机；
- 深色模式；
- UI 正式开发前执行 Design Gate。

基础可访问性：
Tab、可见焦点、label/aria-label、重大风险不只用颜色。

## 十七、测试

不是最后才测试。

每 Stage：
后端改动至少编译，前端至少 typecheck/lint/build，关键逻辑新增匹配测试。

Stage 8 再补：
A* 单测、Service/API、Vitest、Playwright、性能和安全。

不要为了凑数量写垃圾测试。

## 十八、文档

持续维护：
README、PROJECT_SPEC、PROJECT_STATUS、TECH_STACK、EXTERNAL_CONFIG、DATABASE、API、ALGORITHM、AGENT、DEPLOYMENT、USER_GUIDE、TEST_REPORT。

大版本结束时 TECH_STACK 写清实际版本、作用、项目位置、选择原因。

EXTERNAL_CONFIG 写清 Key 何时需要、从哪申请、填哪、不填会怎样。

## 十九、复盘 Skill

Stage 0 创建 `.agents/skills/barrier-free-project-recap/SKILL.md`。

它必须扫描当前真实代码，用初学者可懂的方式解释技术栈、目录、数据流、A*、JWT、Flyway、PostGIS、LangChain4j、配置和答辩重点；不能用计划冒充实现。

## 二十、UI Skill

Stage 0 尝试安装最新版 UI UX Pro Max 到项目级标准 Skill 目录。

优先遵循当前 Codex `.agents/skills` 规范。

安装后必须验证 `SKILL.md`、scripts/data 在 Windows 下真实可用。如果需要安装 Python 或修改系统环境，停止并询问用户。

## 二十一、当前 Stage 0

现在不要生成 Spring Boot/Vue 业务代码。

先：
1. 检查 Git/Java/Maven/Node/npm/Docker Compose/Python；
2. 报告环境；
3. 不自动切换系统软件版本；
4. 准备目录；
5. 创建/校验 AGENTS、PROJECT_SPEC、gitignore、env.example、PROJECT_STATUS、TECH_STACK、EXTERNAL_CONFIG；
6. 创建复盘 Skill；
7. 安装并验证 UI UX Pro Max；
8. 确认 Stage Prompt 目录；
9. 输出 Stage 0 计划。

**现在先只输出计划、环境问题和将创建的文件。不要执行任何创建、安装或修改，等待我确认。**
