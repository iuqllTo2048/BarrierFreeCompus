# 提交说明：refactor: 拆分业务与治理统计后端服务

## 提交信息
`refactor: 拆分业务与治理统计后端服务`

## 背景
交接文档点名：`BusinessService`、`AnalyticsService` 较大，后续可按领域拆分；`MapDataService` 的拆分已随 GeoJSON 恢复提交完成。

## 改动内容
- business 包：`BusinessService` 改为门面，拆分出 `UserProfileService`（个人资料）、`FacilityInteractionService`（设施评分/评论/建议）、`BarrierGovernanceService`（上报/审核/自动过期）、`UserDataService`（历史/收藏）、`AdminGovernanceService`（总览/建议/用户/审计/设置/启停/Demo 重置）、`BusinessSupport`（共享工具）。
- analytics 包：`AnalyticsService` 改为门面，拆分出 `AnalyticsQueryService`（统计查询）、`BuildingScoreService`（建筑评分）、`AnalyticsCsvService`（CSV 导出）；权重校验保留。

## 解决什么问题
降低单文件体积与耦合，让每个领域职责清晰、更易测试与维护；Controller 调用与 DTO 完全不变，行为不变。

## 验证
- 后端 74/74 JUnit 通过（纯重构，无行为变化）。
- Docker 重建后 db/backend healthy，health `UP`。

## 注意
本提交不含 mapdata 包（已在 GeoJSON 恢复提交中拆分）；不含任何前端改动。
