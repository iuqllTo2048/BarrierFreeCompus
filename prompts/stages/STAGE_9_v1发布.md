# Stage 9 — v1.0 发布与交付

不新增主要业务。

## 部署
- `docker compose up -d`
- 启动/停止/日志命令
- DB volume
- 端口
- 健康检查
- `http://localhost`
- Nginx `/api`

## Demo
- 安全初始化
- 管理员一键重置
- 二次确认
- 只影响 Demo
- Formal 绝不受影响
- 重置后五类演示场景恢复

## 文档正式完成
README、DATABASE、API、DEPLOYMENT、USER_GUIDE、TEST_REPORT、ALGORITHM、AGENT、TECH_STACK、EXTERNAL_CONFIG、PROJECT_STATUS。

TECH_STACK 每项写名称、实际版本、作用、项目位置、选择原因。

EXTERNAL_CONFIG 列高德、AI、当前无需 OSS，写清获取时机、填写位置、缺失影响和安全规则。

## 最终 Review
代码、UI、文档一致性、TODO/假实现、未用依赖、Secret、自动测试、手工演示。

## 复盘
使用 `barrier-free-project-recap` 生成 `docs/PROJECT_RECAP.md`。

最后输出发布验收报告；等待用户确认；确认后中文 commit；创建 v1.0 tag 前再确认。

完成后停止。
