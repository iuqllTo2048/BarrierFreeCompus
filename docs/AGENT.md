# AGENT.md — v1.0 智能路线助手

## 1. 定位

智能体是自然语言入口和事实解释层，不是路线计算器。地点、路线、设施和障碍事实必须来自后端白名单 Tool；路线仍由 `RoutingService` 的自建路网 A* 计算。模型输出不能覆盖认证、审核、重复上报或业务校验。

## 2. 运行链路

```text
认证用户消息
  → 安全策略和必要追问
  → 白名单 Tool（校园数据 / A* / 草稿）
  → 确定性路线比较
  → Mock 或 LangChain4j Gateway 解释
  → SSE 事件与可见消息
```

`AI_ENABLED=false` 时使用确定性 `MockAiGateway`，不发起外部请求；设为 `true` 时才创建 OpenAI-compatible LangChain4j Gateway，并要求 Base URL、API Key、模型名完整。

## 3. 白名单 Tool

| Tool | 行为与副作用 |
|---|---|
| `searchCampusPlace` | 只读启用数据集的建筑、入口、节点和开放设施，最多 8 项 |
| `calculateAccessibleRoutes` | 调用真实 A*，模型不计算或拼接路径 |
| `searchFacilitiesNearRoute` | 从路线结果汇总沿途设施 |
| `searchActiveBarriers` | 只读已审核、生效、未过期障碍 |
| `compareRoutes` | 确定性比较风险、楼梯、距离和警告 |
| `createBarrierReportDraft` | 只写两小时过期的 `ai_action_draft`，不写正式障碍 |

没有 SQL、Shell、删除、用户角色、管理员审核、Demo 重置或权限绕过 Tool。

## 4. 对话、SSE 与降级

消息接口发出 `request`、`status`、`tool_start`、`tool_result`、`delta`、`route_result`、`comparison`、`barrier_draft`、`done` 或 `error`。对话和用户可见消息持久化；调用日志记录 request ID、Provider、模型、延迟、成功状态及脱敏 Tool 摘要。

Provider 超时、限流或失败时保留已经得到的 Tool 与 A* 结果，并提示“智能服务暂时不可用，基础路线规划仍可使用”。基础地图、手工路线、审核与治理统计均不依赖外部模型。

## 5. 障碍草稿确认

助手生成草稿后，前端明确显示“尚未生效”。用户确认时由前端调用已有 `POST /api/business/barriers` 创建正式上报，再将草稿标记为 `CONFIRMED`。正式上报仍经过当前用户身份、重复检测、可信度合并和管理员审核。

## 6. 安全与隐私

- 关键词安全策略拒绝越权、系统命令、数据库、删除和密钥诱导；最终权限仍由 Spring Security 与业务服务负责。
- 不启用 LangChain4j 原始请求/响应日志；不保存或展示 API Key、完整堆栈、任意 SQL或模型隐藏思维链。
- 普通用户不能读取管理员调用日志；Tool 执行绑定当前认证用户上下文。
- 外部模型是可选解释器，不是可信授权主体。更换 Provider 后必须重新验证接口兼容、限流与隐私条款。

## 7. 已知限制

- 本地 Mock 只按确定性中文关键词识别，不等同于通用语言理解。
- 当前地点识别面向演示数据名称，不包含 RAG、向量检索、多模态或语音。
- SSE Fetch 在访问令牌恰好过期时不自动重放消息，重新登录后需再次发送。
- 模型生成文本可能不稳定；页面中的结构化路线、风险和统计才是事实依据。
