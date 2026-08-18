# Design Gate — 只确定视觉方向，不写正式业务页面

使用已安装的 `ui-ux-pro-max` Skill；如果 Skill 未被发现，先报告，不要假装使用。

读取：AGENTS.md、PROJECT_SPEC.md、PROJECT_STATUS、`docs/DESIGN_DIRECTION.md`、当前已有页面（如有）。

`docs/DESIGN_DIRECTION.md` 是小组已经确认的方向层约束。三套方案只能在该方向内具体化，不能推翻它。

为 BarrierFreeCampus 提出 **3 套明显不同、都适合比赛展示的 UI/UX 方向**。

现在禁止大规模编写正式业务页，禁止重构业务。

共同约束：
- Vue 3 + Element Plus；
- 不新增 Tailwind/shadcn；
- 用户端地图与寻路是核心；
- 管理端强调地图 + 数据治理；
- PC 和手机响应式；
- 后续深色模式；
- 避免“AI 自动生成网页”套路；
- 禁止紫色科技渐变、玻璃拟态泛滥、Bento Grid 泛滥、emoji 功能图标、无意义发光和巨型营销 Hero；
- 适合“校园公共服务 + 无障碍出行 + 智能路线”；
- 重大风险不能只靠颜色表达。

每套方案说明：
1. 设计关键词；
2. 背景/品牌色方向（先不锁具体 HEX）；
3. 字体层级；
4. 地图页布局；
5. 路线结果卡片；
6. 移动端；
7. 管理后台；
8. ECharts；
9. 深色模式；
10. 为什么不像 AI 模板；
11. 潜在缺点。

最后对比，不替用户决定，输出后停止。

用户确认后：
1. 生成 `docs/DESIGN_SYSTEM.md`；
2. 创建 `.agents/skills/barrier-free-ui/SKILL.md`；
3. Skill 只约束项目设计，不重新发明业务。
