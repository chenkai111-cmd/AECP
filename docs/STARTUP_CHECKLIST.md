# AECP 启动就绪清单

## 1. 当前状态

本文件记录 AECP 当前可重复的验证入口。F00-F03 已完成，当前启动链路包含 Spring Boot、Flyway V1、MySQL 8.4、Redis 7.4、F02 认证和 F03 组织成员 API。

本轮已完成以下真实验证：

- [x] Java 17 可用
- [x] Maven Wrapper 3.9.9 可用
- [x] Docker Engine 与 Docker Compose 可用
- [x] `docker compose config` 通过
- [x] MySQL、Redis 容器健康
- [x] Flyway V1 在 MySQL 首次启动时成功应用，重复启动可安全校验
- [x] `mvnw clean test` 通过，测试不依赖外部服务
- [x] `xiaou-starter` 多模块打包通过
- [x] Spring Boot 启动后 `/` 返回 HTTP 200
- [x] F02 登录/退出与 F03 成员 CRUD、401、跨组织 403、非管理员 403 真实 E2E 通过
- [x] Compose 已停止，工作区没有未追踪密钥文件

最近一次 F03 验证记录（2026-08-26）：

- Java `17.0.20.1`；Maven Wrapper `3.9.9`。
- MySQL `8.4` 与 Redis `7.4` 均报告 `healthy`；Flyway 首次应用 V1，第二次启动确认 schema 已是最新。
- Identity 聚焦测试 27/27；F03 Web 与 F02 回归测试 28/28。
- `mvnw clean test` 全 Reactor 成功：Identity 27/27，starter 32/32。
- `xiaou-starter` 多模块 `package -DskipTests` 成功。
- 前端路由 29/29，TypeScript 检查和 Vite 构建通过。
- 真实 HTTP：F02 登录 200；F03 add 201、list/patch/delete 200；无效 token 401；跨组织和非管理员访问 403；logout 200。
- 验证结束后已停止应用并执行 `docker compose down`；数据卷保留，未创建或提交 `.env`。

## 2. 环境准备

Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -version
docker version
docker compose version
```

如果系统尚未安装依赖，需要管理员 PowerShell 执行：

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK --exact --source winget
winget install --id Docker.DockerDesktop --exact --source winget
```

Docker Desktop 首次启动后确认 Engine 已启动，且 `docker info` 能返回 Server 信息。

## 3. 配置与 Compose

仓库只提交 `.env.example`，真实 `.env` 被 `.gitignore` 排除。复制并按需修改：

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up -d
docker compose ps
```

停止本地基础设施：

```powershell
docker compose down
```

如需同时删除本地数据库和 Redis 数据卷，必须明确确认后再执行 `docker compose down -v`。

## 4. 测试、打包和启动

自动测试使用 H2/MockMvc，不启动外部 MySQL/Redis：

```powershell
.\mvnw.cmd clean test
```

F03 聚焦回归入口：

```powershell
.\mvnw.cmd -pl xiaou-modules/xiaou-aecp-identity -am "-Dtest=IdentityMigrationTest,OrganizationMemberServiceTest,JdbcOrganizationMemberRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl xiaou-starter -am "-Dtest=BearerSessionAuthenticatorTest,OrganizationMemberControllerTest,AuthControllerTest,AuthServiceTest,RedisAuthSessionRepositoryTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

多模块打包：

```powershell
.\mvnw.cmd -pl xiaou-starter -am package -DskipTests
```

启动前先启动 Compose，因为 `dev` 配置会连接本地 MySQL 和 Redis：

```powershell
$env:AECP_AUTH_DEMO_USERNAME = 'demo-admin-a'
if ([string]::IsNullOrWhiteSpace($env:AECP_TEST_PASSWORD)) { throw 'Set AECP_TEST_PASSWORD for this terminal' }
$env:AECP_AUTH_DEMO_PASSWORD = $env:AECP_TEST_PASSWORD
docker compose up -d
docker compose ps
.\mvnw.cmd -pl xiaou-starter spring-boot:run
```

另开一个 PowerShell 窗口执行 HTTP 冒烟：

```powershell
$response = Invoke-WebRequest http://localhost:8080/
$response.StatusCode
$response.Content
```

预期状态码为 `200`，响应包含 `欢迎使用AECP`。验证完成后回到应用窗口停止 Spring Boot，再执行 `docker compose down`。

F03 最小 smoke 在另一个设置了相同 `AECP_TEST_PASSWORD` 的 PowerShell 执行：

```powershell
if ([string]::IsNullOrWhiteSpace($env:AECP_TEST_PASSWORD)) { throw 'Set the same process-local password' }
$loginBody = @{ username = 'demo-admin-a'; password = $env:AECP_TEST_PASSWORD } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.data.token)" }

try {
    Invoke-RestMethod -Method Delete -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members/USR-DEMO-ENG-A' -Headers $headers | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

$memberBody = @{ user_id = 'USR-DEMO-ENG-A'; role = 'ENGINEER' } | ConvertTo-Json -Compress
$added = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members' -Headers $headers -ContentType 'application/json' -Body $memberBody
$list = Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members' -Headers $headers
if ($added.status -ne 201 -or $list.data.items.user_id -notcontains 'USR-DEMO-ENG-A') { throw 'F03 smoke failed' }
```

预期新增返回 201，列表返回 200 且包含 `USR-DEMO-ENG-A`。测试密码和 token 只保存在当前进程环境，禁止写入仓库文件。

## 5. 项目结构说明

- `xiaou-common`：模板公共模块和依赖 BOM，包含 core、web、mybatis、redis 等能力。
- `xiaou-starter`：Spring Boot 启动模块，包含首页、F02 认证、F03 Bearer/REST 适配和 Flyway 运行时接线。
- `xiaou-modules/xiaou-aecp-identity`：F03 Identity 领域、Spring JDBC Repository、Flyway V1 与独立测试。
- `xiaou-test`：模板测试支持模块。
- `docker-compose.yml`：MySQL 8.4、Redis 7.4、本地端口、健康检查和持久化卷。
- `application-dev.yml` / `application-prod.yml`：通过环境变量读取数据库、Redis 和运行参数，不包含远程账号密码。

## 6. 故障排查与已知限制

- PowerShell 报错 `740` 表示当前窗口没有管理员权限；请以“管理员身份运行”的 PowerShell 执行系统安装或 DISM 命令。
- Docker Desktop 已安装但 `docker info` 没有 Server 信息时，先启动 Docker Desktop 并等待 Engine 就绪。
- 3306 或 6379 被占用时，停止占用端口的服务，或在 `.env`/Compose 中调整端口映射后同步修改应用地址。
- MySQL 8.4 本地非 TLS 连接若报 `Public Key Retrieval is not allowed`，确认开发 JDBC URL 包含 `allowPublicKeyRetrieval=true`；生产环境应使用 TLS 和独立密钥管理。
- `mvnw clean test` 默认不依赖 MySQL/Redis；真实联通性由 Compose 启动后的 F02/F03 smoke 覆盖。
- 回滚触发条件：Flyway 迁移失败、应用启动健康失败、F02 回归失败或 F03 CRUD smoke 失败。
- 回滚应用版本时保留 V1 表和成员数据，不执行 `docker compose down -v`；修复只能新增迁移版本，不修改已执行的 V1。
- 生产部署配置仍属于后续范围，不能直接复用开发环境的非 TLS 数据库参数。
