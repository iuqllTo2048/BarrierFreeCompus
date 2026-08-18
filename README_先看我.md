# 先看这个：Prompt 包怎么用

你拿到的是 **给 Codex 用的 Prompt 包**，不是项目源码。

最简单用法：

1. 新建空文件夹 `BarrierFreeCampus`；
2. `git init`；
3. 用 Codex Desktop 打开这个文件夹；
4. 把 `00_MASTER_BOOTSTRAP_PROMPT.md` 全部复制给 Codex；
5. Codex 必须先给计划；
6. 你确认它执行 Stage 0；
7. Stage 0 完成后必须停；
8. 你人工验收；
9. 你说“继续”，再进入下一阶段。

推荐把本包中的：
- `AGENTS.md`
- `PROJECT_SPEC.md`
- `.agents/skills/barrier-free-project-recap/SKILL.md`
- `prompts/stages/*`

复制到项目对应位置；也可以让 Stage 0 的 Codex 按这些内容创建。

**不要一次把 Stage 0～9 的所有 Prompt 当成一个开发任务执行。**

总控 Prompt 已经让 Codex 知道全局目标；Stage Prompt 负责控制当前阶段。

## Codex Skill

当前项目级 Skill 使用：

```text
.agents/skills/
```

UI UX Pro Max 优先使用当前 CLI 的 Agent Standard / universal 方式安装到 `.agents/skills/`。Stage 0 会先检查，再执行类似：

```powershell
npx ui-ux-pro-max-cli@latest init --ai universal
```

然后必须确认：

```text
.agents/skills/ui-ux-pro-max/SKILL.md
```

实际存在且 Windows 下脚本/数据可用。

如果 Python 缺失，Codex 应停下来让你决定安装，不得擅自改系统环境。

## 每完成一个 Stage

你应该看到：
- 实现内容
- 关键文件
- 新依赖
- 数据库迁移
- 外部配置/Key
- 测试结果
- 手工验收
- 限制
- 下一阶段

然后它应该停。

如果它直接开始下一阶段，回复：

```text
停止。你违反了 Stage Gate。回到当前阶段验收状态，不得继续新增下一阶段功能。
```


## 已加入的小组 UI 设计方向

本包新增：`docs/DESIGN_DIRECTION.md`。

已确认方向：**公共服务感 + 轻科技感**。

它只冻结方向，不锁死具体色号、字体和组件细节。执行 `DESIGN_GATE_视觉确认.md` 时，Codex/UI Skill 必须读取它，并在该方向内提出 3 套具体方案。小组确认后再生成 `docs/DESIGN_SYSTEM.md` 和 `.agents/skills/barrier-free-ui/SKILL.md`。
