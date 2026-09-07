# 修复：深色模式下地点选择下拉框可读性

## 背景

用户端切换到深色模式后，起点和终点的地点选择下拉框仍使用 Element Plus 的默认浅色浮层。白色背景与浅灰选项文字对比度不足，影响地点检索和路线规划。

## 原因

项目主题已覆盖基础表面和文字变量，但 Element Plus 选择器浮层由 Teleport 渲染，仍读取默认的 `--el-bg-color-overlay`、`--el-fill-color-light` 等变量，因此没有随深色主题切换。

## 改动

- 在 `frontend/src/style.css` 补齐 Element Plus 的浮层、填充、边框、禁用和选项悬停主题变量，使其映射到项目的语义色。
- 深色模式下，地点选择浮层背景为 `#172321`，悬停/填充色为 `#20302d`，文字和边框继承项目高对比度色板。
- 在 `frontend/e2e/release-candidate.spec.ts` 增加回归场景：移动端切换深色主题后打开“起点”选择器，断言浮层可见且关键主题变量为暗色值。

## 影响范围

- 用户端路线页的起点、终点和同类 Element Plus 下拉浮层。
- 不涉及业务接口、数据库迁移、新依赖或新增配置。

## 验证

- `npm run test`：31 个 Vitest 用例通过。
- `npm run lint`、`npm run typecheck`、`npm run build`：通过。
- `$env:E2E_BASE_URL='http://localhost:8080'; npm run test:e2e -- release-candidate.spec.ts -g "主题和地图"`：1 个 Playwright 场景通过。
- Docker 重建前端后，在真实页面打开深色主题地点选择器，浮层背景计算值为 `rgb(23, 35, 33)`。

## 已知说明

全仓 `npm run format:check` 仍报告既有的 13 个文件格式偏差；本次新增的 Playwright 测试文件已单独通过 Prettier 检查，未对无关文件做批量格式化。
