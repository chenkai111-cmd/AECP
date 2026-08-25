# F02 本地账号登录与会话失效设计

## 1. 目标与边界

F02 为本地演示账号提供两个后端接口：

- `POST /api/v1/auth/login` 接收 `{username, password}`，校验成功后返回 HTTP 200、`status: 200` 和非空访问 token。
- `POST /api/v1/auth/logout` 接收 `Authorization: Bearer <token>`，删除对应会话，使该 token 立即失效。

本切片只实现后端认证闭环，不修改 `xiaou-frontend` 的 F01 `localStorage` demo-session，不新增受保护业务接口，不实现用户/组织数据库、RBAC、失败锁定、刷新令牌、2FA 或 SSO。完整身份域仍属于后续 Task 05。

## 2. 已确认的方案

采用轻量认证服务 + Redis opaque session：

1. 演示用户的用户名和明文密码来自 Spring 配置；密码只在进程内用于 BCrypt 匹配，不写日志、不写 Git。
2. 登录成功后使用 `SecureRandom` 生成 32 字节 URL-safe token。
3. Redis 保存 `aecp:auth:session:<token> -> <username>`，并设置可配置 TTL；不把密码或完整用户对象写入 Redis。
4. 退出按 token 删除 Redis key。重复退出视为幂等成功，便于客户端清理本地状态。
5. 通过独立 `AuthApiResponse<T>` 暴露 `status/data` 契约，不修改公共 `R.code` 字段，避免影响已有 F00/F01 基线。

### 2.1 方案取舍

- Spring Security + JWT 的标准化程度较高，但 JWT 需要黑名单或刷新状态才能实现 F02 要求的立即失效。
- Sa-Token 与现有版本属性相关，但当前工程没有真正接入依赖和运行配置；本次引入会把最小接口绑定到框架默认行为。
- Redis opaque session 能直接表达“登录创建、退出删除”的状态机，改动集中且方便后续把演示用户替换为数据库身份域，因此作为 F02 实现。

## 3. 代码边界与组件

```text
AuthController
    -> AuthService
        -> BCryptPasswordEncoder
        -> AuthSessionRepository
              -> RedissonClient -> Redis
```

- `AuthProperties`：绑定 `aecp.auth` 下的用户名、演示密码、session TTL 和 Redis key 前缀。
- `AuthService`：编排参数校验、凭据匹配、token 生成、会话保存和会话删除；不依赖 Web 层类型。
- `AuthSessionRepository`：只暴露 `save(token, username, ttl)`、`exists(token)`、`delete(token)` 三个会话操作；使用 Redisson bucket。
- `AuthController`：完成 HTTP 请求/响应映射、Bearer token 提取和异常到响应状态的转换。
- `AuthApiResponse`：字段为 `status`、`message`、`data`；登录数据包含 `token`、`expires_in_seconds`、`username`，不包含密码或 BCrypt hash。

本次修改集中在 `xiaou-starter`；只增加 BCrypt 依赖和认证代码/配置/测试，不创建新的业务模块。

## 4. 配置契约

开发环境使用以下配置语义：

```yaml
aecp:
  auth:
    demo-username: ${AECP_AUTH_DEMO_USERNAME:demo-pilot-pm}
    demo-password: ${AECP_AUTH_DEMO_PASSWORD:${AECP_TEST_PASSWORD:demo-password}}
    session-ttl-seconds: ${AECP_AUTH_SESSION_TTL_SECONDS:3600}
    session-key-prefix: ${AECP_AUTH_SESSION_KEY_PREFIX:aecp:auth:session:}
```

生产环境不提供默认密码，必须显式设置 `AECP_AUTH_DEMO_PASSWORD`。`AECP_TEST_PASSWORD` 仅为 `features.md` 本地验证命令提供兼容入口；正式部署不应依赖该变量名。

## 5. HTTP 契约

### 5.1 登录成功

请求：

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"demo-pilot-pm","password":"<injected password>"}
```

响应：

```json
{
  "status": 200,
  "message": "登录成功",
  "data": {
    "token": "<opaque token>",
    "expires_in_seconds": 3600,
    "username": "demo-pilot-pm"
  }
}
```

### 5.2 登录失败

缺少用户名/密码或 JSON 无法校验时返回 HTTP 400；用户名不存在或密码错误时返回 HTTP 401，并使用统一消息 `用户名或密码错误`，避免枚举账号。

### 5.3 退出

请求头为 `Authorization: Bearer <token>` 时删除 session，返回 HTTP 200：

```json
{
  "status": 200,
  "message": "退出成功",
  "data": {"invalidated": true}
}
```

缺少、格式错误或已经不存在的 token 也返回上述幂等成功响应，但 `invalidated` 为 `false`；服务端不保留退出黑名单，因为 token 是 Redis 有状态会话，删除即失效。

## 6. 失败处理与安全约束

- token 使用 `SecureRandom` 生成，禁止使用用户名、时间戳或可预测 UUID 拼接作为 token。
- Redis key 只包含 token，不把密码、密码 hash 或完整请求体写入日志。
- Redis 不可用时登录返回 HTTP 503 风格的 `status`/消息，不能返回一个无法撤销的假 token。
- Redis session TTL 必须为正数；配置绑定失败时应用启动失败，而不是静默使用无限期会话。
- Controller 不暴露 session 查询接口；“退出后失效”通过 repository 单元测试、Web 层流程测试和真实 Redis 端到端验证证明。
- 本切片不声称满足 Task 05 的失败 5 次锁定 30 分钟、禁用账号、可刷新会话和登录审计要求，这些不在 F02 验收范围内。

## 7. 测试与验收

1. AuthService 单元测试使用 fake repository 和 BCrypt encoder，覆盖正确凭据、错误凭据、token/TTL、退出删除、重复退出。
2. AuthController MockMvc 测试覆盖登录 JSON 契约、参数错误、错误凭据、Bearer 退出和重复退出。
3. Redis 集成验证使用 Compose 提供的 Redis，确认登录写入带 TTL 的 session，退出后 key 不存在。
4. 端到端验证设置 `AECP_TEST_PASSWORD`，启动应用并执行 `docs/features.md` 的登录 curl；解析 token 后调用 logout，再次 logout 验证幂等且原 session 已删除。
5. F02 完成后重新运行 `mvnw.cmd clean test`、`mvnw.cmd -pl xiaou-starter -am package -DskipTests`，并运行前端现有 `pnpm test:routes`、`pnpm typecheck` 和 `pnpm build` 作为未修改模块回归。

## 8. 后续演进

Task 05 可以保留 `AuthController` 和 `AuthService` 的边界，将演示用户解析替换为数据库 identity repository，增加 bcrypt hash 持久化、账号状态、失败锁定、refresh session、登录审计和可配置 2FA；F02 的 opaque session 机制不阻塞这些演进。
