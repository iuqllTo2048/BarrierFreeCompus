---
name: barrier-free-ui
description: 为 BarrierFreeCampus 设计或实现用户端、管理端、地图、路线、图表及相关界面时，应用已确认的“静谧导览”设计系统。仅用于本项目的 UI/UX 工作。
---

# BarrierFreeCampus UI Skill

## 使用前

先读取：

1. `AGENTS.md`
2. `docs/DESIGN_DIRECTION.md`
3. `docs/DESIGN_SYSTEM.md`

以实际代码和当前 Stage Prompt 判断可改动范围；设计规范不授权创建未进入当前 Stage 的业务功能。

## 项目约束

- 技术栈限定 Vue 3、TypeScript 与 Element Plus；主题化 Element Plus，不增加 Tailwind、shadcn 或其他 UI 框架。
- 用户端以地图和路线为视觉核心；PC 使用轻量顶栏，路线控制区不应挤压地图主画布；移动端使用地图配底部抽屉。
- 管理端强调地图、数据可信度、障碍与审核联动，表格仅作为数据明细和操作入口。
- 路线、风险、可信度和状态必须使用颜色之外的文字、图标、线型或标签表达；正式功能图标使用统一线性 SVG 图标，禁止 emoji。
- 使用 `DESIGN_SYSTEM.md` 的语义 Token、系统字体、间距、圆角、深色模式和动效规则；不要添加紫色科技渐变、玻璃拟态、Bento Grid、霓虹发光或营销式巨型 Hero。

## 交付检查

- 桌面与手机布局均保持地图优先，且在窄屏不依赖仅拖拽才能完成的操作。
- 所有键盘可达控件拥有清晰焦点；输入框有 label；图标按钮有 `aria-label`。
- 为浅色和深色模式分别检查正文、边框、风险状态和路线的层次；尊重 `prefers-reduced-motion`。
- 页面实现后按当前 Stage 的测试要求验证，不把设计规范当作已实现功能。
