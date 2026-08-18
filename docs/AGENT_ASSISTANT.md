# 智能路线助手边界与运行说明

## 定位

智能体是自然语言入口和事实解释层，不是路线计算器。路线、设施和障碍结论必须来自后端白名单 Tool；模型输出不能覆盖权限、审核和业务校验。

## 运行链路

```text
认证用户消息
  → 安全策略与必要追问
  → 白名单 Tool（校园数据 / A* / 草稿）
  → 确定性路线比较
  → Mock 或 LangChain4j Gateway 解释
  → SSE 事件
```

默认 `AI_ENABLED=false`：启用 `MockAiGateway`，不发生外部请求。`AI_ENABLED=true` 时才创建 `LangChain4jAiGateway`，并要求 Base URL、API Key、模型名全部存在。

## Tool 边界

| Tool | 数据与副作用 |
|---|---|
| `searchCampusPlace` | 只读启用数据集中的建筑、入口、节点与开放设施，最多 8 项 |
| `calculateAccessibleRoutes` | 调用 `RoutingService` 的自建路网 A*，不由模型计算路径 |
| `searchFacilitiesNearRoute` | 从真实路线结果汇总沿途设施 |
| `searchActiveBarriers` | 只读 `APPROVED + active + 有效期内` 障碍 |
| `compareRoutes` | 确定性比较风险、楼梯、距离与警告 |
| `createBarrierReportDraft` | 仅写入 `ai_action_draft`，两小时过期，不写正式障碍表 |

禁止向模型暴露 SQL、Shell、删除、用户角色、审核、Demo 重置和权限绕过能力。

## 上报确认

草稿生成后，前端必须显示“尚未生效”。用户确认时调用现有 `POST /api/business/barriers`；成功后只把对应草稿标为 `CONFIRMED`。正式上报仍经过用户身份、重复上报检测、可信度规则和管理员审核。

## 日志和隐私

- 保存必要可见 conversation/message。
- 保存 request ID、Provider、模型、延迟、结果与脱敏 Tool 摘要。
- 不启用 LangChain4j 原始请求/响应日志。
- 不保存或展示 API Key、完整异常堆栈、任意 SQL 或模型隐藏思维链。
- 普通用户不能访问管理员调用日志。

## 降级

Provider 超时或异常统一返回：`智能服务暂时不可用，基础路线规划仍可使用`。基础地图与 A* 路线接口不依赖 AI，可继续工作。
