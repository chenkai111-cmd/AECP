# F03 Organization Membership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现组织管理员新增、查询、变更角色和软删除组织成员的 F03 后端闭环，并通过 MySQL、Redis 和真实 HTTP 流程验收。

**Architecture:** 新增 `xiaou-aecp-identity` 业务模块，以 Flyway 管理组织、账号和成员表，以 Spring JDBC 持久化，以领域服务统一执行组织管理员授权、事务和最后管理员约束。`xiaou-starter` 只负责从 F02 Redis session 解析操作者、映射 REST DTO 和 HTTP 错误；公共 `ApiResponse<T>` 保持 F02/F03 的 `{status,message,data}` 契约一致。

**Tech Stack:** Java 17、Spring Boot 3.4.4、Spring MVC、Spring JDBC、Spring Transaction、Flyway、MySQL 8.4、H2 MySQL mode、Redisson 3.45.1、JUnit 5、AssertJ、Mockito、MockMvc、Maven Wrapper 3.9.9、Docker Compose。

**Spec:** `docs/superpowers/specs/2026-08-25-f03-organization-members-design.md`

## Global Constraints

- 只实现 F03 后端；不修改 `xiaou-frontend` 业务代码，不实现项目权限、邀请、注册、批量导入或持久审计历史。
- 继续使用 Java 17、Spring Boot 3.4.4、Maven Wrapper 3.9.9、MySQL 8.4、Redis 7.4，以及现有 `com.xiaou` 坐标和 `xiaou-*` 模块名。
- 所有接口使用 `{status,message,data}`；请求和响应业务字段使用 snake_case，时间以 UTC 保存并输出 ISO 8601 `Z` 格式。
- F03 的组织角色只允许 `ORGANIZATION_ADMIN`、`PROJECT_MANAGER`、`ENGINEER`、`AUDITOR`。
- 所有 F03 接口要求有效 Bearer session；缺失或失效 session 返回 401，非目标组织管理员返回 403。
- 成员删除为软删除；有效成员重复添加返回 409，失效成员重新添加返回 201。
- 每个组织始终至少保留一名有效 `ORGANIZATION_ADMIN`；写操作按组织行锁串行化。
- 不记录密码、完整 token、Authorization 头、Redis key、数据库凭据或 SQL 异常明细。
- 不手工修改 `docs/features.md` 状态；验证完成后只记录实际命令证据。
- Flyway V1 只向前执行且发布后不得改写；应用回滚保留 F03 表和成员数据，不执行破坏性 down migration。
- 严格按单元测试、集成测试、真实 E2E 顺序验证；上一层失败时停止。

---

## File Map

### 公共响应与 F02 兼容

- `xiaou-common/xiaou-common-web/src/main/java/com/xiaou/web/response/ApiResponse.java`：F02/F03 共用响应 envelope。
- `xiaou-starter/src/main/java/com/xiaou/web/auth/AuthSessionRepository.java`：增加 token 到用户名的查询接口。
- `xiaou-starter/src/main/java/com/xiaou/web/auth/RedisAuthSessionRepository.java`：从 Redis bucket 读取用户名。
- `xiaou-starter/src/main/java/com/xiaou/web/auth/AuthController.java`：切换为公共响应类型。
- `xiaou-starter/src/main/java/com/xiaou/web/auth/AuthApiResponse.java`：公共响应接管后删除。

### Identity 模块

- `xiaou-modules/xiaou-aecp-identity/pom.xml`：Spring JDBC、测试和迁移测试依赖。
- `xiaou-modules/xiaou-aecp-identity/src/main/resources/db/migration/V1__create_identity_and_organization_members.sql`：三张表与固定演示种子。
- `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationRole.java`：四个组织角色。
- `UserAccount.java`、`OrganizationMembership.java`、`OrganizationMember.java`：领域值对象和查询视图。
- `OrganizationMemberError.java`：业务失败原因的单一词汇表。
- `OrganizationMemberRepository.java`：领域服务需要的持久化端口。
- `OrganizationMemberService.java`：授权、事务、时钟、成员规则和安全日志。
- `JdbcOrganizationMemberRepository.java`：Spring JDBC SQL 与行映射。
- `IdentityConfiguration.java`：提供 `Clock.systemUTC()`。

### Starter HTTP 适配

- `xiaou-starter/src/main/java/com/xiaou/web/auth/BearerSessionAuthenticator.java`：Bearer token 到 session 用户名。
- `xiaou-starter/src/main/java/com/xiaou/web/auth/InvalidSessionException.java`：401 认证失败信号。
- `xiaou-starter/src/main/java/com/xiaou/web/organization/*.java`：请求 DTO、响应 DTO、Controller 和专用异常处理。
- `xiaou-starter/src/main/java/com/xiaou/web/Application.java`：扫描 identity 包。
- `xiaou-starter/src/main/resources/application.yml`：显式启用 Flyway 默认迁移目录。

### 测试与项目记录

- identity 模块测试：迁移、领域服务和 JDBC repository。
- starter 测试：F02 回归、Bearer session 解析和 F03 MockMvc 契约。
- `PROGRESS.md`、`DECISIONS.md`、`docs/STARTUP_CHECKLIST.md`：只在全部验证成功后记录事实。

---

### Task 1: 建立公共响应与可查询的 F02 Session

**Files:**

- Create: `xiaou-common/xiaou-common-web/src/main/java/com/xiaou/web/response/ApiResponse.java`
- Modify: `xiaou-starter/src/main/java/com/xiaou/web/auth/AuthSessionRepository.java`
- Modify: `xiaou-starter/src/main/java/com/xiaou/web/auth/RedisAuthSessionRepository.java`
- Modify: `xiaou-starter/src/main/java/com/xiaou/web/auth/AuthController.java`
- Delete: `xiaou-starter/src/main/java/com/xiaou/web/auth/AuthApiResponse.java`
- Modify: `xiaou-starter/src/test/java/com/xiaou/web/auth/AuthServiceTest.java`
- Modify: `xiaou-starter/src/test/java/com/xiaou/web/auth/RedisAuthSessionRepositoryTest.java`
- Modify: `xiaou-starter/src/test/java/com/xiaou/web/auth/AuthControllerTest.java`

**Interfaces:**

- Consumes: 现有 Redis bucket 值 `token -> username` 和 F02 登录/退出契约。
- Produces: `ApiResponse.success(int status, String message, T data)`、`ApiResponse.success(String message, T data)`、`ApiResponse.failure(int status, String message)`；`AuthSessionRepository.findUsername(String token): Optional<String>`。

- [ ] **Step 1: 先修改测试，表达公共响应与 session 查询行为**

在 `RedisAuthSessionRepositoryTest` 增加：

```java
@Test
void findUsernameReturnsStoredSessionValue() {
    when(redissonClient.<String>getBucket("test:auth:session:opaque-token")).thenReturn(bucket);
    when(bucket.get()).thenReturn("demo-admin-a");

    assertThat(repository.findUsername("opaque-token"))
            .contains("demo-admin-a");
}

@Test
void findUsernameReturnsEmptyForMissingSession() {
    when(redissonClient.<String>getBucket("test:auth:session:missing-token")).thenReturn(bucket);
    when(bucket.get()).thenReturn(null);

    assertThat(repository.findUsername("missing-token")).isEmpty();
}
```

在 `AuthServiceTest.InMemorySessionRepository` 增加同签名实现。`AuthControllerTest` 不直接引用响应 Java 类型，保留其现有 JSON 断言作为 F02 契约回归。

- [ ] **Step 2: 运行 F02 聚焦测试，确认 RED**

Run:

```powershell
.\mvnw.cmd -pl xiaou-starter -am "-Dtest=AuthServiceTest,AuthControllerTest,RedisAuthSessionRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，编译错误明确指出 `findUsername` 或公共 `ApiResponse` 尚不存在；不得因其他模块测试失败进入实现。

- [ ] **Step 3: 实现最小公共响应和 session 查询**

创建：

```java
package com.xiaou.web.response;

public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return success(200, message, data);
    }

    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }

    public static <T> ApiResponse<T> failure(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}
```

扩展 repository：

```java
Optional<String> findUsername(String token);
```

Redis adapter 使用现有 `bucket(token)`：

```java
@Override
public Optional<String> findUsername(String token) {
    return Optional.ofNullable(bucket(token).get());
}
```

`AuthController` 的所有 `AuthApiResponse` 类型和工厂调用切换为 `ApiResponse`，HTTP 状态、消息和 JSON 字段保持不变，然后删除 `AuthApiResponse.java`。不要删除 `exists`，避免扩大 F02 接口变更。

- [ ] **Step 4: 运行 F02 聚焦测试，确认 GREEN**

Run:

```powershell
.\mvnw.cmd -pl xiaou-starter -am "-Dtest=AuthServiceTest,AuthControllerTest,RedisAuthSessionRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；登录仍返回 `status=200/data.token`，退出仍保持幂等，Redis 查询存在和缺失 session 均通过。

- [ ] **Step 5: 提交认证兼容切片**

```powershell
git -c safe.directory=D:/agent/AECP add xiaou-common/xiaou-common-web/src/main/java/com/xiaou/web/response/ApiResponse.java xiaou-starter/src/main/java/com/xiaou/web/auth xiaou-starter/src/test/java/com/xiaou/web/auth
git -c safe.directory=D:/agent/AECP commit -m "refactor: share API response and session lookup"
```

---

### Task 2: 创建 Identity 模块、Flyway 表结构与固定种子

**Files:**

- Modify: `xiaou-modules/pom.xml`
- Create: `xiaou-modules/xiaou-aecp-identity/pom.xml`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/resources/db/migration/V1__create_identity_and_organization_members.sql`
- Create: `xiaou-modules/xiaou-aecp-identity/src/test/java/com/xiaou/aecp/identity/IdentityMigrationTest.java`

**Interfaces:**

- Consumes: Maven 父版本 `${revision}` 和 `classpath:db/migration`。
- Produces: `aecp_organization`、`aecp_user_account`、`aecp_organization_member`，以及两个管理员成员关系和六个账号。

- [ ] **Step 1: 注册模块并写迁移失败测试**

在 `xiaou-modules/pom.xml` 增加：

```xml
<modules>
    <module>xiaou-aecp-identity</module>
</modules>
```

新模块 POM 使用 `xiaou-modules` 为父模块，主依赖为 `spring-boot-starter-jdbc`；测试依赖为 `spring-boot-starter-test`、`com.h2database:h2` 和 `org.flywaydb:flyway-core`。创建测试：

```java
class IdentityMigrationTest {

    @Test
    void migrationCreatesSchemaAndDeterministicDemoSeed() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:f03_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("select count(*) from aecp_organization", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from aecp_user_account", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "select count(*) from aecp_organization_member where active = true",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from aecp_organization_member where user_id = 'USR-DEMO-ENG-A'",
                Integer.class)).isZero();
    }
}
```

- [ ] **Step 2: 运行迁移测试，确认 RED**

Run:

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am -Dtest=IdentityMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，查询报 `aecp_organization` 不存在，因为 V1 尚未创建。

- [ ] **Step 3: 写入兼容 MySQL 8.4 与 H2 MySQL mode 的 V1**

迁移采用以下完整结构；所有种子时间为 UTC：

```sql
CREATE TABLE aecp_organization (
    id VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE aecp_user_account (
    id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_aecp_user_account_username UNIQUE (username)
);

CREATE TABLE aecp_organization_member (
    organization_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    removed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT fk_aecp_member_organization FOREIGN KEY (organization_id)
        REFERENCES aecp_organization (id),
    CONSTRAINT fk_aecp_member_user FOREIGN KEY (user_id)
        REFERENCES aecp_user_account (id)
);

CREATE INDEX idx_aecp_member_active_role
    ON aecp_organization_member (organization_id, active, role);

INSERT INTO aecp_organization
    (id, display_name, organization_type, active, created_at, updated_at)
VALUES
    ('ORG-DEMO-COMAC', '商飞演示组织 A', 'AIRFRAME_SIDE', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00'),
    ('ORG-DEMO-AECC', '商发演示组织 B', 'ENGINE_SIDE', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00');

INSERT INTO aecp_user_account
    (id, username, display_name, enabled, created_at, updated_at)
VALUES
    ('USR-DEMO-PM', 'demo-pilot-pm', '演示项目负责人', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00'),
    ('USR-DEMO-ADMIN-A', 'demo-admin-a', '演示管理员 A', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00'),
    ('USR-DEMO-ADMIN-B', 'demo-admin-b', '演示管理员 B', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00'),
    ('USR-DEMO-ENG-A', 'demo-engineer-a', '演示工程师 A', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00'),
    ('USR-DEMO-ENG-B', 'demo-engineer-b', '演示工程师 B', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00'),
    ('USR-DEMO-AUDITOR', 'demo-auditor', '演示审计员', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00');

INSERT INTO aecp_organization_member
    (organization_id, user_id, role, active, joined_at, updated_at, removed_at)
VALUES
    ('ORG-DEMO-COMAC', 'USR-DEMO-ADMIN-A', 'ORGANIZATION_ADMIN', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00', NULL),
    ('ORG-DEMO-AECC', 'USR-DEMO-ADMIN-B', 'ORGANIZATION_ADMIN', TRUE, '2026-08-25 00:00:00', '2026-08-25 00:00:00', NULL);
```

- [ ] **Step 4: 运行迁移测试，确认 GREEN**

Run:

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am -Dtest=IdentityMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；两组织、六账号、两管理员成员关系存在，ENG-A 成员关系不存在。

- [ ] **Step 5: 提交数据库基线切片**

```powershell
git -c safe.directory=D:/agent/AECP add xiaou-modules/pom.xml xiaou-modules/xiaou-aecp-identity
git -c safe.directory=D:/agent/AECP commit -m "feat: add F03 identity schema and demo seed"
```

---

### Task 3: 用领域服务实现授权与成员业务规则

**Files:**

- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationRole.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/UserAccount.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationMembership.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationMember.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationMemberError.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationMemberRepository.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/IdentityConfiguration.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/OrganizationMemberService.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/test/java/com/xiaou/aecp/identity/organization/OrganizationMemberServiceTest.java`

**Interfaces:**

- Consumes: 固定 UTC `Clock`、repository 端口和四个组织角色。
- Produces: `listMembers`、`addMember`、`changeRole`、`removeMember`；Web 层只依赖这些方法和 `OrganizationMemberError.Reason`。

公开类型固定为：

```java
public enum OrganizationRole {
    ORGANIZATION_ADMIN, PROJECT_MANAGER, ENGINEER, AUDITOR
}

public record UserAccount(String id, String username, String displayName) {}

public record OrganizationMembership(
        String organizationId,
        String userId,
        OrganizationRole role,
        boolean active,
        Instant joinedAt) {}

public record OrganizationMember(
        String organizationId,
        String userId,
        String username,
        String displayName,
        OrganizationRole role,
        Instant joinedAt) {}
```

Repository 端口固定为：

```java
public interface OrganizationMemberRepository {
    boolean activeOrganizationExists(String organizationId);
    boolean lockActiveOrganization(String organizationId);
    Optional<UserAccount> findEnabledUserByUsername(String username);
    Optional<UserAccount> findEnabledUserById(String userId);
    Optional<OrganizationMembership> findMembership(String organizationId, String userId);
    Optional<OrganizationMember> findActiveMember(String organizationId, String userId);
    List<OrganizationMember> findActiveMembers(String organizationId);
    long countActiveAdministrators(String organizationId);
    void insertMembership(String organizationId, String userId, OrganizationRole role, Instant now);
    void reactivateMembership(String organizationId, String userId, OrganizationRole role, Instant now);
    void updateRole(String organizationId, String userId, OrganizationRole role, Instant now);
    void deactivateMembership(String organizationId, String userId, Instant now);
}
```

Service 端口固定为：

```java
public List<OrganizationMember> listMembers(String actorUsername, String organizationId);
public OrganizationMember addMember(
        String actorUsername, String organizationId, String userId, OrganizationRole role);
public OrganizationMember changeRole(
        String actorUsername, String organizationId, String userId, OrganizationRole role);
public void removeMember(String actorUsername, String organizationId, String userId);
```

- [ ] **Step 1: 写覆盖全部领域分支的失败测试**

使用固定时钟：

```java
private static final Instant NOW = Instant.parse("2026-08-25T02:00:00Z");
private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
```

在测试内实现 `FakeOrganizationMemberRepository`，用 Map 保存账号和成员，并记录 `calls`。测试方法必须包含：

| 测试方法 | Given / When | 精确断言 |
|---|---|---|
| `adminListsOnlyActiveMembersInRepositoryOrder` | ADMIN-A 查询 COMAC，fake 返回一条 active 和一条 inactive | 结果只包含 active，顺序与 repository 返回一致 |
| `missingActorAccountIsUnauthenticated` | actor username 不存在 | `reason()` 等于 `UNAUTHENTICATED` |
| `nonAdminAndCrossOrganizationActorAreForbidden` | actor 是 ENGINEER，或 ADMIN-A 管理 AECC | `reason()` 等于 `FORBIDDEN` |
| `missingOrganizationIsNotFound` | `activeOrganizationExists/lockActiveOrganization` 返回 false | `reason()` 等于 `ORGANIZATION_NOT_FOUND` |
| `adminAddsEnabledUser` | ADMIN-A 添加 ENG-A 为 ENGINEER | 返回 userId、role、joinedAt 分别为 ENG-A、ENGINEER、NOW |
| `missingOrDisabledTargetUserIsNotFound` | target lookup 返回 empty | `reason()` 等于 `USER_NOT_FOUND`，无写调用 |
| `activeMemberCannotBeAddedTwice` | ENG-A membership 已 active | `reason()` 等于 `ALREADY_ACTIVE` |
| `inactiveMemberIsReactivatedWithNewRoleAndJoinTime` | ENG-A membership inactive | `reactivateMembership` 收到 AUDITOR 和 NOW，返回 active 视图 |
| `duplicateKeyRaceMapsToAlreadyActive` | fake insert 抛 `DuplicateKeyException` | `reason()` 等于 `ALREADY_ACTIVE` |
| `changingToSameRoleIsIdempotent` | 当前角色 ENGINEER，请求 ENGINEER | 返回当前视图，`updateRole` 调用次数为 0 |
| `activeMemberRoleCanBeChanged` | ENGINEER 改为 AUDITOR | `updateRole` 收到 AUDITOR 和 NOW，返回角色 AUDITOR |
| `inactiveOrMissingMemberCannotBeChanged` | membership empty 或 inactive | `reason()` 等于 `MEMBER_NOT_FOUND` |
| `ordinaryMemberCanBeSoftRemoved` | active ENGINEER | `deactivateMembership` 收到 NOW，随后 fake 状态 active=false |
| `inactiveOrMissingMemberCannotBeRemoved` | membership empty 或 inactive | `reason()` 等于 `MEMBER_NOT_FOUND` |
| `lastAdministratorCannotBeDemotedOrRemoved` | 当前成员为管理员且管理员计数为 1 | 两种操作均得到 `LAST_ADMINISTRATOR`，无更新调用 |
| `administratorCanBeDemotedOrRemovedWhenAnotherAdministratorExists` | 管理员计数为 2 | 降级和移除分别成功 |
| `writeLocksOrganizationBeforeAuthorizationAndMutation` | ADMIN-A 添加 ENG-A | `calls` 前三项依次为 lock organization、find actor、find actor membership |

`OrganizationMemberError` 使用以下失败原因，不用自由字符串驱动 HTTP：

```java
public final class OrganizationMemberError extends RuntimeException {

    public enum Reason {
        UNAUTHENTICATED,
        FORBIDDEN,
        ORGANIZATION_NOT_FOUND,
        USER_NOT_FOUND,
        MEMBER_NOT_FOUND,
        ALREADY_ACTIVE,
        LAST_ADMINISTRATOR
    }

    private final Reason reason;

    public OrganizationMemberError(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
```

- [ ] **Step 2: 运行领域测试，确认 RED**

Run:

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am -Dtest=OrganizationMemberServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，领域类型和服务尚不存在。

- [ ] **Step 3: 实现最小领域服务**

`IdentityConfiguration` 提供：

```java
@Bean
@ConditionalOnMissingBean
Clock identityClock() {
    return Clock.systemUTC();
}
```

`OrganizationMemberService` 注入 repository 和 Clock。列表方法使用 `@Transactional(readOnly = true)`；三个写方法使用 `@Transactional`。写方法必须先调用 `lockActiveOrganization`，然后在事务内重新授权。核心顺序如下：

```java
private UserAccount requireAdministrator(String actorUsername, String organizationId) {
    UserAccount actor = repository.findEnabledUserByUsername(actorUsername)
            .orElseThrow(() -> error(UNAUTHENTICATED));
    OrganizationMembership membership = repository.findMembership(organizationId, actor.id())
            .filter(OrganizationMembership::active)
            .orElseThrow(() -> error(FORBIDDEN));
    if (membership.role() != ORGANIZATION_ADMIN) {
        throw error(FORBIDDEN);
    }
    return actor;
}

private void lockOrganization(String organizationId) {
    if (!repository.lockActiveOrganization(organizationId)) {
        throw error(ORGANIZATION_NOT_FOUND);
    }
}
```

`addMember` 在授权后查询 enabled target；active 关系报 `ALREADY_ACTIVE`，inactive 关系调用 `reactivateMembership`，无关系调用 `insertMembership`。捕获 `DuplicateKeyException` 并转为 `ALREADY_ACTIVE`，最后通过 `findActiveMember` 返回视图。

`changeRole` 对不存在或 inactive 关系报 `MEMBER_NOT_FOUND`；相同角色直接返回当前视图。只有当旧角色为管理员且新角色不是管理员时检查 `countActiveAdministrators <= 1` 并报 `LAST_ADMINISTRATOR`。

`removeMember` 对不存在或 inactive 关系报 `MEMBER_NOT_FOUND`；管理员删除前执行同一最后管理员检查，然后调用 `deactivateMembership`。

每个成功写操作用参数化日志记录 `operation`、`organizationId`、`targetUserId`、`actorUsername`、`result=success`，不记录请求体或 token。

- [ ] **Step 4: 运行领域测试，确认 GREEN**

Run:

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am -Dtest=OrganizationMemberServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；所有授权、添加、重新激活、修改、软删除、并发冲突映射和最后管理员分支均通过。

- [ ] **Step 5: 提交领域规则切片**

```powershell
git -c safe.directory=D:/agent/AECP add xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization xiaou-modules/xiaou-aecp-identity/src/test/java/com/xiaou/aecp/identity/organization/OrganizationMemberServiceTest.java
git -c safe.directory=D:/agent/AECP commit -m "feat: enforce F03 organization member rules"
```

---

### Task 4: 实现并验证 Spring JDBC Repository

**Files:**

- Create: `xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/JdbcOrganizationMemberRepository.java`
- Create: `xiaou-modules/xiaou-aecp-identity/src/test/java/com/xiaou/aecp/identity/organization/JdbcOrganizationMemberRepositoryTest.java`

**Interfaces:**

- Consumes: Task 2 的 V1 schema、Task 3 的 repository 端口和领域记录。
- Produces: 基于 `NamedParameterJdbcTemplate` 的完整 repository；成员列表顺序为 `joined_at ASC, user_id ASC`。

- [ ] **Step 1: 写每个 SQL 操作的失败集成测试**

每个测试方法创建唯一 H2 数据库并执行 Flyway：

```java
@BeforeEach
void setUp() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    repository = new JdbcOrganizationMemberRepository(new NamedParameterJdbcTemplate(dataSource));
}
```

测试方法固定为：

| 测试方法 | 操作 | 精确断言 |
|---|---|---|
| `findsAndLocksOnlyActiveOrganization` | 查询及事务内锁定 COMAC、UNKNOWN | COMAC 为 true，UNKNOWN 为 false |
| `findsOnlyEnabledUsersByUsernameAndId` | 按 `demo-admin-a` 和 `USR-DEMO-ENG-A` 查询 | 返回固定 ID、username、displayName；不存在值为 empty |
| `insertsAndReadsActiveMemberView` | 插入 ENG-A/ENGINEER | active view 的五个响应字段与种子和输入一致 |
| `duplicateInsertRaisesDuplicateKeyException` | 连续插入同一组织和用户 | 第二次抛 `DuplicateKeyException` |
| `reactivatesRemovedMemberWithNewRoleAndTime` | 先 deactivate，再以 AUDITOR reactivate | active=true、role=AUDITOR、joinedAt=新时间、removed_at 为 null |
| `listsOnlyActiveMembersInStableOrder` | 插入两个同 joinedAt 用户并移除其中一个 | 只返回 active；同时间按 userId 升序 |
| `updatesRoleAndUpdatedAt` | ENGINEER 改 AUDITOR | view 角色为 AUDITOR，数据库 updated_at 等于输入时间 |
| `deactivatesWithoutDeletingRow` | deactivate ENG-A | active view empty；底层行仍存在且 removed_at 等于输入时间 |
| `countsOnlyActiveOrganizationAdministrators` | 查询 COMAC，增加管理员，再移除新增管理员 | 计数依次为 1、2、1 |

- [ ] **Step 2: 运行 repository 测试，确认 RED**

Run:

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am -Dtest=JdbcOrganizationMemberRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，`JdbcOrganizationMemberRepository` 尚不存在。

- [ ] **Step 3: 用明确 SQL 实现 repository**

查询 SQL 固定为：

```sql
SELECT id FROM aecp_organization
WHERE id = :organizationId AND active = TRUE

SELECT id FROM aecp_organization
WHERE id = :organizationId AND active = TRUE
FOR UPDATE

SELECT id, username, display_name FROM aecp_user_account
WHERE username = :username AND enabled = TRUE

SELECT id, username, display_name FROM aecp_user_account
WHERE id = :userId AND enabled = TRUE

SELECT organization_id, user_id, role, active, joined_at
FROM aecp_organization_member
WHERE organization_id = :organizationId AND user_id = :userId

SELECT m.organization_id, m.user_id, u.username, u.display_name, m.role, m.joined_at
FROM aecp_organization_member m
JOIN aecp_user_account u ON u.id = m.user_id
WHERE m.organization_id = :organizationId
  AND m.user_id = :userId
  AND m.active = TRUE
  AND u.enabled = TRUE

SELECT m.organization_id, m.user_id, u.username, u.display_name, m.role, m.joined_at
FROM aecp_organization_member m
JOIN aecp_user_account u ON u.id = m.user_id
WHERE m.organization_id = :organizationId
  AND m.active = TRUE
  AND u.enabled = TRUE
ORDER BY m.joined_at ASC, m.user_id ASC

SELECT COUNT(*) FROM aecp_organization_member
WHERE organization_id = :organizationId
  AND active = TRUE
  AND role = 'ORGANIZATION_ADMIN'
```

写 SQL 固定为：

```sql
INSERT INTO aecp_organization_member
    (organization_id, user_id, role, active, joined_at, updated_at, removed_at)
VALUES
    (:organizationId, :userId, :role, TRUE, :now, :now, NULL)

UPDATE aecp_organization_member
SET role = :role, active = TRUE, joined_at = :now, updated_at = :now, removed_at = NULL
WHERE organization_id = :organizationId AND user_id = :userId

UPDATE aecp_organization_member
SET role = :role, updated_at = :now
WHERE organization_id = :organizationId AND user_id = :userId AND active = TRUE

UPDATE aecp_organization_member
SET active = FALSE, updated_at = :now, removed_at = :now
WHERE organization_id = :organizationId AND user_id = :userId AND active = TRUE
```

使用 `Timestamp.from(instant)` 写入 UTC instant，使用 `resultSet.getTimestamp("joined_at").toInstant()` 读取。角色通过 `OrganizationRole.valueOf` 映射；不要用字符串拼接构造 SQL。

- [ ] **Step 4: 运行迁移、repository 和领域测试，确认 GREEN**

Run:

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am "-Dtest=IdentityMigrationTest,OrganizationMemberServiceTest,JdbcOrganizationMemberRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；迁移、领域规则和 JDBC 行为同时通过。

- [ ] **Step 5: 提交 JDBC 切片**

```powershell
git -c safe.directory=D:/agent/AECP add xiaou-modules/xiaou-aecp-identity/src/main/java/com/xiaou/aecp/identity/organization/JdbcOrganizationMemberRepository.java xiaou-modules/xiaou-aecp-identity/src/test/java/com/xiaou/aecp/identity/organization/JdbcOrganizationMemberRepositoryTest.java
git -c safe.directory=D:/agent/AECP commit -m "feat: persist F03 organization members"
```

---

### Task 5: 暴露 Bearer 鉴权的 F03 REST API

**Files:**

- Modify: `xiaou-starter/pom.xml`
- Modify: `xiaou-starter/src/main/java/com/xiaou/web/Application.java`
- Modify: `xiaou-starter/src/main/resources/application.yml`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/auth/InvalidSessionException.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/auth/BearerSessionAuthenticator.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/AddOrganizationMemberRequest.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/UpdateOrganizationMemberRoleRequest.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/OrganizationMemberResponse.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/OrganizationMemberListResponse.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/RemoveOrganizationMemberResponse.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/OrganizationMemberController.java`
- Create: `xiaou-starter/src/main/java/com/xiaou/web/organization/OrganizationMemberExceptionHandler.java`
- Create: `xiaou-starter/src/test/java/com/xiaou/web/auth/BearerSessionAuthenticatorTest.java`
- Create: `xiaou-starter/src/test/java/com/xiaou/web/organization/OrganizationMemberControllerTest.java`

**Interfaces:**

- Consumes: `AuthSessionRepository.findUsername`、`OrganizationMemberService` 和公共 `ApiResponse`。
- Produces: POST/GET collection、PATCH/DELETE member 四个接口，以及 400/401/403/404/409/500/503 HTTP 映射。

请求和响应 DTO 固定为：

```java
public record AddOrganizationMemberRequest(
        @NotBlank @JsonProperty("user_id") String userId,
        @NotNull OrganizationRole role) {}

public record UpdateOrganizationMemberRoleRequest(@NotNull OrganizationRole role) {}

public record OrganizationMemberResponse(
        @JsonProperty("user_id") String userId,
        String username,
        @JsonProperty("display_name") String displayName,
        OrganizationRole role,
        @JsonProperty("joined_at") Instant joinedAt) {
    static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.userId(),
                member.username(),
                member.displayName(),
                member.role(),
                member.joinedAt());
    }
}

public record OrganizationMemberListResponse(
        List<OrganizationMemberResponse> items,
        long total) {}

public record RemoveOrganizationMemberResponse(boolean removed) {}
```

- [ ] **Step 1: 添加编译依赖并写 Bearer 与 MockMvc 失败测试**

`xiaou-starter/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.xiaou</groupId>
    <artifactId>xiaou-aecp-identity</artifactId>
    <version>${revision}</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

Bearer 测试覆盖：

```java
@Test
void validBearerReturnsStoredUsername() {
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    when(sessions.findUsername("opaque-token")).thenReturn(Optional.of("demo-admin-a"));
    BearerSessionAuthenticator authenticator = new BearerSessionAuthenticator(sessions);

    assertThat(authenticator.requireUsername("Bearer opaque-token")).isEqualTo("demo-admin-a");
}

@Test
void bearerSchemeIsCaseInsensitive() {
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    when(sessions.findUsername("opaque-token")).thenReturn(Optional.of("demo-admin-a"));
    BearerSessionAuthenticator authenticator = new BearerSessionAuthenticator(sessions);

    assertThat(authenticator.requireUsername("bearer opaque-token")).isEqualTo("demo-admin-a");
}

@Test
void missingMalformedBlankOrExpiredTokenIsUnauthorized() {
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    when(sessions.findUsername("expired-token")).thenReturn(Optional.empty());
    BearerSessionAuthenticator authenticator = new BearerSessionAuthenticator(sessions);

    assertThatThrownBy(() -> authenticator.requireUsername(null)).isInstanceOf(InvalidSessionException.class);
    assertThatThrownBy(() -> authenticator.requireUsername("")).isInstanceOf(InvalidSessionException.class);
    assertThatThrownBy(() -> authenticator.requireUsername("Basic abc")).isInstanceOf(InvalidSessionException.class);
    assertThatThrownBy(() -> authenticator.requireUsername("Bearer ")).isInstanceOf(InvalidSessionException.class);
    assertThatThrownBy(() -> authenticator.requireUsername("Bearer expired-token")).isInstanceOf(InvalidSessionException.class);
}
```

MockMvc 测试至少包含：

```java
@Test
void addReturnsHttpAndBodyStatus201WithSnakeCaseMember() throws Exception {
    when(authenticator.requireUsername("Bearer admin-token")).thenReturn("demo-admin-a");
    OrganizationMember member = new OrganizationMember(
            "ORG-DEMO-COMAC", "USR-DEMO-ENG-A", "demo-engineer-a",
            "演示工程师 A", OrganizationRole.ENGINEER,
            Instant.parse("2026-08-25T02:00:00Z"));
    when(service.addMember("demo-admin-a", "ORG-DEMO-COMAC",
            "USR-DEMO-ENG-A", OrganizationRole.ENGINEER)).thenReturn(member);

    mockMvc.perform(post("/api/v1/organizations/ORG-DEMO-COMAC/members")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"user_id\":\"USR-DEMO-ENG-A\",\"role\":\"ENGINEER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.user_id").value("USR-DEMO-ENG-A"))
            .andExpect(jsonPath("$.data.display_name").value("演示工程师 A"))
            .andExpect(jsonPath("$.data.role").value("ENGINEER"))
            .andExpect(jsonPath("$.data.joined_at").value("2026-08-25T02:00:00Z"));
}
```

其余 MockMvc 测试使用同一 `admin-token -> demo-admin-a` stub，并按下表构造请求或让依赖抛出异常：

| 测试方法 | 输入或 stub | 精确断言 |
|---|---|---|
| `listReturnsOnlyItemsAndTotalWithStableServiceOrder` | service 返回 ADMIN-A、ENG-A | HTTP/status 200，`items[0/1].user_id` 保序，`total=2` |
| `patchReturnsUpdatedRole` | PATCH role=AUDITOR | HTTP/status 200，`data.role=AUDITOR` |
| `deleteReturnsRemovedTrue` | DELETE ENG-A | HTTP/status 200，`data.removed=true` |
| `blankUserIdAndUnknownRoleReturn400` | POST 空 user_id；PATCH role=OWNER | 两次均 HTTP/status 400，data=null |
| `missingOrExpiredSessionReturns401` | authenticator 抛 `InvalidSessionException` | HTTP/status 401，响应不含 token |
| `nonAdminAndCrossOrganizationRequestsReturn403` | service 抛 reason FORBIDDEN | HTTP/status 403，data=null |
| `missingOrganizationUserOrMemberReturn404` | 依次抛三个 not-found reason | 每个响应 HTTP/status 404 |
| `duplicateMemberAndLastAdministratorReturn409` | 依次抛 ALREADY_ACTIVE、LAST_ADMINISTRATOR | 每个响应 HTTP/status 409 |
| `redisOrDatabaseUnavailableReturns503WithoutInternalDetails` | 抛 `RedisException`、`DataAccessResourceFailureException` | HTTP/status 503；响应不含连接地址、SQL 或 key |
| `unexpectedFailureReturns500WithoutInternalDetails` | service 抛 `IllegalStateException("internal-detail")` | HTTP/status 500；响应不含 `internal-detail` |

- [ ] **Step 2: 运行 Web 聚焦测试，确认 RED**

Run:

```powershell
.\mvnw.cmd -pl xiaou-starter -am "-Dtest=BearerSessionAuthenticatorTest,OrganizationMemberControllerTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，认证适配器、Controller 和 DTO 尚不存在。

- [ ] **Step 3: 实现 Bearer session 解析**

```java
@Component
public class BearerSessionAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";
    private final AuthSessionRepository sessions;

    public String requireUsername(String authorization) {
        if (authorization == null || !authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new InvalidSessionException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new InvalidSessionException();
        }
        return sessions.findUsername(token)
                .filter(username -> !username.isBlank())
                .orElseThrow(InvalidSessionException::new);
    }
}
```

`InvalidSessionException` 的客户端消息固定为 `认证信息无效`；异常对象不保存 token。

- [ ] **Step 4: 实现四个 Controller 方法与 DTO 映射**

Controller 使用路径 `/api/v1/organizations/{organizationId}/members`。新增方法的核心形式为：

```java
@PostMapping
public ResponseEntity<ApiResponse<OrganizationMemberResponse>> addMember(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @PathVariable String organizationId,
        @Valid @RequestBody AddOrganizationMemberRequest request) {
    String actor = authenticator.requireUsername(authorization);
    OrganizationMember member = service.addMember(
            actor, organizationId, request.userId(), request.role());
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(201, "成员添加成功", OrganizationMemberResponse.from(member)));
}
```

GET 返回 `ApiResponse.success("查询成功", new OrganizationMemberListResponse(items, items.size()))`；PATCH 返回 `成员角色更新成功`；DELETE 在 service 无返回值成功后返回 `new RemoveOrganizationMemberResponse(true)` 和 `成员移除成功`。

- [ ] **Step 5: 实现 F03 专用异常映射并接入应用扫描**

`OrganizationMemberExceptionHandler` 使用：

```java
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OrganizationMemberController.class)
```

映射固定为：

```java
UNAUTHENTICATED -> 401
FORBIDDEN -> 403
ORGANIZATION_NOT_FOUND, USER_NOT_FOUND, MEMBER_NOT_FOUND -> 404
ALREADY_ACTIVE, LAST_ADMINISTRATOR -> 409
MethodArgumentNotValidException, HttpMessageNotReadableException -> 400
RedisException, DataAccessResourceFailureException, CannotAcquireLockException -> 503
other DataAccessException, Exception -> 500
```

每个 handler 返回 `ResponseEntity<ApiResponse<Void>>`，HTTP 状态与 body.status 相同，body.data 为 null。客户端消息使用固定中文，不拼接 exception message；服务端日志只记录 URI、异常类型和领域 reason。

应用扫描改为：

```java
@SpringBootApplication(scanBasePackages = {"com.xiaou.web", "com.xiaou.aecp.identity"})
@ConfigurationPropertiesScan(basePackages = "com.xiaou.web")
```

在 `application.yml` 的 `spring` 下加入：

```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
```

- [ ] **Step 6: 运行 F03 Web 与 F02 回归测试，确认 GREEN**

Run:

```powershell
.\mvnw.cmd -pl xiaou-starter -am "-Dtest=BearerSessionAuthenticatorTest,OrganizationMemberControllerTest,AuthControllerTest,AuthServiceTest,RedisAuthSessionRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；四个 F03 接口和全部错误状态通过，F02 JSON 契约与退出幂等继续通过。

- [ ] **Step 7: 提交 HTTP 与运行时接线切片**

```powershell
git -c safe.directory=D:/agent/AECP add xiaou-starter/pom.xml xiaou-starter/src/main/java/com/xiaou/web/Application.java xiaou-starter/src/main/resources/application.yml xiaou-starter/src/main/java/com/xiaou/web/auth xiaou-starter/src/main/java/com/xiaou/web/organization xiaou-starter/src/test/java/com/xiaou/web/auth xiaou-starter/src/test/java/com/xiaou/web/organization
git -c safe.directory=D:/agent/AECP commit -m "feat: expose F03 organization member APIs"
```

---

### Task 6: 完成分层验证、真实 MySQL/Redis E2E 与项目记录

**Files:**

- Modify after successful verification: `PROGRESS.md`
- Modify after successful verification: `DECISIONS.md`
- Modify after successful verification: `docs/STARTUP_CHECKLIST.md`
- Do not modify: `docs/features.md`

**Interfaces:**

- Consumes: F02 login/logout、F03 四个接口、Docker Compose MySQL/Redis。
- Produces: 实际命令证据、可重复启动说明和 F03 架构决策记录。

- [ ] **Step 1: 按层运行 identity 与 Web 聚焦测试**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am "-Dtest=IdentityMigrationTest,OrganizationMemberServiceTest,JdbcOrganizationMemberRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl xiaou-starter -am "-Dtest=BearerSessionAuthenticatorTest,OrganizationMemberControllerTest,AuthControllerTest,AuthServiceTest,RedisAuthSessionRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 两条命令均 exit 0。任何失败都先修复并重跑当前层，不启动 Compose。

- [ ] **Step 2: 运行完整 Maven 测试与打包**

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd -pl xiaou-starter -am package -DskipTests
```

Expected: 全 Reactor 模块成功，starter 可重打包。

- [ ] **Step 3: 运行未修改前端的回归验证**

```powershell
pnpm --dir xiaou-frontend test:routes
pnpm --dir xiaou-frontend typecheck
pnpm --dir xiaou-frontend build
```

Expected: 路由测试、TypeScript 检查和 Vite 构建全部 exit 0。

- [ ] **Step 4: 启动 MySQL、Redis 和管理员身份应用**

```powershell
docker compose up -d
docker compose ps
$env:AECP_AUTH_DEMO_USERNAME = 'demo-admin-a'
if ([string]::IsNullOrWhiteSpace($env:AECP_TEST_PASSWORD)) {
    throw 'Set AECP_TEST_PASSWORD in terminal A before starting the application'
}
$env:AECP_AUTH_DEMO_PASSWORD = $env:AECP_TEST_PASSWORD
.\mvnw.cmd -pl xiaou-starter spring-boot:run
```

Expected: MySQL 和 Redis 均为 healthy；Flyway V1 成功；应用监听 8080。临时密码只保存在当前进程环境，不写入文件。应用在终端 A 前台运行，以下命令在终端 B 执行。


终端 B 必须注入相同的临时值，但不得把真实值复制到仓库文件：

```powershell
if ([string]::IsNullOrWhiteSpace($env:AECP_TEST_PASSWORD)) {
    throw 'Set AECP_TEST_PASSWORD in terminal B to the same process-local value used in terminal A'
}
```
- [ ] **Step 5: 通过 F02 获取管理员 token 并建立可重复前置状态**

```powershell
$loginJson = curl.exe -sS -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"demo-admin-a\",\"password\":\"$env:AECP_TEST_PASSWORD\"}"
$login = $loginJson | ConvertFrom-Json
if ($login.status -ne 200 -or [string]::IsNullOrWhiteSpace($login.data.token)) {
    throw 'Admin login failed'
}
$env:AECP_TEST_TOKEN = $login.data.token

# 允许 200 或 404；目的只是确保下一次 POST 必定是新增或重新激活并返回 201。
curl.exe -sS -X DELETE `
  http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members/USR-DEMO-ENG-A `
  -H "Authorization: Bearer $env:AECP_TEST_TOKEN" | Out-Null
```

- [ ] **Step 6: 执行功能清单的 F03 命令并验证完整 CRUD**

先原样执行功能清单验收：

```powershell
curl.exe -sS -X POST http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members `
  -H "Authorization: Bearer $env:AECP_TEST_TOKEN" `
  -H "Content-Type: application/json" `
  -d "{\"user_id\":\"USR-DEMO-ENG-A\",\"role\":\"ENGINEER\"}" `
  | jq -e '.status == 201'
```

再验证列表、修改和删除：

```powershell
$headers = @{ Authorization = "Bearer $env:AECP_TEST_TOKEN" }
$list = Invoke-RestMethod -Method Get `
  -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members' `
  -Headers $headers
$engineer = $list.data.items | Where-Object user_id -eq 'USR-DEMO-ENG-A'
if ($list.status -ne 200 -or $engineer.role -ne 'ENGINEER') { throw 'List after add failed' }

$patchBody = @{ role = 'AUDITOR' } | ConvertTo-Json -Compress
$updated = Invoke-RestMethod -Method Patch `
  -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members/USR-DEMO-ENG-A' `
  -Headers $headers -ContentType 'application/json' -Body $patchBody
if ($updated.status -ne 200 -or $updated.data.role -ne 'AUDITOR') { throw 'Role update failed' }

$removed = Invoke-RestMethod -Method Delete `
  -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members/USR-DEMO-ENG-A' `
  -Headers $headers
if ($removed.status -ne 200 -or $removed.data.removed -ne $true) { throw 'Member removal failed' }

$afterDelete = Invoke-RestMethod -Method Get `
  -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members' `
  -Headers $headers
if ($afterDelete.data.items.user_id -contains 'USR-DEMO-ENG-A') { throw 'Removed member is still listed' }
```

- [ ] **Step 7: 验证 401、跨组织 403 和非管理员 403**

管理员应用仍运行时执行：

```powershell
$invalidStatus = curl.exe -sS -o $null -w "%{http_code}" `
  http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members `
  -H "Authorization: Bearer invalid-token"
if ([int]$invalidStatus -ne 401) { throw 'Invalid token was not rejected with 401' }

$crossOrgStatus = curl.exe -sS -o $null -w "%{http_code}" `
  http://localhost:8080/api/v1/organizations/ORG-DEMO-AECC/members `
  -H "Authorization: Bearer $env:AECP_TEST_TOKEN"
if ([int]$crossOrgStatus -ne 403) { throw 'Cross-organization access was not rejected with 403' }
```

在终端 A 用 Ctrl+C 停止应用，保留 Compose 和 Redis；然后改为工程师账号并重启：

```powershell
$env:AECP_AUTH_DEMO_USERNAME = 'demo-engineer-a'
.\mvnw.cmd -pl xiaou-starter spring-boot:run
```

终端 B 登录并验证：

```powershell
$engineerLoginJson = curl.exe -sS -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"demo-engineer-a\",\"password\":\"$env:AECP_TEST_PASSWORD\"}"
$engineerLogin = $engineerLoginJson | ConvertFrom-Json
if ($engineerLogin.status -ne 200) { throw 'Engineer login failed' }
$engineerToken = $engineerLogin.data.token
$nonAdminStatus = curl.exe -sS -o $null -w "%{http_code}" `
  http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members `
  -H "Authorization: Bearer $engineerToken"
if ([int]$nonAdminStatus -ne 403) { throw 'Non-admin access was not rejected with 403' }
```

- [ ] **Step 8: 清理会话和基础设施，不删除数据卷**

```powershell
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/logout `
  -H "Authorization: Bearer $engineerToken" | Out-Null
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/logout `
  -H "Authorization: Bearer $env:AECP_TEST_TOKEN" | Out-Null
```

在终端 A 用 Ctrl+C 停止应用，然后执行：

```powershell
docker compose down
Remove-Item Env:AECP_AUTH_DEMO_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:AECP_TEST_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:AECP_TEST_TOKEN -ErrorAction SilentlyContinue
```

Expected: 应用停止，容器停止，MySQL/Redis volume 保留；ENG-A 最终处于 inactive，下一轮可以重新添加。

- [ ] **Step 9: 检查差异和秘密，再记录真实结果**

```powershell
git -c safe.directory=D:/agent/AECP diff --check
git -c safe.directory=D:/agent/AECP status --short
rg -n "Authorization: Bearer|aecp:auth:session:" xiaou-starter/src/main/java xiaou-modules/xiaou-aecp-identity/src/main/java
```

检查结果必须证明：源码没有硬编码真实 token 或密码；日志语句没有输出 Authorization、session key 或 SQL 明细。只有前述所有命令实际成功后：

- 在 `PROGRESS.md` 把 F03 记录为已完成，并逐条追加 Maven、前端和真实 E2E 命令结果；下一步改为 F04。
- 在 `DECISIONS.md` 记录“F03 使用独立 identity 模块、Flyway、Spring JDBC、组织级行锁和软删除”。
- 在 `docs/STARTUP_CHECKLIST.md` 更新“尚无迁移/业务 API”的过时说明，补充 Flyway 启动、F03 smoke 入口，以及回滚触发条件：迁移失败、启动健康失败、F02 回归失败或 F03 CRUD smoke 失败；回滚应用版本时保留 V1 表和成员数据，修复只能新增迁移版本，不修改已执行 V1。
- 保持 `docs/features.md` 不变。

- [ ] **Step 10: 运行最终证据检查并提交收尾记录**

```powershell
.\mvnw.cmd clean test
git -c safe.directory=D:/agent/AECP diff --check
git -c safe.directory=D:/agent/AECP status --short
git -c safe.directory=D:/agent/AECP add PROGRESS.md DECISIONS.md docs/STARTUP_CHECKLIST.md
git -c safe.directory=D:/agent/AECP commit -m "docs: record F03 verification results"
git -c safe.directory=D:/agent/AECP status --short
```

Expected: 最后一次 Maven 全量测试 exit 0，提交只包含验证记录，最终工作区为空。
