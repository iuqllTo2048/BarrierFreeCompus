# BarrierFreeCampus 后端逆向学习教学 Prompt

> 范围：当前项目的后端、数据库、算法、智能体、测试和部署。
> 目标：能够独立阅读、修改、排错、设计受控智能体并完成后端答辩。
> 方式：以真实代码为教材，一次一个阶段，默认只读。

## 1. 学习者背景

- Java 基础已经掌握，Spring Boot 常用概念了解，Spring Security 需要复习。
- 使用过 MySQL，了解事务、外键和索引；没有使用过 PostgreSQL/PostGIS。
- 需要同时理解 A*、Yen Top-K 的数学原理和真实代码执行过程。
- 需要独立理解受控智能体、模型 JSON、上下文、Tool Calling 和降级。
- 不需要课后编码任务，不按固定天数学习。

## 2. 后端真实技术栈

| 分组 | 当前技术 | 职责 |
| --- | --- | --- |
| 框架 | Java 21、Spring Boot 3.5.x | Web、依赖注入、配置、事务和业务服务 |
| 安全 | Spring Security、JWT、BCrypt | 登录、身份校验、角色授权和令牌轮换 |
| 数据访问 | JdbcTemplate、少量 MyBatis-Plus | SQL 执行和数据映射 |
| 数据库 | PostgreSQL 17、PostGIS、Flyway | 关系数据、空间几何、索引和版本迁移 |
| 算法 | A*、Yen Top-K | 无障碍加权寻路和最多三条无环候选 |
| AI | LangChain4j、DeepSeek 兼容接口 | 受控 Tool Calling、意图理解和解释 |
| 接口 | REST、SSE、SpringDoc OpenAPI | 普通业务、流式助手和接口文档 |
| 测试部署 | JUnit、Testcontainers、Maven、Docker Compose、Nginx | 验证、构建和运行 |

## 3. 分阶段课程表

| 阶段 | 主题 | 学完后的能力 |
| --- | --- | --- |
| 0 | HTTP、REST 与 Spring 请求链 | 从 URL 找到 Controller、Service、SQL 和 DTO |
| 1 | Spring Security 与 JWT | 解释登录、刷新、授权和 401/403 |
| 2 | PostgreSQL/PostGIS | 从 MySQL 迁移认知，读懂空间数据 |
| 3 | Flyway | 理解迁移顺序、checksum 和只追加原则 |
| 4 | 地图 CRUD、GeoJSON 与恢复 | 解释地图数据怎样校验和落库 |
| 5 | A* 与成本模型 | 推导公式并追踪路线搜索 |
| 6 | Yen Top-K 与途经点 | 解释三条候选和多段合并 |
| 7 | 用户上报与管理员审核 | 解释治理状态机和事实生效条件 |
| 8 | LangChain4j 与 Tool Calling | 解释模型 JSON、可信上下文和降级 |
| 9 | 测试、配置与部署 | 分层定位测试和运行问题 |
| 10 | 综合调用链与答辩 | 用代码证据讲清架构、安全和边界 |

---

## 阶段 0：HTTP、REST 与 Spring Boot 请求链

### 学习目标

理解 HTTP 方法、状态码、JSON、DTO、统一响应和 Spring 分层；能够从一个 `/api` 请求定位最终 SQL。

### 前置知识

Java 注解、接口、异常；浏览器与服务器通信基础。

### 代码入口

- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDataController.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDataService.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapSnapshotService.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDtos.java`
- `backend/src/main/java/cn/barrierfreecampus/common/ApiResponse.java`
- `backend/src/main/java/cn/barrierfreecampus/common/GlobalExceptionHandler.java`

### 调用流程

```text
HTTP → Security Filter → Controller → DTO 校验
→ Service → Repository/JdbcTemplate/Mapper → PostgreSQL
→ record DTO → ApiResponse → JSON
```
### 逐段解释

Controller 负责 URL、HTTP 方法、参数、当前身份和响应；Service 表达业务规则和事务；Repository/JdbcTemplate 执行查询；DTO 跨边界传输数据。沿地图快照接口记录字符串如何变成 Java 类型、数据库行如何成为 `record`、Jackson 如何把结果序列化成 JSON。

统一响应让前端按固定结构读取成功或错误。全局异常处理把校验、权限和业务异常映射为状态码。重点不是背注解，而是理解每层拥有什么信息、为什么不应越层。

---

## 阶段 1：Spring Security、JWT 与刷新令牌

### 学习目标

复习 FilterChain、SecurityContext、JWT、refresh token 轮换、角色授权和 401/403。

### 前置知识

阶段 0；Header、Cookie、哈希和对称密钥基础。

### 代码入口

- `backend/src/main/java/cn/barrierfreecampus/security/SecurityConfig.java`
- `backend/src/main/java/cn/barrierfreecampus/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/cn/barrierfreecampus/auth/AuthController.java`
- `backend/src/main/java/cn/barrierfreecampus/auth/AuthService.java`
- `backend/src/main/java/cn/barrierfreecampus/auth/JwtService.java`
- `backend/src/main/java/cn/barrierfreecampus/auth/TokenHashService.java`

### 调用流程

```text
登录 → BCrypt 验证 → access JWT + refresh token
访问 → Bearer JWT → JwtAuthenticationFilter → SecurityContext → Controller
刷新 → HttpOnly Cookie → 校验 token 哈希 → 撤销旧值 → 签发新值
授权 → ROLE_USER / ROLE_ADMIN → 允许或 403
```

### 逐段解释

`SecurityConfig` 使用无状态会话，只放行认证、健康检查和 API 文档等端点。JWT Filter 在用户名密码过滤器之前。SSE 需要允许异步 Dispatcher，避免流式请求后续分发被错误拦截。

过滤器验签后仍查询用户当前角色和启用状态，因此禁用用户不必等待旧 JWT 过期。401 表示没有有效身份，403 表示身份有效但无权访问。

access token 短期使用，通过 Bearer Header 发送；refresh token 放在 HttpOnly Cookie，数据库只保存其哈希。刷新时轮换并撤销旧值，降低泄漏重放风险。前端角色守卫不是安全边界。

---

## 阶段 2：PostgreSQL、PostGIS 与空间数据

### 学习目标

把 MySQL 经验迁移到 PostgreSQL；理解 `geometry`、GiST 索引、空间查询和 GCJ-02。

### 前置知识

MySQL 表、事务、外键、B-tree 索引和 SQL。

### 代码入口

- `backend/src/main/resources/db/migration/V3__map_data_model.sql`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingRepository.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapSnapshotService.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDataSupport.java`

### 调用流程

```text
dataset.coordinate_system=GCJ02
→ building / entrance / route_node / route_edge / facility / barrier
→ PostGIS geometry 保存点线面
→ GiST 支持空间筛选
→ JdbcTemplate 转 DTO/GeoJSON
```

### 逐段解释

PostgreSQL 与 MySQL 都支持关系模型、事务、外键和普通索引。PostGIS 扩展增加空间类型、函数和 GiST 索引。B-tree 擅长等值与排序范围，GiST 支持几何相交、邻近和包围盒筛选。

`route_node` 与 `route_edge` 构成路网，建筑、入口、设施和障碍补充业务语义。GCJ-02 没有官方 EPSG 编号，因此几何使用 SRID 0，数据集另存 `GCJ02`。SRID 0 不代表 WGS84，也不能让不同坐标直接混算。

---

## 阶段 3：Flyway 与数据库演进

### 学习目标

理解迁移版本、`flyway_schema_history`、checksum 和只追加原则。

### 前置知识

阶段 2；数据库 DDL 和事务。

### 代码入口

- `backend/src/main/resources/db/migration/V1__init_security.sql`
- `backend/src/main/resources/db/migration/V3__map_data_model.sql`
- `backend/src/main/resources/db/migration/V4__seed_yunlu_demo.sql`
- `backend/src/main/resources/db/migration/V6__business_workflow.sql`
- `backend/src/main/resources/db/migration/V7__agent_assistant.sql`
- `backend/src/main/resources/db/migration/V9__add_geojson_import_backup.sql`
- `backend/src/main/resources/application.yml`

### 调用流程

```text
应用启动 → 读取迁移目录和历史表 → 校验版本/checksum
→ 顺序执行缺失 V*.sql → 数据库就绪 → Service 使用表
```

### 逐段解释

迁移顺序体现项目演进：安全基线、地图路线、业务闭环、智能体和导入备份。已经在共享数据库执行的迁移不能随意改写，否则 checksum 失败并造成环境结构不一致；新变化应增加更高版本迁移。

---

## 阶段 4：地图 CRUD、GeoJSON 与备份恢复

### 学习目标

理解节点、道路、建筑、入口、设施和障碍管理；区分 GeoJSON 交换与恢复备份。

### 前置知识

阶段 0、2、3；CRUD、事务和 JSON。

### 代码入口

- `backend/src/main/java/cn/barrierfreecampus/mapdata/AdminMapController.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapObjectService.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/GeoJsonExportService.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/GeoJsonImportService.java`
- `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDataSupport.java`

### 调用流程

```text
管理员 REST → AdminMapController → DTO/权限/数据集校验
→ MapObjectService 或 GeoJsonImportService
→ 事务检查引用、坐标和对象关系 → PostgreSQL/PostGIS
```

### 逐段解释

`MapObjectService` 集中处理数据集归属、引用关系和事务。删除对象时必须判断目标是否存在、是否属于当前数据集、是否被其他对象引用。

GeoJSON `FeatureCollection` 用于交换空间对象。普通导入导出解决数据交换；导入备份在应用导入前保存快照；恢复预览只展示影响；一键恢复才真正写入。它不是 Git，也不是数据库全量灾备。

---

## 阶段 5：A* 与无障碍成本模型

### 学习目标

推导 A*、启发式成立条件和业务成本；逐步追踪一次搜索。

### 前置知识

图、优先队列、加法和不等式。

### 代码入口

- `backend/src/main/java/cn/barrierfreecampus/routing/RouteGraph.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingRepository.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RouteCostPolicy.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/AStarRouter.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingService.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingDtos.java`
- `backend/src/test/java/cn/barrierfreecampus/routing/AStarRouterTest.java`
- `backend/src/test/java/cn/barrierfreecampus/routing/RouteCostPolicyTest.java`

### 调用流程

```text
RoutePlanRequest → 加载图和有效障碍 → RouteCostPolicy
→ AStarRouter 优先队列搜索 → previous 重建
→ 距离、时间、楼梯、坡度、风险、设施和警告 → RouteResult
```

### 逐段解释

校园路网是加权图 `G=(V,E)`。A* 使用 `f(n)=g(n)+h(n)`：`g` 是真实累计成本，`h` 是剩余估计。优先队列扩展 `f` 最小节点，松弛邻边，用 `previous` 重建路径。

项目边成本是：

```text
distance + slope + stairs + width + surface
+ lighting + barrier + uncertainty + facilityPreference
```

五种行动方式改变成本或硬限制。轮椅禁止楼梯；封闭类有效障碍使边不可走；未知数据增加惩罚。设施偏好通过缺少时增加非负成本实现，避免负边破坏最短路假设。

启发式不能高估。项目以 Haversine 为基础，并根据有效边 `edge.distance / haversine(from,to)` 的最小比例缩放，防止录入边长小于直线距离时高估。

---

## 阶段 6：Yen Top-K 与有序途经点

### 学习目标

理解 K 条候选的生成、去重，以及途经点为何分段再合并。

### 前置知识

阶段 5；路径前缀、列表和集合。

### 代码入口

- `backend/src/main/java/cn/barrierfreecampus/routing/YenTopKRouter.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RoutingService.java`
- `backend/src/main/java/cn/barrierfreecampus/routing/RouteItineraryService.java`
- `backend/src/test/java/cn/barrierfreecampus/routing/YenTopKRouterTest.java`
- `backend/src/test/java/cn/barrierfreecampus/routing/RouteItineraryServiceTest.java`

### 调用流程

```text
A* 第一条 → Yen 逐节点选择 spur node
→ 固定 root path，屏蔽重复边和成环节点
→ A* 求 spur path → 候选优先队列 → 最多 3 条

[起点, 途经点..., 终点]
→ 相邻地点逐段规划 → 合并指标、edgeIds 与 LineString
```

### 逐段解释

Yen 复用 A*，通过根路径和偏离路径生成不同无环候选。候选按总成本排序，达到 K 或耗尽后停止。不同策略可能得到相同 `edgeIds`，业务层会去重，所以不保证恰好三条。

N 个途经点形成 N+1 段。任一段不可达则整条行程不可达；全部可达时合并距离、风险、设施、警告和 GeoJSON。模型只识别地点顺序，不负责算路。

---

## 阶段 7：用户上报与管理员审核

### 学习目标

理解用户报告、正式障碍和路线事实的区别。

### 前置知识

阶段 0～3；事务和角色权限。

### 代码入口

- `backend/src/main/java/cn/barrierfreecampus/business/UserBusinessController.java`
- `backend/src/main/java/cn/barrierfreecampus/business/AdminBusinessController.java`
- `backend/src/main/java/cn/barrierfreecampus/business/BarrierGovernanceService.java`
- `backend/src/main/java/cn/barrierfreecampus/business/AdminGovernanceService.java`

### 调用流程

```text
用户上报 → 待审核、未生效 → 管理员通过/驳回并审计
→ 通过、生效、未过期障碍 → RoutingRepository → 路线封闭或加权
```

### 逐段解释

用户报告不是正式事实。初始报告待审核、低可信且不生效；只有管理员审核通过、生效且未过期的数据才影响路线。HIGH 可信度需要现场核验，不能由模型语气决定。用户和管理员 Controller 的权限不同，服务层维护状态与事务。

---

## 阶段 8：LangChain4j、受控 Tool Calling 与降级

### 学习目标

独立解释系统 Prompt、模型工具 JSON、后端校验、可信上下文、工具结果、历史消息、SSE 和降级。

### 前置知识

阶段 0、5、6、7；JSON 和大模型基础。

### 代码入口

- `backend/src/main/resources/prompts/user-route-assistant-system.txt`
- `backend/src/main/java/cn/barrierfreecampus/agent/LangChain4jAiGateway.java`
- `backend/src/main/java/cn/barrierfreecampus/agent/ControlledAgentTools.java`
- `backend/src/main/java/cn/barrierfreecampus/agent/AgentExecutionContext.java`
- `backend/src/main/java/cn/barrierfreecampus/agent/AgentService.java`
- `backend/src/main/java/cn/barrierfreecampus/agent/AgentSafetyPolicy.java`

### 调用流程

```text
自然语言 + 历史 + 系统 Prompt → 模型生成白名单 Tool JSON
→ 后端校验参数和歧义 → 可信上下文注入身份/数据集/会话
→ A*/数据库执行 → Tool Result 回模型 → 模型解释 + SSE 结构化事件
→ 模型失败时确定性降级
```

### 逐段解释

模型可能生成：

```json
{
  "name": "calculateAccessibleRoutes",
  "arguments": {
    "startPlace": "西苑5栋",
    "waypointPlaces": ["龙山体育场"],
    "endPlace": "北苑2号楼A座",
    "mobilityMode": "WHEELCHAIR",
    "preferShortest": false,
    "preferLowRisk": true
  }
}
```

LangChain4j 只注册白名单。`ControlledAgentTools` 校验文本、枚举、最多三个途经点和地点歧义；模型不能提交任意 SQL、Shell、坐标、身份或数据集。`AgentExecutionContext` 保存可信状态、限制工具次数并阻止同参数重复调用。

信任顺序是 A*/数据库/工具结果高于已确认上下文，后者高于未经验证的自然语言。写操作只能生成草稿。AI 关闭或 DeepSeek 失败时，确定性流程保留基础路线或草稿能力。

---

## 阶段 9：测试、配置、Docker 与部署

### 学习目标

理解测试层次、环境变量、容器网络和反向代理。

### 前置知识

阶段 0～8；命令行基础。

### 代码入口

- `backend/pom.xml`
- `backend/src/test/java/cn/barrierfreecampus/mapdata/MapDataIntegrationTest.java`
- `backend/src/test/java/cn/barrierfreecampus/agent/AgentSafetyPolicyTest.java`
- `backend/src/main/resources/application.yml`
- `backend/Dockerfile`
- `docker-compose.yml`
- `frontend/default.conf.template`

### 调用流程

```text
单元测试 → 算法/策略
集成测试 → Spring + Testcontainers PostgreSQL/PostGIS
Maven → JAR → Docker image
Compose → PostgreSQL + backend + frontend → Nginx /api 代理
```

### 逐段解释

单元测试快速验证算法和策略；集成测试验证真实 SQL、Flyway、事务和接口。先运行最小相关测试，再扩展完整构建。

配置文件只说明变量名称，不记录真实密码或 Key。容器内 `localhost` 指当前容器，Compose 服务名承担容器网络主机名。AI 默认可关闭，不应成为基础路线启动的硬依赖。

---

## 阶段 10：综合请求链与后端答辩

### 学习目标

把安全、数据库、算法、治理和智能体组合成完整解释与排错方法。

### 前置知识

完成阶段 0～9。

### 代码入口

- `README.md`
- `PROJECT_SPEC.md`
- `docs/PROJECT_STATUS.md`
- `docs/API.md`
- `docs/DATABASE.md`
- `docs/TEST_REPORT.md`

### 调用流程

```text
故障 → Security / Controller / Service / SQL / 数据 / 算法 / AI 分层
→ 最小复现 → 日志与请求 → 最小测试 → 根因

答辩 → 用户问题 → 当前设计 → 代码/表/测试证据 → 权衡 → 限制
```

### 逐段解释

路线异常要区分地点、路网、有效障碍、成本和候选；智能文本异常但路线正确时，要区分模型解释层与事实工具层。答辩必须指出真实类、表和测试，并主动说明没有 RAG、室内导航和 GPS，不能承诺绝对安全。

## 4. 可直接复制的后端总教学 Prompt

```text
你是 BarrierFreeCampus 的后端私人教师。不要替我写代码，而要依据当前仓库真实实现，带我从 Vibe Coding 反向学会后端。

我的基础：Java 基础没有问题；Spring Boot 常用概念了解，Spring Security 需要复习；只用过 MySQL，没有用过 PostgreSQL/PostGIS；需要同时理解算法数学原理和代码运行；最终要独立阅读、修改、排错、设计受控智能体并答辩。

规则：
1. 先检查当前源码、Flyway 迁移和测试，再参考 PROJECT_SPEC.md、README.md、docs/PROJECT_STATUS.md。
2. 与历史文档冲突时以源码为准，不把计划说成实现。
3. 不读取或输出 .env、密码、JWT Secret、高德安全码、模型 Key。
4. 本轮只读，不修改代码、不生成迁移、不提交 Git。
5. 一次只讲一个阶段，讲完等待我说“继续”；不布置课后编码任务。

阶段：0 请求链；1 Security/JWT；2 PostgreSQL/PostGIS；3 Flyway；4 地图 CRUD/GeoJSON/恢复；5 A*；6 Yen/途经点；7 治理闭环；8 LangChain4j/Tool Calling；9 测试/部署；10 综合答辩。

本次只讲：[填写阶段编号和标题]

输出只能有五个一级部分：学习目标、前置知识、代码入口、调用流程、逐段解释。

必须沿真实 Controller/Tool → Service → Repository/JdbcTemplate/Mapper → PostgreSQL → DTO 调用链讲解。PostgreSQL/PostGIS 要与 MySQL 对比；A* 要讲 f=g+h、优先队列、松弛、previous、可采纳性和 Haversine 缩放；Yen 要讲 root/spur、屏蔽边节点、候选队列、无环和去重；智能体要区分系统 Prompt、Tool JSON、后端校验、可信上下文、工具结果、历史、SSE 和降级。信任顺序是 A*/数据库/工具结果 > 已确认上下文 > 未验证自然语言。写操作只能生成草稿。每个结论引用真实代码，说明原因、去掉后的影响和当前限制。不展示隐藏思维链。逐段解释末尾给 3～5 个口头答辩问题，然后停止。
```
