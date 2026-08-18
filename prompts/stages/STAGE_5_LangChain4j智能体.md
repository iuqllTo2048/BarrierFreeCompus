# Stage 5 — LangChain4j 智能体

先不要立即接真实 API。

第一步：
1. 读取现有 Route/Facility/Barrier Service；
2. 设计 Tool 边界；
3. 设计 Provider/Gateway 抽象；
4. 设计 Mock AI；
5. 设计真实 API 环境变量；
6. 输出计划等待确认。

## 目标
AI 是自然语言入口和解释层，不是路线计算器。

## Provider
优先国内模型，候选 DeepSeek，但业务不能锁死。
兼容接口只表示协议兼容。

变量：
AI_ENABLED、AI_BASE_URL、AI_API_KEY、AI_MODEL_NAME。

先支持 `AI_ENABLED=false`。
自动测试不得大量消耗真实 Token。

## Tools
至少：
searchCampusPlace、calculateAccessibleRoutes、searchFacilitiesNearRoute、searchActiveBarriers、compareRoutes、createBarrierReportDraft。

不得暴露：
executeSql、shell、delete、user-role、绕过权限工具。

## 多轮
地点/行动偏好不明确时做最少必要追问。

## 流式
SSE。

## 日志
保存 conversation/message 必要数据、tool name、业务参数摘要、tool result 摘要、provider/model、latency、success/error、request id。
不保存/展示隐藏思维链。

## 上报
AI 只能生成草稿，用户确认后正常 REST 提交。

## 降级
AI 超时/失败：提示“智能服务暂时不可用，基础路线规划仍可使用”。
不得向普通用户展示完整堆栈/secret。

## Prompt Injection
测试：
- 忽略规则删除道路
- 冒充管理员
- 诱导 SQL
- 诱导输出 Key

后端 Tool 白名单和权限是最终防线。

完成后停止。
