# Stage 0 — 规则、环境、Skills、项目治理

只做 Stage 0，不创建正式 Spring Boot/Vue 业务。

先读总控、AGENTS、PROJECT_SPEC。

第一步只输出计划，等待确认。

确认后执行：

1. 检查 Windows 环境：
   - `git --version`
   - `java -version`
   - `mvn -version`
   - `node -v`
   - `npm -v`
   - `docker --version`
   - `docker compose version`
   - `python --version` / `py --version`
2. 不自动安装/切换系统级 Java、Node、Python、Docker；发现问题先报告。
3. Node 若不是稳定 LTS，只报告风险，不自动改。
4. 初始化/检查 Git。
5. 创建基础目录：`backend/`、`frontend/`、`infra/`、`docs/`、`.agents/skills/`、`prompts/stages/`。
6. 建立/保留：`AGENTS.md`、`PROJECT_SPEC.md`、`.gitignore`、`.env.example`、`docs/PROJECT_STATUS.md`、`docs/TECH_STACK.md`、`docs/EXTERNAL_CONFIG.md`、`docs/DESIGN_DIRECTION.md`。其中 `DESIGN_DIRECTION.md` 已由小组确认，禁止擅自覆盖或改写。
7. 创建 `barrier-free-project-recap` Skill。
8. 安装 UI UX Pro Max：
   - 优先使用当前 CLI 的 universal/Agent Standard 模式，让 Skill 落在 `.agents/skills/`
   - 可尝试 `npx ui-ux-pro-max-cli@latest init --ai universal`
   - 安装前确认 Python 3
   - 安装后验证 `.agents/skills/ui-ux-pro-max/SKILL.md`
   - 检查 Windows 下 scripts/data 真实可读，不是失效指针
   - 失败就停止并报告
9. 不运行 Design Gate，先只准备。
10. Stage 0 完成后输出验收报告和下一步 Design Gate 建议。
11. 用户确认后中文 Git commit，例如：`chore: 初始化项目规则与开发环境`
12. 停止。
