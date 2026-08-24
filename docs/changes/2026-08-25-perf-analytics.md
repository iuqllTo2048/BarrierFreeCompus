# 提交说明：perf: 治理洞察地图与图表按需加载

## 提交信息
`perf: 治理洞察地图与图表按需加载`

## 背景
`AdminAnalyticsView` 生产分包约 651.66kB（gzip 218.20kB），进入治理洞察首包拖慢加载；文档标注后续可做按需拆分。

## 改动内容
- `AdminAnalyticsView.vue` 将 `CampusMap`（高德地图）与 `EChartPanel`（ECharts）改为 `defineAsyncComponent` 异步加载。

## 解决什么问题 / 新效果
治理洞察主包 651.66kB → 111.55kB（gzip 218.20 → 33.75kB），ECharts 与地图拆为独立分包按需加载、可跨页面缓存；页面视觉与交互不变。

## 验证
- Vite production build 通过，分包大小对比生效。
- typecheck / lint / 31 Vitest 通过。
- 无头浏览器打开 `/admin/analytics` 渲染正常，0 JS 错误。
