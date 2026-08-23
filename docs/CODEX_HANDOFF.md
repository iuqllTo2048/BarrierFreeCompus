# CODEX_HANDOFF.md — Codex 继续开发交接

> 更新日期：2026-08-23  
> 基线提交：`72b2ac7 feat: 升级校园底图与折线路网编辑`  
> 当前分支：`main`  
> 远程仓库：`git@github.com:iuqllTo2048/BarrierFreeCompus.git`

## 1. 新 Codex 接手顺序

1. 完整读取根目录 `AGENTS.md`。
2. 读取 `PROJECT_SPEC.md`、`docs/PROJECT_STATUS.md`、`docs/DESIGN_DIRECTION.md`、`docs/DESIGN_SYSTEM.md`。
3. 读取本文件，再检查 `git status --short` 和最近提交；不要重复已经完成的 V8 工作。
4. UI 改动使用 `.agents/skills/barrier-free-ui/SKILL.md` 与 `.agents/skills/ui-ux-pro-max/SKILL.md`。
5. Windows 上 Python 命令使用 `py`；不安装或切换系统级软件。
6. 先运行最小相关测试，最后运行全量测试、生产构建和隔离 E2E。

不得输出或提交 `.env`、高德 Key、AI Key、密码、证书或数据库备份。不得修改已经执行的 Flyway V1–V8；新迁移从 V9 开始。

## 2. 当前真实状态

- 技术版本仍为 `1.0.0`，Git Tag 为 `v1.0`。
- V8 已创建 Formal 数据集 `SCHOOL_EXAMPLE_V1`，GCJ-02 中心为 `104.695359,31.534827`。
- 新数据集初始为 0 建筑、0 节点、0 道路、0 设施、0 障碍。
- `YUNLU_DEMO_V1` 仅停用，历史数据未删除，管理员仍可查看。
- 管理端已支持道路折线绘制、PolylineEditor 拖动/增删拐点、撤销、重置、完成、取消和键盘替代操作。
- 道路距离由前端预览、后端根据完整 LineString 权威复算。
- 地图点选已有十字标记、坐标状态反馈、字段高亮与 `aria-live` 播报。
- 基线验证：67/67 JUnit、31/31 Vitest、6/6 隔离 Playwright、TypeScript、ESLint、Prettier、生产构建通过。
- 2026-08-23 最后一次检查时 Docker Desktop Linux Engine 已停止；继续集成测试前先由用户启动 Docker Desktop，不要反复重试同一连接错误。

## 3. 当前待实现目标

实现“Formal GeoJSON 安全同步”，让组员可以：

```text
编辑地图 → 导出 GeoJSON → 传递文件 → 目标环境预检 → 明确确认 → 合并导入
```

用户已经确认开始开发。该功能与地图编辑升级共同属于 post-v1 地图协作改进；完成后等待用户验收再提交，不自行推送。

## 4. 已确认的功能边界

### GeoJSON v2

- 根对象增加 `schemaVersion`、`datasetCode`、`coordinateSystem`、`exportedAt`。
- 导出可共享地图对象：建筑、入口、道路节点、折线路径、设施、管理员地图编辑器创建的障碍。
- 不导出用户账号、路线历史、评分、评论、AI 会话或普通用户的 `USER_REPORT` 障碍。
- 保留旧 Demo GeoJSON v1 导入兼容；Formal 只接受 v2。

### 只读预检

- 新增预检接口，上传文件后不写数据库。
- 校验 FeatureCollection、版本、数据集代码、GCJ-02、数量上限、几何类型、坐标、同类型 `externalId` 唯一性。
- 校验道路端点引用节点，入口和设施引用建筑。
- 返回按对象类型统计的新增、相同、内容不同和错误，并提供有限的编号样例。

### 安全应用

- 仅使用 MERGE：文件里缺失的本地对象绝不自动删除。
- 同编号且内容相同跳过；内容不同默认保留本地，管理员可明确选择覆盖。
- 预检返回 payload 指纹和目标数据指纹；正式应用时重新计算，目标已变化则返回 409，要求重新预检。
- 单事务应用，任意失败全部回滚。
- 导入前使用 V9 新表保存目标数据集当前 GeoJSON JSONB 备份、操作者、两个指纹和时间；返回 `backupId`。
- 写审计日志；道路距离继续忽略文件值并由服务端复算。
- 本 Stage 不做“删除缺失对象”和“一键恢复备份”，避免扩大破坏性范围。

### 管理端 UX

- 选择文件后先展示预览对话框，不立即导入。
- 展示目标数据集、文件、各类型新增/相同/差异/错误统计及警告。
- 冲突策略提供“保留本地（默认）/覆盖同编号对象”，覆盖需要明确确认。
- 明示“合并不会删除本地对象”。错误存在时禁用确认按钮。
- 成功后显示统计和 `backupId`，刷新地图数据。
- 使用现有 Vue 3 + Element Plus 和语义 Token；兼容深色、375px、键盘与焦点，不新增 UI 框架。

## 5. 关键代码入口

| 领域 | 文件 |
|---|---|
| 管理地图接口 | `backend/src/main/java/cn/barrierfreecampus/mapdata/AdminMapController.java` |
| GeoJSON 导入导出 | `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDataService.java` |
| 地图 DTO | `backend/src/main/java/cn/barrierfreecampus/mapdata/MapDtos.java` |
| 地图集成测试 | `backend/src/test/java/cn/barrierfreecampus/mapdata/MapDataIntegrationTest.java` |
| 数据模型 | `backend/src/main/resources/db/migration/V3__map_data_model.sql` |
| V8 新校园 | `backend/src/main/resources/db/migration/V8__add_blank_school_example_dataset.sql` |
| 前端管理地图 | `frontend/src/views/AdminDashboardView.vue` |
| 前端地图 API | `frontend/src/services/map-api.ts` |
| 前端地图类型 | `frontend/src/types/map.ts` |
| E2E | `frontend/e2e/release-candidate.spec.ts`、`scripts/run-e2e.ps1` |

现有 `MapDataService.importGeoJson` 在 `dataset.demo()==false` 时直接拒绝；现有导出只包含 NODE、EDGE、FACILITY。不要简单删除这条保护后直接允许 Formal，必须先完成预检、并发指纹、明确冲突策略、事务备份和审计。

## 6. 推荐接口形状

```text
POST /api/admin/map/datasets/{datasetId}/geojson/preview
POST /api/admin/map/datasets/{datasetId}/geojson/apply
```

`preview` 接收 GeoJSON v2，返回 payload/target fingerprint、统计、错误和冲突样例。`apply` 接收同一 GeoJSON、两个预检指纹和 `KEEP_TARGET|OVERWRITE`。不要依赖前端传入统计值。

## 7. 验证清单

后端至少覆盖：

- Formal 预检完全只读；
- datasetCode/GCJ-02/版本/几何/重复编号/引用错误；
- 新增、相同、内容不同统计；
- KEEP_TARGET 与 OVERWRITE；
- 文件缺失对象不删除；
- 目标指纹过期返回 409；
- 任意失败整批回滚；
- 备份和审计存在；
- 多拐点道路形状保存且距离由服务端复算；
- Demo v1 兼容。

前端至少覆盖：预览状态、错误禁用、冲突选择、成功刷新、文件解析错误。隔离 Playwright 必须通过 `scripts/run-e2e.ps1` 运行，不能对正式 8080 数据集执行会写数据的测试。

常用命令：

```powershell
cd D:\BarrierFreeCampus\backend
mvn -q test

cd D:\BarrierFreeCampus\frontend
npm test
npm run lint
npm run typecheck
npm run format:check
npm run build

cd D:\BarrierFreeCampus
powershell -ExecutionPolicy Bypass -File scripts\run-e2e.ps1
powershell -ExecutionPolicy Bypass -File scripts\security-scan.ps1
```

## 8. 完成和 Git 规则

- 更新 `docs/PROJECT_STATUS.md`、`docs/DATABASE.md`、`docs/USER_GUIDE.md`、`docs/USER_ADMIN_MANUAL.md`、`docs/TEST_REPORT.md` 和本交接文档。
- 不修改或删除 V8，不清空 Formal 数据，不在正式数据库执行测试导入。
- 完成后按 `AGENTS.md` 的 10 项格式汇报，等待用户人工验收。
- 只有用户验收后才创建中文提交；用户没有要求时不要 push。
