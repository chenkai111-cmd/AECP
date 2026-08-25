# AECP 协作与启动说明

## 后续会话必读

开始任何修改前，先阅读本文件和 `docs/STARTUP_CHECKLIST.md`，再查看相关模块的实际代码。所有“已完成”“已通过”“可启动”的结论都必须有本地命令输出作为依据，不得无验证声称完成。

## 当前工程边界

- 项目名称：AECP
- 当前分支：`main`
- 技术栈：Java 17、Spring Boot 3.4.4、Maven Wrapper 3.9.9、MySQL 8.4、Redis 7.4、Docker Compose
- Maven 坐标、`com.xiaou` 包名和 `xiaou-*` 模块名来自模板，当前阶段保持不变
- 本阶段只完成工程初始化、环境配置和可验证测试框架，不开发会议、任务、文件、部件追溯等业务功能

## 目录结构

```text
AECP/
├─ xiaou-common/       公共能力模块及 BOM
├─ xiaou-starter/      Spring Boot 启动模块、Web 入口和启动测试
├─ xiaou-modules/      模板业务模块聚合目录，当前不新增 AECP 业务
├─ xiaou-test/         模板测试支持模块
├─ docker-compose.yml  本地 MySQL/Redis 基础设施
├─ .env.example        本地环境变量示例，真实 .env 不入 Git
├─ mvnw / mvnw.cmd      Maven 3.9.9 Wrapper 入口
└─ docs/                工程运行和验收文档
```

## 常用命令

在 PowerShell 中先确保当前会话使用 JDK 17：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

```powershell
docker compose up -d
.\mvnw.cmd clean test
.\mvnw.cmd -pl xiaou-starter -am package -DskipTests
.\mvnw.cmd -pl xiaou-starter spring-boot:run
Invoke-WebRequest http://localhost:8080/
docker compose down
```

完整顺序、验收项和故障排查见 `docs/STARTUP_CHECKLIST.md`。

## 协作约束

- 继续开发时按一个可验收切片推进，先测试后实现，并在结束前重新运行相关验证命令
- 不把数据库、Redis、密钥或远程账号写死在配置和代码中；本地配置使用环境变量
- 不修改现有模块职责，不随意重命名模板模块，不改动 AECP 需求文档
- 不新增未被请求的业务 API；若需求扩大，先更新计划和验收标准
