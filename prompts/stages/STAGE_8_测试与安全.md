# Stage 8 — 测试、安全与发布候选

目标 v0.9-rc。

先做测试矩阵和风险清单，等待确认。

## A* 单测
约 15～25 个高价值场景：五模式、楼梯、坡度、UNKNOWN、封路、单向、无路、同点、多 Profile、权重边界、障碍过期、Demo 场景。

## 后端
关键 Service、Security、Login/refresh、API 校验、权限、MockMvc、Flyway 空库启动人工集成验证。

## Vitest
store、route transform、表单校验、权重边界、复杂工具函数。

## Playwright 5～8 条
USER 登录+规划、轮椅路线、USER 上报、ADMIN 审核、封路后路线变化、AI disabled 降级、移动 viewport 等。

## 性能
路线计算耗时、P95（样本足够时）、路线成功率、约束满足率、楼梯避让成功率、封路重规划成功率。
不要叫“模型准确率”。

## 安全
USER 访问 ADMIN、JWT 过期/刷新、输入校验、XSS 基础、Secret 扫描、错误脱敏、AI 注入、Tool 越权、Demo reset 权限。

## 限流
v1.0 默认不做。只有确实需要时先给不引入 Redis 的简单进程内方案，等待用户确认。

生成 TEST_REPORT 草案，完成后停止。
