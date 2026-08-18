# DATABASE.md — Stage 1 数据库

## 基本信息

- 数据库：PostgreSQL 17 + PostGIS 3.5 镜像。
- Schema 管理：仅使用 Flyway；未启用 JPA 自动建表或 schema update。
- 当前版本：v2。
- 坐标相关表尚未创建；Stage 2 引入时必须明确记录 GCJ-02 等坐标系。

## 表

| 表 | 用途 | 关键约束 |
|---|---|---|
| `app_user` | 本地账号与角色 | 用户名唯一；角色仅 `USER`/`ADMIN`；密码为 BCrypt |
| `refresh_token` | 刷新令牌会话 | 只保存 SHA-256 摘要；摘要唯一；支持过期和撤销 |
| `audit_log` | 基础安全审计 | 记录 LOGIN、TOKEN_REFRESH、LOGOUT 等动作 |

## Flyway 历史

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_security.sql` | 创建安全相关表和演示账号 |
| V2 | `V2__fix_demo_credentials_and_refresh_token_index.sql` | 修复演示凭据摘要并增加刷新令牌唯一索引 |

V1 已在本地执行，因此没有回写历史迁移；修复通过新增 V2 完成。从空库启动时 Flyway 会依次执行 V1、V2，得到相同最终结构。

## 安全说明

- 数据库不保存 Refresh Token 明文。
- Access Token 不入库，默认有效期 15 分钟。
- Refresh Token 默认有效期 7 天，每次刷新撤销旧令牌并签发新令牌。
- 当前演示账号只用于本地验收，正式部署必须替换或停用。
