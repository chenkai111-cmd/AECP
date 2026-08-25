# 项目进度

> 本文件记录当前工程状态。每次完成一个可验收切片后更新，并以本地命令输出作为完成依据。

## 当前状态

- 阶段：工程初始化基线已完成，F02 认证会话功能已实现并完成分层验收。
- 当前进行中：暂无功能处于开发中，下一切片为 F03 组织成员管理。
- 业务范围：F00 首页、F01 前端演示会话和 F02 后端认证已完成；F03 及后续业务功能尚未开发。

## 已完成

- 已确定项目技术栈与模块边界：Java 17、Spring Boot 3.4.4、Maven Wrapper 3.9.9、MySQL 8.4、Redis 7.4、Docker Compose。
- 已保留现有模板模块职责、Maven 坐标、`com.xiaou` 包名和 `xiaou-*` 模块命名。
- 已建立工程运行与验收文档，启动顺序和故障排查以 `docs/STARTUP_CHECKLIST.md` 为准。
- 已完成启动清单记录的初始化验收：Java 17、Maven Wrapper、Docker Compose 配置、MySQL/Redis 健康状态、Maven 测试、多模块打包，以及 Spring Boot 根路径 HTTP 200。
- F00 首页契约已实现：`GET /` 返回 HTML 200，包含 `欢迎使用AECP` 与 `会议 → 任务 → 文件 → 部件追溯`，并由 MockMvc 与真实 HTTP 冒烟覆盖。
- F01 前端演示会话已保留并通过路由回归：登录后可访问受保护演示路由，退出后重定向到 `/login`。
- F02 后端认证会话已实现：BCrypt 校验配置的演示账号，Redis 保存带 TTL 的 opaque token，登录/退出接口完成 MockMvc、完整 Maven 和真实 Redis E2E 验证。

## 未完成

- F03 及后续组织、项目、会议、任务、文件和部件追溯业务尚未开发。

## 进行中

- 无业务功能处于开发中。

## 已知问题

- 当前 PowerShell 环境无法识别 `make` 命令，因此仓库约定的 `make check` 尚未执行成功。
- 后续功能的验收标准、接口和数据模型需要在对应切片启动前明确。

## 下一步

1. 激活并实现下一个功能项 F03，先明确成员管理的验收标准。
2. 按“数据库 → 后端 → 权限 → 前端 → 测试 → 端到端验证”的顺序实现一个可验收切片。
3. 保持每个切片的真实命令、结果和未解决问题记录在本文件中。
4. 评估并补齐 `make check` 的 Windows 可执行入口，或在项目规范中明确等价检查命令。

## 验证记录

| 日期 | 命令 | 结果 | 备注 |
| --- | --- | --- | --- |
| 2026-08-25 | `java -version`、`mvnw -version`、`docker compose config` | 通过 | 详见 `docs/STARTUP_CHECKLIST.md` |
| 2026-08-25 | `docker compose up -d`、`docker compose ps` | 通过 | MySQL、Redis 健康；验证后已停止 Compose |
| 2026-08-25 | `./mvnw clean test` | 通过 | 全 Reactor 模块测试通过 |
| 2026-08-25 | `./mvnw -pl xiaou-starter -am package -DskipTests` | 通过 | 多模块打包通过 |
| 2026-08-25 | `GET http://localhost:8080/` | 通过 | HTTP 200；验证后已停止应用 |
| 2026-08-25 | `.\mvnw.cmd -pl xiaou-starter -am -Dtest=IndexControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | F00 定向测试 1/1；先验证缺失欢迎文案导致 RED，再补齐首页文案后通过 |
| 2026-08-25 | `.\mvnw.cmd clean test` | 通过 | 全 Reactor 10 模块，xiaou-starter 2/2 测试通过 |
| 2026-08-25 | `.\mvnw.cmd -pl xiaou-starter -am package -DskipTests` | 通过 | xiaou-starter 多模块打包成功 |
| 2026-08-25 | `GET http://localhost:8080/` F00 首页文案冒烟 | 通过 | HTTP 200、text/html;charset=UTF-8，欢迎文案和主链路文案均存在；验证后停止应用 |
| 2026-08-25 | `.\mvnw.cmd clean test` | 通过 | 全 Reactor 10 模块，xiaou-starter 16/16 测试通过，F02 配置、服务、控制器和 Redis 测试均通过 |
| 2026-08-25 | `.\mvnw.cmd -pl xiaou-starter -am package -DskipTests` | 通过 | F02 应用多模块打包成功 |
| 2026-08-25 | `docker compose up -d`、`docker compose ps` | 通过 | MySQL、Redis 均为 healthy；E2E 后已执行 `docker compose down` |
| 2026-08-25 | `POST /api/v1/auth/login`、`POST /api/v1/auth/logout`、重复 logout | 通过 | 真实 Spring Boot + Redis：登录 200/token 非空，首次退出 invalidated=true，重复退出 invalidated=false |
| 2026-08-25 | `pnpm test:routes`、`pnpm typecheck`、`pnpm build` | 通过 | 使用工作区 Bundled Node/pnpm 重跑；前端路由 29/29 测试通过，类型检查和生产构建通过 |
| 2026-08-25 | `rg f02-local-pass-2026 logs` | 通过 | 测试密码未写入应用日志 |
| 2026-08-25 | `make check` | 未执行成功 | 当前 PowerShell 无 `make` 命令 |
