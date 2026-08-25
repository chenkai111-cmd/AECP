# F03 组织成员与角色管理设计

## 1. 目标与范围

F03 为组织管理员提供组织成员管理后端闭环：

- 新增组织成员；
- 查询当前有效成员列表；
- 变更成员角色；
- 移除组织成员；
- 阻止跨组织管理和最后一名组织管理员被移除或降级。

本切片实现 `docs/features.md` 中 F03 的后端接口、MySQL 持久化、组织级权限校验、固定演示数据和分层测试。前端成员管理页面、项目级权限、邀请流程、用户注册、批量导入、成员变更审计表和历史列表不在 F03 范围内。

F03 不修改 `docs/features.md` 的状态；只有仓库既有验证机制完成端到端验证后，才允许由验证流程更新状态。

## 2. 已确认方案与取舍

采用独立 identity 业务模块、Spring JDBC、Flyway 和 F02 Redis 会话解析：

1. 新增 `xiaou-modules/xiaou-aecp-identity`，承载组织、用户账号、组织成员领域模型、成员角色、业务服务、仓储接口和 JDBC 实现。
2. `xiaou-starter` 依赖 identity 模块，在 Web 层提供组织成员 REST 接口，并把 Bearer token 解析为当前登录用户名。
3. identity 模块不依赖 F02 认证实现。Controller 完成认证适配后，只向领域服务传递操作者用户名、组织 ID 和命令参数。
4. 使用 Spring JDBC 编写显式 SQL，不为当前三个小型聚合引入额外 ORM 映射体系。
5. 使用 Flyway 管理表结构和固定演示数据，应用启动时自动校验并执行迁移。

未采用以下方案：

- 继续把组织成员代码放入 `xiaou-starter`：初始文件较少，但会把 Web、认证和身份域持久化混合在启动模块，不利于 F04/F05 复用权限边界。
- 直接启用模板中的通用用户/权限模块：现有模板模型和 AECP 的组织级角色语义没有形成可验证映射，本切片接入会扩大范围。
- 在 Redis 中维护组织成员：无法满足持久关系、约束和后续项目权限查询，MySQL 更适合作为事实来源。

## 3. 组件边界

```text
OrganizationMemberController (xiaou-starter)
    -> BearerTokenResolver
    -> AuthSessionRepository.findUsername(token) -> Redis
    -> OrganizationMemberService (xiaou-aecp-identity)
        -> OrganizationMemberRepository
            -> Spring JDBC -> MySQL
```

### 3.1 `xiaou-aecp-identity`

- `OrganizationMemberService`：授权校验、业务规则、事务边界和结果编排。
- `OrganizationMemberRepository`：定义组织、账号、成员查询和成员写操作。
- JDBC repository：执行显式 SQL 和必要的行锁，不依赖 Web DTO。
- `OrganizationRole`：限定为 `ORGANIZATION_ADMIN`、`PROJECT_MANAGER`、`ENGINEER`、`AUDITOR`。
- 领域异常：表达无权限、资源不存在、成员冲突和最后管理员保护，不携带 SQL 或基础设施细节。

### 3.2 `xiaou-starter`

- `OrganizationMemberController`：请求校验、Bearer token 解析、HTTP 状态映射和响应 DTO。
- 扩展 F02 的 `AuthSessionRepository`，增加 token 到用户名的查询能力；登录和退出行为保持不变。
- 将 identity 包加入应用组件扫描，使业务服务和 JDBC repository 被 Spring 容器管理。
- 增加 Flyway 运行依赖，使 identity 模块资源中的迁移在启动时执行。

### 3.3 `xiaou-common-web`

新增通用 `ApiResponse<T>`，JSON 字段固定为 `status`、`message`、`data`。F02 从私有 `AuthApiResponse` 切换到该类型，但已有认证接口的状态码和 JSON 字段不变。F03 不改动模板已有 `R` 类型，避免影响未纳入本切片的接口。

## 4. 请求与授权流程

所有 F03 接口遵循同一流程：

1. Controller 校验 `Authorization: Bearer <token>`。
2. 通过 Redis session 查询当前用户名。
3. 将当前用户名、路径中的组织 ID 和请求命令传给 `OrganizationMemberService`。
4. 服务查询当前用户账号及其在目标组织中的有效成员关系。
5. 只有角色为 `ORGANIZATION_ADMIN` 的有效成员可以查询或修改该组织成员。
6. 服务在 MySQL 中执行查询或写事务，返回领域结果。
7. Controller 转换为统一响应，不返回密码、token、Redis key 或数据库细节。

认证和授权边界如下：

- 缺少 Bearer token、格式错误、session 不存在或过期：HTTP 401。
- session 存在，但对应账号不存在或已禁用：HTTP 401。
- 登录用户不是目标组织的有效管理员，包括 A 组织管理员访问 B 组织：HTTP 403。
- Redis 连接失败：HTTP 503，而不是把基础设施故障伪装成 token 失效。

## 5. 数据模型与迁移

Flyway 增加首个 identity 迁移 `V1__create_identity_and_organization_members.sql`。迁移使用 MySQL 8.4 与 H2 MySQL 模式均可执行的 SQL 子集。

### 5.1 `aecp_organization`

| 字段 | 约束 | 说明 |
|---|---|---|
| `id` | VARCHAR，主键 | 固定业务 ID，例如 `ORG-DEMO-COMAC` |
| `display_name` | 非空 | 脱敏显示名称 |
| `organization_type` | 非空 | 组织类型 |
| `active` | 非空 | 是否有效 |
| `created_at` | 非空 | 创建时间 |
| `updated_at` | 非空 | 更新时间 |

### 5.2 `aecp_user_account`

| 字段 | 约束 | 说明 |
|---|---|---|
| `id` | VARCHAR，主键 | 固定用户 ID |
| `username` | 非空、唯一 | 与 F02 session 中用户名关联 |
| `display_name` | 非空 | 脱敏显示名称 |
| `enabled` | 非空 | 是否允许作为有效身份使用 |
| `created_at` | 非空 | 创建时间 |
| `updated_at` | 非空 | 更新时间 |

该表只保存 F03 需要的身份索引，不保存密码或 BCrypt hash。F02 的演示密码仍来自环境配置。

### 5.3 `aecp_organization_member`

| 字段 | 约束 | 说明 |
|---|---|---|
| `organization_id` | 联合主键、外键 | 所属组织 |
| `user_id` | 联合主键、外键 | 成员账号 |
| `role` | 非空 | 当前组织角色 |
| `active` | 非空 | 是否为当前有效成员 |
| `joined_at` | 非空 | 最近一次加入时间 |
| `updated_at` | 非空 | 最近变更时间 |
| `removed_at` | 可空 | 软删除时间 |

联合主键保证同一用户在同一组织只有一条当前关系记录。移除成员只把 `active` 设为 false 并填写 `removed_at`，不物理删除。

### 5.4 固定演示数据

迁移创建两个组织：

| 组织 ID | 显示名称 | `organization_type` |
|---|---|---|
| `ORG-DEMO-COMAC` | 商飞演示组织 A | `AIRFRAME_SIDE` |
| `ORG-DEMO-AECC` | 商发演示组织 B | `ENGINE_SIDE` |

迁移创建六个账号：

| 用户 ID | 用户名 | 显示名称 |
|---|---|---|
| `USR-DEMO-PM` | `demo-pilot-pm` | 演示项目负责人 |
| `USR-DEMO-ADMIN-A` | `demo-admin-a` | 演示管理员 A |
| `USR-DEMO-ADMIN-B` | `demo-admin-b` | 演示管理员 B |
| `USR-DEMO-ENG-A` | `demo-engineer-a` | 演示工程师 A |
| `USR-DEMO-ENG-B` | `demo-engineer-b` | 演示工程师 B |
| `USR-DEMO-AUDITOR` | `demo-auditor` | 演示审计员 |

初始成员关系只有两条：ADMIN-A 是 COMAC 的 `ORGANIZATION_ADMIN`，ADMIN-B 是 AECC 的 `ORGANIZATION_ADMIN`。

`USR-DEMO-ENG-A` 只预置账号，不预置有效组织成员关系，确保 `docs/features.md` 中首次向 COMAC 添加该用户能返回 201。`docs/DEMO_DATA.md` 中该用户的“所属组织”表示预期演示归属，F03 的新增接口负责建立实际成员关系。

## 6. REST API 契约

所有字段使用 snake_case。数据库时间统一按 UTC 保存，接口时间使用 ISO 8601 UTC 字符串，例如 `2026-08-25T02:00:00Z`。

### 6.1 新增成员

```http
POST /api/v1/organizations/{organizationId}/members
Authorization: Bearer <token>
Content-Type: application/json

{"user_id":"USR-DEMO-ENG-A","role":"ENGINEER"}
```

成功时返回 HTTP 201：

```json
{
  "status": 201,
  "message": "成员添加成功",
  "data": {
    "user_id": "USR-DEMO-ENG-A",
    "username": "demo-engineer-a",
    "display_name": "演示工程师 A",
    "role": "ENGINEER",
    "joined_at": "2026-08-25T02:00:00Z"
  }
}
```

已被软删除的成员可以重新添加。重新添加会设置新角色、恢复 `active`、清空 `removed_at`，并把 `joined_at` 更新为本次重新加入时间。已处于有效状态的成员重复添加返回 HTTP 409。

### 6.2 查询当前成员

```http
GET /api/v1/organizations/{organizationId}/members
Authorization: Bearer <token>
```

成功时返回 HTTP 200：

```json
{
  "status": 200,
  "message": "查询成功",
  "data": {
    "items": [
      {
        "user_id": "USR-DEMO-ADMIN-A",
        "username": "demo-admin-a",
        "display_name": "演示管理员 A",
        "role": "ORGANIZATION_ADMIN",
        "joined_at": "2026-08-25T00:00:00Z"
      }
    ],
    "total": 1
  }
}
```

列表只返回有效成员，按 `joined_at` 升序、`user_id` 升序稳定排序。F03 不实现分页；`total` 等于 `items` 数量。

### 6.3 变更角色

```http
PATCH /api/v1/organizations/{organizationId}/members/{userId}
Authorization: Bearer <token>
Content-Type: application/json

{"role":"AUDITOR"}
```

成功时返回 HTTP 200 和更新后的成员。请求角色与当前角色相同时按幂等成功处理，返回未变化的成员。把最后一名有效组织管理员改为其他角色返回 HTTP 409。

### 6.4 移除成员

```http
DELETE /api/v1/organizations/{organizationId}/members/{userId}
Authorization: Bearer <token>
```

成功时返回 HTTP 200：

```json
{
  "status": 200,
  "message": "成员移除成功",
  "data": {"removed": true}
}
```

删除不存在或已失效的成员返回 HTTP 404。移除最后一名有效组织管理员返回 HTTP 409。

## 7. 业务规则

- 只有目标组织的有效 `ORGANIZATION_ADMIN` 可以调用四个接口。
- 组织不存在或已失效时返回 HTTP 404。
- 请求中的目标用户不存在或已禁用时返回 HTTP 404，避免创建无效成员关系。
- 查询、更新或删除不存在的成员时返回 HTTP 404。
- 角色必须是四个固定枚举值之一；空值或未知值返回 HTTP 400。
- 有效成员重复添加返回 HTTP 409；失效成员重新添加返回 HTTP 201。
- 组织必须始终至少保留一名有效 `ORGANIZATION_ADMIN`。
- F03 角色是组织级角色；不推导项目权限，也不修改 `docs/DEMO_DATA.md` 中项目角色定义。

## 8. 事务与并发控制

新增、重新激活、角色变更和软删除均在 `@Transactional` 方法中执行。每个写事务按以下固定顺序处理：

1. 对目标组织记录执行 `SELECT ... FOR UPDATE`，同一组织的成员写操作按组织串行化；
2. 在事务内重新查询操作者账号及有效管理员关系；
3. 查询目标账号和成员关系；
4. 校验重复成员或最后管理员约束；
5. 执行插入或更新；
6. 查询并返回事务内最新成员视图。

锁定组织行使“检查最后管理员”和“执行变更”处于同一串行化边界，防止两个管理员被并发降级或移除。不同组织使用不同组织行锁，互不阻塞。

新增关系的并发请求仍受联合主键保护；数据库唯一约束冲突统一转换为 HTTP 409。锁等待、连接失败等非业务异常不向客户端暴露 SQL。

## 9. 错误映射

| HTTP / `status` | 场景 |
|---|---|
| 400 | JSON 或字段校验失败、角色值无效 |
| 401 | token 缺失、格式错误、过期、不存在，或 session 对应账号失效 |
| 403 | 当前用户不是目标组织管理员或尝试跨组织访问 |
| 404 | 组织、目标账号或有效成员不存在 |
| 409 | 重复有效成员、最后管理员保护、并发唯一约束冲突 |
| 503 | Redis 或数据库连接等依赖暂时不可用 |
| 500 | 未分类内部错误，响应只返回通用消息 |

所有失败响应继续使用 `{status, message, data}`，失败时 `data` 为 null。消息不得包含 SQL、表名、Redis key、密码、token、堆栈或内部连接地址。

## 10. 测试与验收

验证严格按单元测试、集成测试、端到端测试顺序执行；前一层失败时不进入下一层。

### 10.1 单元测试

`OrganizationMemberService` 使用 fake repository 覆盖：

- 目标组织管理员可以新增成员；
- 非管理员和跨组织管理员被拒绝；
- 有效成员重复添加冲突；
- 失效成员重新激活并更新加入时间；
- 角色变更及相同角色幂等返回；
- 软删除；
- 最后一名管理员不能被移除或降级；
- 存在另一名有效管理员时允许移除或降级。

### 10.2 集成测试

- 使用 H2 MySQL 模式执行 Flyway 迁移，验证表、约束和固定种子数据。
- JDBC repository 测试覆盖成员插入、重新激活、稳定列表排序、角色更新、软删除和管理员计数。
- MockMvc 测试覆盖四个接口及 201、200、400、401、403、404、409、503 响应映射。
- F02 登录和退出测试必须继续通过，证明公共 `ApiResponse` 与 session 查询扩展没有破坏认证契约。

### 10.3 真实端到端测试

使用 Docker Compose 的 MySQL 8.4、Redis 7.4 和真实 Spring Boot 应用：

1. 设置 `AECP_AUTH_DEMO_USERNAME=demo-admin-a` 和临时测试密码后启动应用；
2. 调用 F02 登录接口获取 `AECP_TEST_TOKEN`；
3. 通过 API 确保 `USR-DEMO-ENG-A` 当前不在 COMAC 有效成员列表中；若上次中断留下有效关系，先调用 DELETE 清理，不直接修改数据库；
4. 执行 `docs/features.md` 的 F03 POST 命令并断言 HTTP/响应 `status` 为 201；
5. GET 列表并断言新增成员可见且 `total` 正确；
6. PATCH 为 `AUDITOR` 并断言角色更新；
7. DELETE 后再次 GET，断言该成员不可见；
8. 验证无效 token 返回 401、非管理员返回 403、ADMIN-A 访问 AECC 返回 403；
9. 清理本次创建的会话和成员状态，不删除开发者已有数据库卷。

MySQL 端到端验证负责证明真实方言、事务、外键和锁定行为；H2 只作为快速集成反馈，不能替代该验证。

### 10.4 最终回归

- `mvnw.cmd clean test`；
- `mvnw.cmd -pl xiaou-starter -am package -DskipTests`；
- 现有前端路由测试、类型检查和构建；
- 标准启动路径和真实 F03 curl 流程；
- 日志和变更扫描，确认没有密码、token、调试代码或未请求的业务接口。

功能清单状态由仓库约定的验证流程更新，不在实现代码中手工编辑。

## 11. 可观测性与故障处理

- 成员写操作记录结构化信息：操作类型、组织 ID、目标用户 ID、操作者用户名、结果和业务错误类型。
- 禁止记录 Authorization 请求头、完整 token、密码、数据源凭据或 Redis key。
- Flyway 迁移失败或校验和不一致时阻止应用启动，不在部分迁移状态提供接口。
- Redis 故障返回 503；数据库连接故障返回通用 503；未分类异常返回通用 500，并在服务端保留不含秘密的关联日志。
- F03 不新增持久审计表。成员变更审计及历史查询由后续审计功能单独设计，当前日志不能被表述为合规审计记录。

## 12. 发布与回滚

发布顺序为：执行自动化测试和打包、启动依赖、由 Flyway 应用迁移、确认应用健康、执行 F03 真实 smoke 流程。出现以下任一情况时停止发布并回滚应用版本：

- Flyway 迁移失败或校验和异常；
- 应用无法完成启动健康检查；
- 登录或 F03 添加、查询、变更、删除 smoke 流程出现非预期状态；
- F02 回归失败。

迁移只向前执行，不提供会删除表或数据的 down migration。回滚应用版本时保留 F03 新表和成员数据；旧版本不读取这些表，因此允许代码回滚而不破坏数据。修复后通过新的 Flyway 版本迁移继续演进，不修改已经执行过的 `V1` 文件。

## 13. 后续演进

- F04/F05 可以复用账号、组织成员和组织级角色查询，但项目成员和项目权限必须使用独立关系表。
- 后续身份功能可把 F02 的单账号配置替换为数据库密码 hash、账号锁定、刷新会话和登录审计；F03 不提前实现这些能力。
- 后续审计功能可以记录成员新增、角色变更和移除的不可变事件，并提供历史查询；F03 只维护当前成员关系。
