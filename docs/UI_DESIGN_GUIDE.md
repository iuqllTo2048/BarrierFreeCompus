# 无碍智行 v1.0 全站 UI 设计规范（含动画与交互细节）

> 状态：设计规范文档（已沉淀，暂不实施）。
> 实施约定：等所有路线/功能开发完成后再按本规范统一做设计；本方案后续会持续完善迭代。
> 上游约束：`docs/DESIGN_DIRECTION.md`（方向层）、`docs/DESIGN_SYSTEM.md`（Token 权威）、`.agents/skills/barrier-free-ui/SKILL.md`。
> 本文是交互与动效层规范；色值、字号、间距以 DESIGN_SYSTEM.md 为准，不重复定义冲突值。

## 1. 设计原则

**静谧导览**：清晰、可信、友好、克制。轻科技感只来自地图、路线、数据层级与克制动效，不使用紫色渐变、霓虹、玻璃拟态泛滥、Bento Grid、营销式巨型 Hero。

- 用户端优先级：地图与路线 → 起终点与行动模式 → 风险与设施 → AI 辅助。
- 管理端优先级：地图与数据治理 → 状态和风险 → 图表 → 表格。
- 重大状态必须同时使用颜色、文字、图标或线型中的至少两种表达。
- 动效服务于"理解发生了什么"，不为炫技。

## 2. 动效系统（核心新增）

### 2.1 动效 Token

在 `:root` 新增（深色模式不变）：

| Token | 值 | 用途 |
|---|---|---|
| `--motion-duration-instant` | `100ms` | 按钮按下、选中态、状态切换 |
| `--motion-duration-fast` | `200ms` | 悬停、焦点、边框、图标、卡片状态 |
| `--motion-duration-base` | `280ms` | 弹窗、菜单、抽屉、展开收起 |
| `--motion-duration-slow` | `360ms` | 地图路线高亮、Marker 弹出、图表过渡 |
| `--motion-ease-out` | `cubic-bezier(0.2, 0, 0, 1)` | 进入/展开（快出缓停） |
| `--motion-ease-in-out` | `cubic-bezier(0.4, 0, 0.2, 1)` | 过渡/位移 |
| `--motion-ease-spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Marker 弹出、强调元素 |

### 2.2 动效原则

- 只动画 `transform` 与 `opacity`，避免动画 `width/height/top/left` 引起布局抖动。
- 默认状态变化 ≤280ms；地图/路线/图表 ≤360ms。
- 所有动效在 `prefers-reduced-motion: reduce` 下直接呈现最终状态（保留透明度过渡除外）。
- 进入动画统一：元素从 `opacity:0 + translateY(4px)` 到 `opacity:1 + translateY(0)`；退出反向。
- 数据刷新不整体闪烁；图表数值变化用 300ms 过渡。

### 2.3 动效清单

| 元素 | 触发 | 时长 | 缓动 | 说明 |
|---|---|---|---|---|
| 按钮 | hover | 200ms | ease-out | 背景/边框/文字色过渡 |
| 按钮 | active | 100ms | ease-out | 轻微下压 `scale(0.98)`，不位移 |
| 导航项 | hover/active | 200ms | ease-out | 背景 + 文字色过渡 |
| 图标按钮 | hover/focus | 200ms | ease-out | 背景 + 边框出现 |
| 卡片/面板 | hover | 200ms | ease-out | 仅边框加深，**不浮起不放大** |
| 弹窗/抽屉 | 进入 | 280ms | ease-out | opacity + translateY |
| 下拉菜单 | 进入 | 200ms | ease-out | opacity + translateY(4px) |
| 展开/收起 | 内容区 | 280ms | ease-in-out | 高度过渡需谨慎，优先用网格行动画 |
| 路线高亮 | 选中 | 360ms | ease-out | 线宽/透明度过渡 |
| Marker | 出现 | 360ms | spring | 缩放 0.85→1 + opacity |
| 图表 | 数据更新 | 300ms | ease-in-out | ECharts animationDuration |
| 骨架/加载条 | 出现 | 280ms | ease-in-out | 不闪烁，柔和渐变 |
| Toast/消息 | 进入/退出 | 280ms | ease-out | opacity + translateY |

## 3. 悬停 / 按下 / 焦点 / 禁用状态

### 3.1 按钮

| 状态 | 主按钮（primary） | 次按钮（default） | 文字按钮 |
|---|---|---|---|
| 默认 | 主色底、白字 | 白底/浅底、主色边框 | 透明、文字主色 |
| hover | `--color-primary-hover` 底 | 背景 `--color-surface-muted`、边框加深 | 背景 `--color-surface-muted` |
| active | 再深一档、`scale(0.98)` | 同上 + `scale(0.98)` | 同上 |
| focus-visible | 2px `--color-focus` 焦点环 | 同左 | 同左 |
| disabled | 40% 不透明度，无阴影 | 同左 | 同左 |
| loading | 显示 loading 图标，禁止重复点击 | 同左 | 同左 |

### 3.2 输入控件

- hover：边框用 `--color-border` 加深一档。
- focus：边框 `--color-primary` + 2px 焦点环（`--color-focus`，浅色半透明）。
- error：边框 `--color-danger` + 错误说明文字（不只靠颜色）。
- 输入框高度 ≥40px，移动端 ≥44px。

### 3.3 导航

- 顶栏导航项 hover：背景 `--color-surface-muted`、文字 `--color-primary`；active 同现有（背景 + 加粗）。
- 移动端菜单项 hover：背景 `--color-surface-muted`；active 同左。
- 底部 tab/分页：hover 文字主色 + 下划线或背景色块。

### 3.4 卡片 / 列表 / 表格

- 普通卡片：hover 仅边框加深，不浮起、不加阴影（阴影只用于地图浮层、抽屉、弹窗）。
- 列表项/表格行：hover 背景 `--color-surface-muted`，内容不位移。
- 可选中卡片（路线结果、数据集）：选中态主色边框 + 背景 `--color-surface-muted` + 选中标记图标。

### 3.5 地图与图表

- Marker hover：放大 1.1 + 弹窗预览；键盘可聚焦（`tabindex=0`）。
- 路线 hover/选中：线宽 +1px、透明度提升，弹窗显示名称与风险摘要。
- 图表：tooltip 跟随；图例 hover 高亮对应系列；柱/线 hover 有轻微强调（透明度/加粗）。

## 4. 组件与页面规范

### 4.1 顶栏

- 64px 轻量顶栏：左标识、中导航、右账户区。
- ≤1000px：导航文字隐藏，显示 ≡ 菜单按钮（已实现）。
- 移动端菜单：顶部 65px 下方弹出，两列网格，点击后关闭。

### 4.2 登录页

- 左侧品牌语境区（标语 + 图例），右侧表单卡片。
- 表单卡片 hover 无变化；提交按钮主色；错误提示带图标 + 文字。

### 4.3 用户端 · 路线规划

- 地图主画布；左 340px 控制面板（桌面）或底部抽屉（移动）。
- 路线结果卡片：结论 → 距离/时间 → 风险摘要 → 折叠的成本明细。
- 三类路线：灰虚线（最短）、深青绿实线（无障碍）、蓝绿实线（综合），卡片同色系标签。

### 4.4 用户端 · 智能路线助手（对话式，见 DESIGN_GATE_智能助手对话界面.md）

- 身份条 + 对话流 + 地图区；流式输出、工具时间线、路线卡片、草稿确认卡。
- 降级时顶部状态条提示，手工路线不受影响。

### 4.5 管理端 · 地图数据

- 地图 + 可收起检视器；工具栏按钮组；GeoJSON 导入/导出/备份对话框复用统一弹窗样式。
- 对象选中：地图 Marker 高亮 + 检视器同步，双通道反馈。

### 4.6 管理端 · 治理工作台 / 治理洞察

- 审核、用户、设置用紧凑表格 + 操作按钮；关键状态用标签（颜色 + 文字）。
- 治理洞察：筛选条 → 摘要卡 → 图表区 → 地图联动；图表动画 300ms。

## 5. 深色模式

- 全部使用语义 Token，`data-theme="dark"` 时自动切换。
- 地图底图、ECharts、Element Plus 跟随同一主题源。
- 检查项：正文对比度 ≥4.5:1；边框、焦点环、路线在深色下仍可辨。

## 6. 响应式断点

| 断点 | 行为 |
|---|---|
| ≤1280px | 图表筛选紧凑化 |
| ≤1000px | 顶栏折叠为菜单按钮 |
| ≤900px | 治理洞察/分析页单列 |
| ≤800px | 地图优先布局、底部抽屉、表格简化 |
| ≤767px | 移动端卡片化、44px 触控目标 |

## 7. 无障碍

- 键盘：Tab 顺序与视觉一致；焦点环 2px 可见；弹窗/抽屉焦点圈闭。
- 语义：输入有 label；图标按钮有 `aria-label`；地图对象可聚焦。
- 播报：助手回复、工具状态、加载完成使用 `aria-live="polite"`。
- 动效：`prefers-reduced-motion: reduce` 时关闭非必要动画。

## 8. 实施落地清单（供编码使用）

1. `style.css` `:root` 增加动效 Token（2.1）。
2. 统一按钮/导航/卡片/输入控件 hover-active-focus 状态（3.1–3.4）。
3. 弹窗/抽屉/菜单进入动画与 `prefers-reduced-motion` 兜底。
4. 地图 Marker/路线/图表动效接入 Token。
5. 逐页走查深浅色、断点与无障碍检查项。

## 9. 待确认

- 动效幅度：当前建议"克制档"（hover 不浮起、不放大）；如需更活泼可开放"卡片轻微上浮 2px"。
- 本轮是否先实施"动效 Token + 全局 hover/focus 状态"，地图/图表动效随后单独 Stage。
