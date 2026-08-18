# API.md — Stage 1 接口

统一响应：

```json
{
  "code": 0,
  "message": "成功",
  "data": {}
}
```

错误同时使用正确 HTTP 状态码，`code` 与 HTTP 状态一致；响应不暴露堆栈或密钥。

## 认证

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | 用户名密码登录，响应 Access Token 并设置 HttpOnly Refresh Cookie |
| POST | `/api/auth/refresh` | Refresh Cookie | 轮换 Refresh Token 并返回新 Access Token |
| POST | `/api/auth/logout` | 公开 | 服务端撤销有效 Refresh Token 并清除 Cookie |
| GET | `/api/auth/me` | Bearer Token | 获取当前用户名和角色 |

登录请求：

```json
{
  "username": "demo_user",
  "password": "Demo@12345"
}
```

Access Token 通过 `Authorization: Bearer <token>` 发送。前端仅保存在运行内存，不写入 localStorage/sessionStorage。

## 权限验收接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/user/home` | USER 或 ADMIN | 用户端认证探针 |
| GET | `/api/admin/dashboard` | ADMIN | 管理端认证探针 |

这两个接口只用于 Stage 1 权限骨架，不代表地图或治理业务已实现。

## 运维与文档

- 健康检查：`GET /actuator/health`
- Swagger：`/swagger-ui.html`
- OpenAPI JSON：`/v3/api-docs`
