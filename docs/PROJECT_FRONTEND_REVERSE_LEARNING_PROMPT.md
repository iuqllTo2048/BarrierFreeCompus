# BarrierFreeCampus 前端逆向学习教学 Prompt

> 范围：当前项目的 Vue 3、TypeScript、状态管理、请求层、地图、智能助手界面和前端测试。
> 目标：能够独立理解页面、修改功能、排查接口与地图问题并完成前端答辩。
> 方式：从真实页面追踪 Store、Service、HTTP/SSE 和组件，一次一个阶段。

## 1. 学习者背景

- HTML、CSS 和 JavaScript 已经掌握。
- TypeScript 没有系统学习，需要结合当前项目补齐。
- 学过 Vue，但不牢固，需要重新建立响应式、组件通信和状态管理理解。
- 重点学习路线卡片与地图、高德与 GeoJSON、Axios 与 SSE、Pinia、Vitest 和 Playwright。
- 不需要课后编码任务，不按固定天数学习。

## 2. 前端真实技术栈

| 分组 | 当前技术 | 职责 |
| --- | --- | --- |
| 构建 | Node.js 22、npm、Vite | 开发服务器、构建和资源打包 |
| 框架 | Vue 3、Composition API、SFC | 页面、组件和响应式交互 |
| 语言 | TypeScript | API、状态、组件和事件类型约束 |
| 路由状态 | Vue Router、Pinia | 页面导航、登录恢复和跨页面状态 |
| 请求 | Axios、fetch、SSE | REST、令牌刷新和流式助手 |
| UI | Element Plus、自定义样式 | 表单、反馈、布局和可访问交互 |
| 地图图表 | 高德 JS API、GeoJSON、ECharts | 地图、路线与治理统计 |
| 测试 | Vitest、Playwright | 逻辑、Store 和浏览器关键链路 |

## 3. 分阶段课程表

| 阶段 | 主题 | 学完后的能力 |
| --- | --- | --- |
| 0 | Vite、Vue 应用与目录 | 知道页面怎样启动、构建和组织 |
| 1 | 项目所需 TypeScript | 读懂接口、联合类型、泛型和收窄 |
| 2 | Vue 响应式与组件通信 | 解释 ref、computed、生命周期、props、emit |
| 3 | Router、登录恢复与 Pinia | 理解页面权限和跨页面状态 |
| 4 | Axios、REST 与刷新令牌 | 追踪普通接口和 401 刷新 |
| 5 | 路线页与三张路线卡片 | 理解候选选择和地图单路线显示 |
| 6 | CampusMap、高德与 GeoJSON | 理解底图、图层、坐标和选点 |
| 7 | 智能助手、fetch 与 SSE | 区分工具进度、文本和结构化路线 |
| 8 | 用户服务、管理端 CRUD 与治理 | 阅读表单、地图编辑和审核页面 |
| 9 | ECharts、异步加载与性能 | 理解治理图表和按需加载 |
| 10 | Vitest、Playwright 与排错 | 分层定位浏览器、状态、请求和地图问题 |
| 11 | 综合联调与答辩 | 从用户操作讲清完整前端数据流 |

---

## 阶段 0：Vite、Vue 应用与目录

### 学习目标

理解源码、开发服务器和构建产物；知道 Vue 如何注册 Router、Pinia 并渲染页面。

### 前置知识

HTML、CSS、JavaScript 和 npm 基础。

### 代码入口

- `frontend/package.json`
- `frontend/vite.config.ts`
- `frontend/src/main.ts`
- `frontend/src/App.vue`
- `frontend/src/layouts/AppShell.vue`
- `frontend/Dockerfile`
- `frontend/default.conf.template`

### 调用流程

```text
npm script → Vite → main.ts → createApp
→ 注册 Pinia 和 Router → App/AppShell → 当前 View
→ npm build → dist 静态文件 → Nginx
```

### 逐段解释

Vite 开发时提供模块热更新和 `/api` 代理，构建时把 Vue、TypeScript、CSS 和资源变成浏览器静态文件。`node_modules` 是依赖缓存，`dist` 是可重新生成的构建产物，不是业务源码。

`main.ts` 是入口。页面位于 `views`，复用组件位于 `components`，跨页面状态位于 `stores`，网络访问位于 `services`，接口形状位于 `types`。

---

## 阶段 1：当前项目需要的 TypeScript

### 学习目标

掌握类型注解、接口、联合类型、泛型、`unknown` 和类型收窄。

### 前置知识

JavaScript；Java 类型经验可用于对比。

### 代码入口

- `frontend/src/types/auth.ts`
- `frontend/src/types/map.ts`
- `frontend/src/types/business.ts`
- `frontend/src/types/agent.ts`
- `frontend/src/services/http.ts`
- `frontend/src/views/UserAssistantView.vue`

### 调用流程

```text
后端 JSON（运行时未知） → service 泛型声明预期响应
→ interface/联合类型约束代码 → 边界处检查并收窄
→ 页面安全访问字段
```

### 逐段解释

TypeScript 不会验证服务器真实返回，它只在开发和构建阶段检查代码。`interface` 描述对象形状，联合类型限制角色和行动方式等字符串，泛型让 `ApiResponse<T>` 保留具体数据类型。

`unknown` 表示尚未验证，使用前必须通过 `typeof`、空值和对象检查收窄。它比 `any` 安全。TypeScript 接口类似 Java 编译期契约，但浏览器运行时不会自动按接口验证 JSON。

---

## 阶段 2：Vue 响应式与组件通信

### 学习目标

理解 `<script setup>`、`ref`、`computed`、生命周期、props、emit 和模板更新。

### 前置知识

阶段 1；JavaScript 函数和模块。

### 代码入口

- `frontend/src/views/UserHomeView.vue`
- `frontend/src/views/UserAssistantView.vue`
- `frontend/src/components/CampusMap.vue`
- `frontend/src/components/AppIcon.vue`

### 调用流程

```text
输入/点击 → 处理函数修改 ref → computed 重算 → 模板更新
→ props 传给 CampusMap → 地图 emit 事件 → 父页面更新状态
```

### 逐段解释

`ref` 保存会变化的值，`computed` 表示派生结果，`onMounted` 在组件挂载后加载数据。Vue 追踪依赖，状态变化后更新受影响的 DOM。

父页面通过 props 传入节点、道路、路线和开关；地图通过 emit 返回节点或位置选择。数据向下、事件向上，地图组件不直接修改父页面内部状态。

---

## 阶段 3：Router、登录恢复与 Pinia

### 学习目标

理解路由表、懒加载、角色 meta、导航守卫和跨页面状态。

### 前置知识

阶段 0～2；URL 和 SPA 基础。

### 代码入口

- `frontend/src/router/index.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/stores/map-data.ts`
- `frontend/src/layouts/AppShell.vue`
- `frontend/src/views/LoginView.vue`

### 调用流程

```text
访问 URL → Router 匹配 → beforeEach 调用 auth.restore
→ 未登录转 login / 角色不符回默认页 → 懒加载 View
→ View 读取 auth 与 map-data Store
```

### 逐段解释

动态 `import` 让页面按需加载。`meta.roles` 控制 USER/ADMIN 的导航体验，登录恢复让刷新页面后恢复会话。前端守卫可被绕过，真正授权仍由 Spring Security 完成。

Pinia 保存登录身份、当前数据集和地图快照等跨页面状态。输入框、弹窗和当前卡片属于局部状态，不必全部塞入 Store。

---

## 阶段 4：Axios、REST 与刷新令牌

### 学习目标

理解 service 分层、Axios 拦截器、Bearer Token、Cookie 和并发 401 共享刷新。

### 前置知识

HTTP、JSON；阶段 1 和 3。

### 代码入口

- `frontend/src/services/http.ts`
- `frontend/src/services/session.ts`
- `frontend/src/services/map-api.ts`
- `frontend/src/services/route-api.ts`
- `frontend/src/services/business-api.ts`
- `frontend/src/stores/auth.ts`

### 调用流程

```text
页面/Store → service → Axios 添加 Bearer → /api
→ 成功解析统一 JSON
或 401 → 共享 refresh Promise → HttpOnly Cookie
→ 新 access token → 重放原请求
```

### 逐段解释

页面不重复拼 URL 和认证头，service 把后端接口封装为有类型函数。`http.ts` 统一处理 Bearer、错误和刷新。

多个请求同时 401 时共享一次刷新，避免令牌轮换互相撤销。refresh token 在 HttpOnly Cookie，JavaScript 无法读取。401 是身份失效，403 是身份有效但无权限。

---

## 阶段 5：路线页与三张候选卡片

### 学习目标

理解路线请求、最多三条候选、卡片选择和地图单路线显示。

### 前置知识

阶段 1～4；数组和 computed。

### 代码入口

- `frontend/src/views/UserHomeView.vue`
- `frontend/src/services/route-api.ts`
- `frontend/src/types/map.ts`
- `frontend/src/components/CampusMap.vue`

### 调用流程

```text
起点/终点/行动方式 → route-api → routes（1～3 条）
→ 模板渲染卡片 → 点击更新 selectedRouteIndex
→ selectedRoute = routes[index]
→ mapRoutes = selectedRoute ? [selectedRoute] : []
→ CampusMap 只画当前路线
```

### 逐段解释

路线卡片是模板按数组渲染，不是在地图上点击生成。路网不足或候选去重后可能只有一两条。

`selectedRouteIndex`、`selectedRoute`、`mapRoutes` 构成响应式选择链，因此多个候选可比较，地图只显示选中路线。规划结果图层与完整校园路网图层独立，默认隐藏路网不妨碍显示规划结果。

---

## 阶段 6：CampusMap、高德 JS API 与 GeoJSON

### 学习目标

理解地图加载、GCJ-02、点线面绘制、路网开关和地图选点。

### 前置知识

阶段 2 和 5；经纬度与 GeoJSON 基础。

### 代码入口

- `frontend/src/components/CampusMap.vue`
- `frontend/src/services/map-geometry.ts`
- `frontend/src/services/map-visuals.ts`
- `frontend/src/types/map.ts`
- `frontend/src/stores/map-data.ts`

### 调用流程

```text
snapshot/路线 props → 加载 AMap 2.0 与插件
→ GeoJSON 点线面转 AMap path → Marker/Polyline/Polygon
→ 图层开关控制完整路网 → 地图点击 emit 给父页面
```

### 逐段解释

高德只负责底图和交互，不决定路线。后端路线是 GeoJSON `LineString`，坐标顺序 `[经度, 纬度]`，业务坐标为 GCJ-02。

完整校园路网和规划路线分层：前者默认可隐藏，后者规划成功后单独显示。地图选点可使用节点或临时候选点，不应强迫用户先打开全部道路。风险不能只靠颜色表达。

---

## 阶段 7：智能助手、fetch 与 SSE

### 学习目标

区分普通 REST 与 SSE；理解工具进度、模型文本、结构化路线和草稿事件。

### 前置知识

阶段 1、2、4～6；HTTP 长连接和 JSON。

### 代码入口

- `frontend/src/services/agent-api.ts`
- `frontend/src/views/UserAssistantView.vue`
- `frontend/src/types/agent.ts`
- `frontend/src/types/map.ts`

### 调用流程

```text
自然语言 → fetch POST → response.body.getReader
→ TextDecoder 与空行切分 SSE → 解析 event/data
→ tool_start/tool_result 更新时间线
→ delta 累加文本
→ route_result 更新卡片与地图
→ comparison 选择推荐卡片
→ barrier_draft 显示草稿
```

### 逐段解释

Axios 适合一次性 REST；助手需要持续接收事件，所以使用 `fetch` 和 `ReadableStream`。代码必须处理字节分块、事件空行边界和非 JSON 文本。

`delta` 是自然语言，`route_result` 是后端真实路线结构，负责卡片和地图，不能从文本中猜坐标。`comparison` 选择默认推荐，用户仍可切换。`barrier_draft` 只是草稿，确认后才走普通 REST。

---

## 阶段 8：用户服务、管理端 CRUD 与治理

### 学习目标

理解用户设施互动和障碍上报；理解管理员地图编辑、设施删除和审核页面。

### 前置知识

阶段 2～6；表单和 REST。

### 代码入口

- `frontend/src/views/UserServicesView.vue`
- `frontend/src/views/AdminDashboardView.vue`
- `frontend/src/views/AdminGovernanceView.vue`
- `frontend/src/services/business-api.ts`
- `frontend/src/services/map-api.ts`
- `frontend/src/stores/map-data.ts`

### 调用流程

```text
用户表单 → business-api → 保存互动/上报 → 刷新列表
管理地图 → map-api CRUD → reload snapshot → 重绘
治理审核 → 通过/驳回 → business-api → 更新状态
```

### 逐段解释

前端只显示和提交状态，不能自行决定报告生效。管理员地图保存成功后由 Store 重新获取后端快照。删除是否弹确认是界面策略，真正权限、归属和引用校验必须由后端保证。

---

## 阶段 9：ECharts、异步加载与性能

### 学习目标

理解治理图表数据转换、ECharts 生命周期和页面按需加载。

### 前置知识

阶段 2～4；数组转换和生命周期。

### 代码入口

- `frontend/src/views/AdminAnalyticsView.vue`
- `frontend/src/services/analytics-api.ts`
- `frontend/src/services/analytics-charts.ts`
- `frontend/src/services/analytics-charts.test.ts`
- `frontend/src/router/index.ts`

### 调用流程

```text
进入统计路由 → 异步加载页面
→ analytics-api 获取数据 → analytics-charts 转 option
→ ECharts 初始化 → setOption → resize/dispose
```

### 逐段解释

API 数据与 ECharts option 分层，纯转换函数便于测试。图表挂载时初始化、数据变化时更新、窗口变化时 resize、卸载时 dispose。Router 懒加载和异步模块避免地图、ECharts 等重依赖全部进入首屏。

---

## 阶段 10：Vitest、Playwright 与前端排错

### 学习目标

理解测试边界，并按浏览器、状态、网络和地图分层定位问题。

### 前置知识

阶段 0～9；断言和 DevTools。

### 代码入口

- `frontend/src/services/map-geometry.test.ts`
- `frontend/src/services/map-visuals.test.ts`
- `frontend/src/stores/map-data.test.ts`
- `frontend/src/services/session.test.ts`
- `frontend/src/services/analytics-charts.test.ts`
- `frontend/e2e/release-candidate.spec.ts`
- `frontend/playwright.config.ts`

### 调用流程

```text
Vitest → 纯函数、Store、会话和转换逻辑
Playwright → 登录、页面和关键用户流程

故障 → Console → Network → Vue 状态 → 请求/响应 → 地图 props/图层
→ 最小测试 → 类型检查/Lint/构建 → 必要时 E2E
```

### 逐段解释

Vitest 快速验证不依赖真实浏览器的逻辑；Playwright 验证完整用户流程，但环境更重、定位更粗。测试数量不能替代高风险边界覆盖。

地图不显示时应区分数据为空、坐标顺序、图层开关、容器尺寸和高德加载；接口问题查看 Network；页面不更新检查响应式状态。先运行最小相关测试。

---

## 阶段 11：综合联调与前端答辩

### 学习目标

从用户操作讲清完整前端数据流，形成前端排错与答辩结构。

### 前置知识

完成阶段 0～10。

### 代码入口

- `frontend/src/router/index.ts`
- `frontend/src/views/UserHomeView.vue`
- `frontend/src/views/UserAssistantView.vue`
- `frontend/src/components/CampusMap.vue`
- `frontend/src/services/http.ts`
- `frontend/src/services/agent-api.ts`
- `docs/API.md`
- `docs/PROJECT_STATUS.md`

### 调用流程

```text
用户操作 → View → Store/service → REST/SSE
→ 结构化响应 → computed → 卡片/图表/地图 props
→ 地图/图表渲染 → 后续交互

答辩 → 用户体验问题 → 状态设计 → 代码证据
→ 后端边界 → 权衡 → 限制
```

### 逐段解释

前端负责输入、状态、比较、展示和反馈，不负责执行寻路或决定审核事实。解释助手时区分 `delta` 和 `route_result`；解释地图时区分高德底图、完整路网和规划路线；解释权限时区分 Router 体验与 Security 授权。

## 4. 可直接复制的前端总教学 Prompt

```text
你是 BarrierFreeCampus 的前端私人教师。不要替我写代码，而要依据当前仓库真实实现，带我从 Vibe Coding 反向学会前端。

我的基础：HTML/CSS/JavaScript 已掌握；TypeScript 没有系统学习；学过 Vue 但不牢固；最终要独立理解页面、修改功能、排查接口与地图问题并答辩。

规则：
1. 先检查当前 Vue/TypeScript 源码、类型、service 和测试，再参考 docs/PROJECT_STATUS.md、docs/API.md。
2. 与历史文档冲突时以源码为准，不把计划说成实现。
3. 不读取或输出 .env、高德 Key/安全码、JWT、密码或模型 Key。
4. 本轮只读，不修改代码、不更新依赖、不提交 Git。
5. 一次只讲一个阶段，讲完等待我说“继续”；不布置课后编码任务。

阶段：0 Vite/目录；1 TypeScript；2 Vue 响应式；3 Router/Pinia；4 Axios/刷新；5 路线卡片；6 高德/GeoJSON；7 SSE 助手；8 用户服务/管理端治理；9 ECharts/性能；10 测试/排错；11 综合答辩。

本次只讲：[填写阶段编号和标题]

输出只能有五个一级部分：学习目标、前置知识、代码入口、调用流程、逐段解释。

必须沿真实 View → Store/service → HTTP/SSE → TypeScript 类型 → 响应式状态 → 子组件/地图讲解。TypeScript 只讲当前使用的 interface、联合类型、泛型、unknown 和收窄；ref、computed、生命周期、props、emit 必须结合真实代码。解释 Router 守卫为何不能替代 Security；解释 Axios REST 与 fetch/ReadableStream SSE；明确 delta 是文本、route_result 是地图和卡片使用的结构化事实；解释 routes、selectedRouteIndex、selectedRoute、mapRoutes；解释完整路网与规划路线分层；解释 GeoJSON LineString、[经度,纬度]、GCJ-02 和高德职责。每个结论引用真实代码，说明原因、替代方案和限制。不展示隐藏思维链。逐段解释末尾给 3～5 个口头答辩问题，然后停止。
```
