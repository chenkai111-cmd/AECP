# AECP 启动就绪清单

## 1. 当前状态

本文件记录 AECP 工程初始化状态和可重复的验证入口。业务功能尚未开发，当前目标是确认模板可以在本地使用 JDK 17、Maven Wrapper、MySQL、Redis 和 Spring Boot 运行。

本轮已完成以下真实验证：

- [x] Java 17 可用
- [x] Maven Wrapper 3.9.9 可用
- [x] Docker Engine 与 Docker Compose 可用
- [x] `docker compose config` 通过
- [x] MySQL、Redis 容器健康
- [x] `mvnw clean test` 通过，测试不依赖外部服务
- [x] `xiaou-starter` 多模块打包通过
- [x] Spring Boot 启动后 `/` 返回 HTTP 200
- [x] Compose 已停止，工作区没有未追踪密钥文件

本轮验证记录（2026-08-25）：

- Java `17.0.20.1`；Maven Wrapper `3.9.9`。
- Docker Server `29.7.2`；Docker Compose `v5.4.0`；`docker compose config` 退出码为 0。
- MySQL `8.4` 与 Redis `7.4` 均报告 `healthy`；应用日志确认 Redisson 已连接 `localhost:6379`。
- `mvnw clean test`：全 Reactor 模块成功，`IndexControllerTest` 和 Redis 配置回归测试均通过。
- `xiaou-starter` 多模块 `package -DskipTests` 成功。
- HTTP 冒烟：`GET http://localhost:8080/` 返回 `200`，内容为 `欢迎使用AECP，请通过前端地址访问。`。
- 验证结束后已执行 `docker compose down`；未创建或提交 `.env`。

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

测试不启动 MySQL/Redis，验证入口控制器的 HTTP 200 和欢迎信息：

```powershell
.\mvnw.cmd clean test
```

多模块打包：

```powershell
.\mvnw.cmd -pl xiaou-starter -am package -DskipTests
```

启动前先启动 Compose，因为 `dev` 配置会连接本地 MySQL 和 Redis：

```powershell
docker compose up -d
.\mvnw.cmd -pl xiaou-starter spring-boot:run
```

另开一个 PowerShell 窗口执行 HTTP 冒烟：

```powershell
$response = Invoke-WebRequest http://localhost:8080/
$response.StatusCode
$response.Content
```

预期状态码为 `200`，响应包含 `欢迎使用AECP`。验证完成后回到应用窗口停止 Spring Boot，再执行 `docker compose down`。

## 5. 项目结构说明

- `xiaou-common`：模板公共模块和依赖 BOM，包含 core、web、mybatis、redis 等能力。
- `xiaou-starter`：Spring Boot 启动模块，当前包含模板首页入口 `IndexController` 和独立 Web 层测试。
- `xiaou-modules`：模板业务模块聚合目录，当前不新增 AECP 业务功能。
- `xiaou-test`：模板测试支持模块。
- `docker-compose.yml`：MySQL 8.4、Redis 7.4、本地端口、健康检查和持久化卷。
- `application-dev.yml` / `application-prod.yml`：通过环境变量读取数据库、Redis 和运行参数，不包含远程账号密码。

## 6. 故障排查与已知限制

- PowerShell 报错 `740` 表示当前窗口没有管理员权限；请以“管理员身份运行”的 PowerShell 执行系统安装或 DISM 命令。
- Docker Desktop 已安装但 `docker info` 没有 Server 信息时，先启动 Docker Desktop 并等待 Engine 就绪。
- 3306 或 6379 被占用时，停止占用端口的服务，或在 `.env`/Compose 中调整端口映射后同步修改应用地址。
- `mvnw clean test` 默认不依赖 MySQL/Redis；真实联通性由 Compose 启动后的应用冒烟验证覆盖。
- 当前没有数据库迁移脚本、业务 API、鉴权流程和生产部署配置；这些属于后续开发范围。
