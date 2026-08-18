# PROJECT_STATUS.md — BarrierFreeCampus 项目状态

## 当前阶段

- 当前 Stage：Stage 0（规则、环境、Skills、项目治理）
- 状态：已初始化，待人工验收
- 下一关卡：Design Gate（视觉方向确认）

## 已完成

- 已确认项目冻结规格、阶段 Prompt 与设计方向文档。
- 已初始化 Git 仓库与基础目录：`backend/`、`frontend/`、`infra/`、`docs/`、`.agents/skills/`、`prompts/stages/`。
- 已建立忽略规则与外部配置样例；未保存任何真实密钥。
- 已保留项目复盘 Skill，未创建 Spring Boot 或 Vue 业务代码。
- 已在 `.agents/skills/ui-ux-pro-max/` 安装 UI UX Pro Max 2.15.0；其 `SKILL.md`、67 个脚本/数据文件可读取，并已通过 Windows `py` 调用本地查询脚本。

## 环境记录

| 工具 | 检测结果 | 备注 |
|---|---|---|
| Git | 2.52.0 | 可用 |
| Java | 21.0.9 LTS | 可用 |
| Maven | 3.9.12 | 可用 |
| Node.js | 26.3.0 | 可用，但不是常规 LTS 线 |
| npm | 11.16.0 | 可用 |
| Docker | 29.1.5 | 可用 |
| Docker Compose | 5.0.1 | 可用 |
| Python | `py` 3.13.0 | `python` 命令不可用，后续使用 `py` |

## Skill 记录

| Skill | 状态 | 说明 |
|---|---|---|
| `barrier-free-project-recap` | 已保留 | 仅基于真实代码进行项目复盘 |
| `ui-ux-pro-max` | 已安装 | 版本 2.15.0，项目级 universal 安装；后续界面工作使用 `py` 执行本地脚本 |

## 未开始

- Design Gate、Stage 1 至 Stage 9。
- 后端、前端、数据库、地图、寻路、用户业务和 AI 功能。
