---
name: barrier-free-project-recap
description: 对 BarrierFreeCampus 当前真实代码库做“从零到能答辩”的技术复盘与项目解剖。适用于“复盘项目、解释当前技术栈、我看不懂 Vibe Coding、讲清楚某阶段、告诉我 AI 写了什么、准备答辩”等场景。不得用旧计划代替实际代码，不得声称未实现功能已经存在。
---

# BarrierFreeCampus Project Recap Skill

任务不是继续开发，而是扫描当前仓库并解释当前真实状态。

## 必须先读
AGENTS.md、PROJECT_SPEC.md、README、PROJECT_STATUS、TECH_STACK、EXTERNAL_CONFIG、backend/pom.xml、frontend/package.json、compose、application 配置、Flyway migrations、关键 Controller/Service/Mapper/Entity/DTO、Security/JWT、route/A*、map/GeoJSON/PostGIS、LangChain4j/Tool、前端 router/store/API/核心页面、ECharts、tests。

只解释实际存在内容。

## 先判断 Stage
给出当前 Stage、已完成、部分完成、未开始，并给文件/类证据。文档与代码冲突时指出，以真实代码/运行状态为准。

## 完整技术栈
按后端、数据库/GIS、前端、地图、AI、测试、部署、工具分组。每项说明：
1. 是什么；
2. 为什么用；
3. 项目文件位置；
4. 和谁配合；
5. 用户至少需理解到什么程度。

## 目录解释
逐层解释真实源码目录；不要把 target/node_modules 当核心源码。

## 完整请求链
路线功能存在时，用真实路径解释：
Vue → API → Controller → Service → A* → Mapper/数据库 → DTO → 前端。

AI 存在时：
自然语言 → LangChain4j → Tool → Service → A* → Tool Result → 高层解释。

## 寻路重点
如果实现，解释 Node/Edge、g/h/f、heuristic、Profile、五行动模式、楼梯、坡度、UNKNOWN、动态封路、可解释结果、三路线，并引用真实类/方法。
未实现就明确说未实现。

## 陌生技术
重点解释 Flyway、PostGIS、GeoJSON、JWT/Refresh Token、Swagger、Nginx、Docker Compose、SSE、LangChain4j Tool Calling，并始终结合真实项目。

## 配置
列必填/可选/可为空、从哪申请、填哪、不填影响、是否禁止提交 Git。绝不输出真实 secret 值。

## Vibe Coding 风险
只审查不自动改：重复代码、过度设计、未使用 AI 生成代码、假实现、TODO、未引用依赖、文档不一致、测试缺口、答辩风险。

## 学习优先级
分“必须学懂 / 建议学懂 / 可暂不深究”，每项给文件路径。

## 口头复述
最后生成一份用户能直接口头讲的：问题、架构、寻路核心、AI 核心、数据可信度、Demo/真实数据、安全边界、限制。

## 严禁
不要求或泄露隐藏思维链；不把计划当实现；不编造测试和真实数据；不输出 secret；不因复盘直接重构/删除。
