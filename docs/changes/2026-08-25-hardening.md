# 提交说明：build: 前端本地 Node 对齐 22 并新增数据库备份脚本

## 提交信息
`build: 前端本地 Node 对齐 22 并新增数据库备份脚本`

## 背景
工程加固：本地 Node 26 与 Docker 构建固定 Node 22 不一致，可能出现"本地能过、容器报错"；数据库缺少完整逻辑备份（GeoJSON 只含地图对象，不含用户/上报/历史）。

## 改动内容
- 新增 `frontend/.nvmrc`（内容 22），声明前端 Node 版本。
- 便携版 Node 22.23.2 位于 `D:\node22\node-v22.23.2-win-x64`（不入库）。
- 新增 `scripts/backup-db.ps1`：pg_dump 自定义格式备份到项目 `backups/`（已加入 .gitignore），默认保留最近 14 份；脚本显式使用项目根目录作为工作目录，兼容 Windows 计划任务触发。
- `.gitignore` 增加 `backups/`。
- `启动说明.md`、`docs/DEPLOYMENT.md` 补充 Node 22 用法、备份/恢复命令、Windows 计划任务说明。

## 解决什么问题 / 新效果
本地与 Docker 构建环境一致；数据库可一键完整备份并在误操作后恢复；已配置计划任务 `BarrierFreeCampus-DB-Backup`（每天 03:00，本机级配置，不入库）。

## 验证
- Node 22.23.2 下：npm ci、typecheck、lint、31 Vitest、format、build 全部通过。
- 备份脚本实测生成有效 dump（约 114KB），`pg_restore --list` 校验通过；计划任务手动触发成功。

## 注意
- 计划任务是本机 Windows 配置，不在 Git 中；新机器需自行创建（命令见启动说明.md）。
- 未改任何技术栈：pom.xml、package.json（内容）、docker-compose.yml 均未变更。
