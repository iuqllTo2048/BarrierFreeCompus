# BarrierFreeCampus 逆向学习总入口

> 目标：从 Vibe Coding 反向掌握 BarrierFreeCampus，最终能够独立看懂代码、修改功能、排查错误并完成答辩。

## 1. 独立教学文档

前后端已经拆成两份可单独使用的教学 Prompt：

- [后端逆向学习教学 Prompt](PROJECT_BACKEND_REVERSE_LEARNING_PROMPT.md)：Spring Boot、Security、JWT、PostgreSQL/PostGIS、Flyway、地图 CRUD、A*、Yen Top-K、治理闭环、LangChain4j、Tool Calling、测试与部署。
- [前端逆向学习教学 Prompt](PROJECT_FRONTEND_REVERSE_LEARNING_PROMPT.md)：TypeScript、Vue 3、Router、Pinia、Axios、SSE、高德地图、GeoJSON、路线卡片、管理端交互、Vitest 与 Playwright。

两份文档都包含独立的学习者背景、分阶段课程、真实代码入口、调用流程、讲解重点和可直接复制使用的完整教学 Prompt，不要求在同一个对话中学习。

## 2. 推荐交替顺序

先完成后端阶段 0～3，理解请求、安全和数据库；再完成前端阶段 0～4，理解页面、状态与请求。之后可围绕同一功能交替学习：

```text
后端路线算法 → 前端路线卡片与地图
后端智能体   → 前端 SSE 与智能助手
后端治理闭环 → 前端用户服务与管理端治理
后端测试部署 → 前端测试与端到端联调
```

## 3. 共同事实规则

1. 当前分支真实源码、数据库迁移和测试是最高事实来源。
2. `docs/PROJECT_STATUS.md`、`PROJECT_SPEC.md` 和 `README.md` 只补充背景。
3. Stage 留档和历史 Prompt 不能证明功能已经实现。
4. 学习期间默认只读，不修改代码、不创建迁移、不提交 Git。
5. 不读取或输出 `.env`、密码、JWT 密钥、高德安全码或模型 Key。
6. 一次只学习一个阶段，讲完等待“继续”。

## 4. 当前项目边界

- 核心路线由自建路网、A* 和 Yen Top-K 计算，高德只负责底图和交互。
- 智能助手采用受控 Tool Calling，A* 与业务数据库保持事实权威。
- 写操作只生成草稿，用户通过普通 REST 提交，管理员审核后才可能生效。
- AI 可以关闭，AI 失败不影响基础路线规划。
- 当前没有 RAG、室内导航、真实 GPS、Redis、OSS、本地大模型和微服务。
- GCJ-02 没有官方 EPSG 编号；项目用 SRID 0 保存几何，并在数据集中记录坐标语义，不能误称为 WGS84。

## 5. 总体完成标准

完成两份课程后，应当能够回答：

1. 一次浏览器请求如何经过 Vue、HTTP、Security、Controller、Service、SQL，再返回页面？
2. 节点、道路、设施和动态障碍怎样共同影响路线？
3. A* 和 Yen Top-K 分别解决什么问题？
4. 三条路线卡片如何与地图中的单条选中路线联动？
5. 模型 Tool JSON 由谁产生、谁校验、谁执行，结果如何通过 SSE 到达前端？
6. 用户障碍上报为什么不能立即成为路线事实？
7. AI、地图数据或部署出错时，如何判断故障位于哪一层？
