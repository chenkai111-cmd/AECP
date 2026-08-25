# AECP 一期到试点实施任务计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 按 PRD V2.1 和技术实现方案，把 AECP 从当前工程骨架推进到可演示、可测试、可灰度试点的一期 MVP，并把 P1 能力排入后续顺序。

**Architecture:** 采用模块化单体：React 18 + TypeScript + Vite 前端，Java 17 + Spring Boot 3 后端，按身份/项目/部件/文件/会议/任务/数模/系统管理划分业务边界。业务写入和异步副作用通过同库事务 + outbox_event + RabbitMQ 保证一致性；STEP 只在浏览器 Web Worker 中用 WebAssembly 解析，不建设服务端格式转换服务。

**Tech Stack:** React 18、TypeScript、Vite、Ant Design 5、Zustand、React Query、Three.js、@react-three/fiber、opencascade.js、Java 17、Spring Boot 3.4.x、Spring Security、当前仓库 MySQL 8.4 基线、Redis 7、RabbitMQ、MinIO/S3、Docker Compose、Nginx、GitHub Actions 或 GitLab CI。

**Spec:** \`WIKI/AECP_PRD_V2.1.md（飞机-发动机协同研发平台PRD，含STEP数模方案+EPICCA API双模式导入）.md\`；\`WIKI/AECP技术实现方案（已整合6条反馈） (13).md\`

## Global Constraints

- 一期发布门槛固定为“会议 → 任务 → 文件 → 部件追溯”，不把 CR、即时消息/话题、里程碑甘特图、EPICCA API、CATIA/IGES、SSO 纳入 MVP 发布门槛。
- 当前仓库和 \`AGENTS.md\` 按 MySQL 8.4 + Redis 验证；技术方案写 PostgreSQL 15。任务 00 必须冻结数据库基线，未冻结前不得编写业务迁移和查询。
- 业务数据永久保留并软删除；审计日志保留 180 天，清理前备份并保留批次摘要。
- 所有业务仓储查询必须带项目上下文；项目成员默认可查看项目内全部文件和 STEP 数模，角色只限制上传、下载、删除、基线、批注、配置等操作。
- 所有异步副作用采用“业务数据 + outbox_event 同库事务”；消费者按 idempotency_key 去重，失败重试超过 5 次进入 FAILED 并告警。
- STEP 一期只接受 \`.step/.stp\`，解析在浏览器 Web Worker 中完成；解析失败不能影响原始文件下载。
- 不把数据库、Redis、对象存储、消息队列、账号密码写死在代码中；所有环境通过 \`.env.example\` 和运行时变量配置。
- 每个任务完成前必须有对应测试、日志或页面验收证据；不能用静态代码阅读代替运行验证。
- 每个任务结束形成一个可审查提交，提交前至少运行该任务的最小测试和受影响模块回归测试。

## 交付分段与依赖

| 阶段 | 任务 | 目标 | 进入下一阶段的门 |
|---|---:|---|---|
| A 基线 | 00–04 | 范围、技术栈、工程、环境、CI、数据基线 | 可重复启动，迁移可回放，前后端构建通过 |
| B 基础能力 | 05–12 | 身份、权限、项目、部件、文件、消息一致性 | 用户可安全进入项目，文件能上传/下载/审计 |
| C P0 主链路 | 13–18 | 会议、任务、文件交付、部件追溯 | MVP 主链路 E2E 通过 |
| D 数模与 P1 | 19–26 | STEP 查看器、P1 模块、管理后台 | STEP 样本和 P1 需求分别达到各自门槛 |
| E 发布 | 27–31 | 安全、测试、性能、恢复、UAT、试点 | 发布门禁全部通过 |

---

## Task 00：冻结需求、数据库与模块基线（阻塞门）

**前置：** 当前工程骨架、PRD V2.1、技术实现方案。

**交付物：**
- \`docs/ADR-001-baseline.md\`
- \`docs/PRD_TRACEABILITY.md\`
- \`docs/DEMO_DATA.md\`
- 数据库和中间件选型决策、P0/P1/Out-of-Scope 清单、试点样本清单。

**验收标准：**
- [ ] ADR 明确 MySQL 8.4 与 PostgreSQL 15 的取舍；默认沿用当前仓库 MySQL 8.4 时，列出 JSON、树查询、全文检索在 MySQL 上的实现约束。
- [ ] PRD 的 P0 需求、MVP 五条验收边界、P1 需求和排除项逐条映射到后续任务 ID，没有无归属需求。
- [ ] 试点至少准备：两组织、1 个项目、项目成员、部件树、会议、任务、文件和 3 组 STEP 样本；敏感数据使用脱敏样本。
- [ ] 评审记录明确“数据库基线、对象存储、队列、认证方式、试点项目负责人”五项决策，未决事项不得阻塞本地骨架任务之外的工作。

**验证证据：**
\`rg -n "REQ-|MVP|Out of Scope" docs/PRD_TRACEABILITY.md\`；ADR 评审记录。

## Task 01：建立 React 前端工程壳与路由骨架

**前置：** Task 00。

**文件边界：**
- Create: \`xiaou-frontend/package.json\`、\`xiaou-frontend/vite.config.ts\`、\`xiaou-frontend/src/app/\`
- Create: \`xiaou-frontend/src/pages/LoginPage.tsx\`、\`WorkspacePage.tsx\`、\`DashboardPage.tsx\`
- Modify: Nginx/Compose 静态资源配置，保留当前 Spring Boot 欢迎页作为后端 fallback。

**验收标准：**
- [ ] \`pnpm install --frozen-lockfile\` 成功，\`pnpm typecheck\` 和 \`pnpm build\` 成功。
- [ ] 路由至少包含 \`/login\`、\`/workspace\`、\`/dashboard\`、\`/meetings\`、\`/tasks\`、\`/files\`、\`/components\`、\`/models/:fileVersionId\`、\`/admin/audit\`。
- [ ] 未登录访问业务路由跳转登录页；刷新路由不返回 404；中文、PC 优先、响应式布局和加载/错误/空状态组件可复用。
- [ ] 当前欢迎页中的 PRD 主链路文案迁移到前端壳后，浏览器访问首页仍有明确产品定位和一期边界。

## Task 02：补齐本地开发/测试基础设施

**前置：** Task 00。

**文件边界：**
- Modify: \`docker-compose.yml\`、\`.env.example\`
- Create: \`docker/healthchecks/\`、\`docs/LOCAL_DEVELOPMENT.md\`

**验收标准：**
- [ ] Compose 提供数据库、Redis、RabbitMQ、MinIO/S3 兼容存储及健康检查；端口、账号、密码均来自环境变量。
- [ ] \`docker compose config\` 退出码为 0；\`docker compose up -d\` 后所有服务进入 healthy。
- [ ] 应用在空白工作区按文档启动，不需要手工改代码；禁止提交真实 \`.env\` 和密钥。
- [ ] 任一中间件不可用时，应用日志能指出依赖和健康状态，不打印密码或敏感文件内容。

## Task 03：建立 CI、代码质量和变更门禁

**前置：** Task 01、Task 02。

**文件边界：**
- Create: \`.github/workflows/ci.yml\` 或 \`.gitlab-ci.yml\`
- Modify: \`AGENTS.md\`、\`README.md\`
- Create: \`docs/CONTRIBUTING.md\`

**验收标准：**
- [ ] CI 至少执行后端 \`.\mvnw.cmd clean test\`、前端 \`pnpm typecheck\`、\`pnpm build\`、Compose 配置检查。
- [ ] CI 对格式、测试失败、类型错误和构建错误返回非零；禁止以跳过测试作为默认通过条件。
- [ ] Pull Request 检查项包含迁移、权限、审计、API 契约、页面验收和回滚影响。
- [ ] README 给出从零启动、测试、打包、HTTP 冒烟和停止环境的可复制命令。

## Task 04：建立数据库迁移、种子数据与领域边界

**前置：** Task 00、Task 02。

**文件边界：**
- Create: \`xiaou-starter/src/main/resources/db/migration/V1__aecp_baseline.sql\` 及后续版本迁移
- Create: \`xiaou-modules/xiaou-aecp-*/src/main/java/com/xiaou/aecp/*\`
- Create: \`docs/DATA_MODEL.md\`

**验收标准：**
- [ ] 迁移覆盖组织、用户、项目、成员、角色、职责矩阵、部件树、文件、文件版本、上传会话、会议、决议、任务、通知、outbox_event、审计、EPICCA 导入记录和 STEP 批注核心表。
- [ ] 新数据库可从零执行全部迁移；重复启动不会重复建表；迁移失败能定位版本和 SQL。
- [ ] 所有业务表具备创建/更新时间、软删除或等价保留字段；项目子表具备项目归属约束。
- [ ] 领域模块之间通过 service/API/事件边界交互，不直接跨模块访问对方 Mapper 或内部表。

## Task 05：实现账号、组织和登录安全

**前置：** Task 04。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-identity/\`
- Frontend: \`xiaou-frontend/src/pages/LoginPage.tsx\`
- Test: identity unit/integration tests。

**验收标准：**
- [ ] 支持两组织用户、部门、人员批量导入、账号启用/禁用和账号密码登录。
- [ ] 密码使用 bcrypt；连续失败 5 次锁定 30 分钟；禁用用户不能登录；登录事件写入审计。
- [ ] 登录成功返回短期访问令牌和可刷新会话；前端刷新后能恢复登录态，退出后令牌失效。
- [ ] 2FA 按 Task 00 的安全决策实现为可配置能力；SSO 不进入一期代码路径。

## Task 06：实现项目、成员、RBAC 与项目隔离

**前置：** Task 05。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-project/\`、\`xiaou-common-*/\`
- Frontend: \`xiaou-frontend/src/pages/WorkspacePage.tsx\`
- Test: authorization integration tests。

**验收标准：**
- [ ] 项目管理员可创建项目、配置密级/周期/双方组织、添加或移除成员、变更项目角色。
- [ ] 系统级角色和项目级角色支持功能权限与数据权限；接口、服务、仓储三层均校验项目上下文。
- [ ] 用户 A 无法读取、修改、下载用户 B 所属且未授权的项目数据；项目成员默认可读取本项目全部文件和 STEP。
- [ ] 权限拒绝返回统一错误码并写审计，不通过前端隐藏按钮代替后端鉴权。

## Task 07：实现审计、Trace ID、软删除和数据保留策略

**前置：** Task 05、Task 06。

**文件边界：**
- Backend: \`xiaou-common-web/src/main/java/.../TraceIdFilter.java\`
- Backend: \`xiaou-modules/xiaou-aecp-audit/\`
- Create: \`docs/AUDIT_RETENTION.md\`

**验收标准：**
- [ ] 请求、异步消息和文件任务都有 trace_id，并在响应头、日志和审计记录中可关联。
- [ ] 登录、文件上传/下载/查看、权限变更、任务转派、部件修改和系统配置变更均写审计。
- [ ] 业务删除只设置软删除；审计角色可查询但不能修改或删除审计记录。
- [ ] 审计清理任务只清理超过 180 天的记录，清理前备份并保留批次摘要；单元测试覆盖边界日期和重复执行。

## Task 08：实现系统部件树和双方职责矩阵

**前置：** Task 06、Task 07。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-component/\`
- Frontend: \`xiaou-frontend/src/pages/ComponentTreePage.tsx\`
- Create: \`docs/templates/component-tree-template.xlsx\`

**验收标准：**
- [ ] 支持系统→子系统→部件→零件的多级树，节点可增删改查、排序、批量导入并校验唯一编号和父子关系。
- [ ] 部件属性包含编号、名称、型号、阶段、状态、技术参数和变更记录。
- [ ] 每个部件可配置商飞主责/配合/审批与商发主责/配合/审批，并记录负责人变更历史。
- [ ] 部件导入错误逐行返回；超过 500 个异步处理，超过 2000 个明确拒绝；树查询支持缓存失效测试。

## Task 09：建立 S3/MinIO 文件对象与元数据抽象

**前置：** Task 02、Task 06、Task 07。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-file/\`
- Create: \`xiaou-common-storage/\`
- Create: \`docs/STORAGE_CONTRACT.md\`

**验收标准：**
- [ ] 业务代码只依赖 S3 兼容接口；本地 MinIO 可替换为另一 S3 兼容实现而不改业务层。
- [ ] 文件元数据包含项目、上传者、组织、文件类型、密级、关联部件、当前版本和软删除状态。
- [ ] 下载必须先通过项目成员和角色校验，再生成有效期 2 小时的预签名 URL；过期 URL 不能下载。
- [ ] 上传扩展名、文件签名、大小和病毒扫描结果均落库；扫描失败或不通过时原始对象不可被业务用户下载。

## Task 10：实现大文件分片、断点续传和版本管理

**前置：** Task 09。

**文件边界：**
- Backend: \`file_upload_session\` API/service
- Frontend: \`xiaou-frontend/src/features/files/upload/\`
- Test: chunk upload integration tests。

**验收标准：**
- [ ] 上传会话状态严格为 \`UPLOADING → COMPLETING → COMPLETED\`，支持分片重试、断点恢复、秒传和 MD5 校验。
- [ ] 只有对象合并成功且 MD5 通过后，才创建 file_version 并更新 current_version_id。
- [ ] 自动生成版本号，支持版本历史、回溯、基线标记和基线保护；非法版本转换返回明确错误。
- [ ] 2GB 文件测试不要求一次性读入应用内存；中断后重新上传能从已完成分片继续。

## Task 11：实现文件、数模和部件的自动关联

**前置：** Task 08、Task 10。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-file/matching/\`
- Frontend: \`xiaou-frontend/src/features/files/matching/\`
- Test: matching rule unit tests。

**验收标准：**
- [ ] 按部件编号、文件名规则、元数据标签三重匹配，输出高/中/低置信度和可解释命中原因。
- [ ] 高置信度自动确认；中置信度自动关联但标记待确认；低置信度进入人工关联队列。
- [ ] 用户可手动修正关联，修正前后均写审计，并能从部件页反向查看文件和 STEP。
- [ ] 通过样本集验证自动关联准确率，指标低于 80% 时产生告警或复盘事件，不得静默放行。

## Task 12：实现 Outbox、RabbitMQ 和通知基础设施

**前置：** Task 04、Task 02。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-event/\`、\`xiaou-modules/xiaou-aecp-notification/\`
- Frontend: \`xiaou-frontend/src/features/notifications/\`
- Test: outbox publisher/consumer integration tests。

**验收标准：**
- [ ] 业务写入和 outbox_event 在同一事务；事务未提交前不发送 RabbitMQ。
- [ ] 发布器支持 PENDING、PUBLISHED、FAILED、指数退避和超过 5 次告警；消费者用 idempotency_key 去重。
- [ ] 通知先落库，再通过 WebSocket 推送；断线后可补拉未读通知，前端按事件 ID 去重。
- [ ] 人为制造重复消息、消费者重启和 RabbitMQ 短暂不可用时，不重复创建任务，最终状态可恢复。

## Task 13：实现会议预约、议程、纪要和历史追溯

**前置：** Task 06、Task 08、Task 12。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-meeting/\`
- Frontend: \`xiaou-frontend/src/pages/MeetingsPage.tsx\`、\`MeetingDetailPage.tsx\`
- Test: meeting API and permission tests。

**验收标准：**
- [ ] 主持人可创建会议，设置类型、时间、地点/线上链接、参会人和描述；时间冲突有提示。
- [ ] 支持议程增删改、顺序调整、汇报人、时长、关联部件/文件；总时长不得超过会议时长。
- [ ] 支持纪要草稿、结构化决议、责任人、截止日期、优先级、关联部件和发布；发布后参会人收到通知。
- [ ] 支持按时间/类型/参会人检索历史会议，能看到纪要、决议、任务和闭环状态；取消会议不删除已分发任务。

## Task 14：实现会议决议到任务的幂等自动分发

**前置：** Task 08、Task 12、Task 13。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-task/dispatch/\`
- Create: \`docs/TASK_DISPATCH_RULES.md\`
- Test: dispatch rule and idempotency tests。

**验收标准：**
- [ ] 关联部件时优先匹配双方部件负责人；指定责任人时使用指定责任人；都没有时指派主持人并提示手动指定。
- [ ] 发布前显示责任人、任务标题、截止日期、优先级确认框，支持主持人调整和批量确认。
- [ ] 幂等键按 \`project + resolution + assignee + ruleVersion\` 生成，同一键只能创建一个任务。
- [ ] 重复发布、网络重试、消费者重试和并发发布都不重复建任务；任务来源能追溯到会议编号和决议 ID。

## Task 15：实现任务代办、已办、状态流转和催办

**前置：** Task 14。

**文件边界：**
- Backend: \`xiaou-modules/xiaou-aecp-task/\`
- Frontend: \`xiaou-frontend/src/pages/TaskInboxPage.tsx\`、\`TaskDetailPage.tsx\`
- Test: task lifecycle and scheduler tests。

**验收标准：**
- [ ] 我的代办按优先级/截止日期排序，已办支持时间、类型、项目、状态筛选和导出。
- [ ] 状态至少覆盖待处理、进行中、待审核、已完成、已超期、待重新指派；非法状态转换被拒绝。
- [ ] 责任人可更新进度、上传交付文件、添加协作人、转派或拒绝并填写原因；项目经理可催办。
- [ ] 到期前 3 天提醒，超期每日催办；账号禁用时自动转派备份负责人，无备份则通知项目经理。

## Task 16：实现会议/任务前端工作台和交互状态

**前置：** Task 13、Task 15。

**文件边界：**
- Frontend: \`xiaou-frontend/src/pages/DashboardPage.tsx\`、\`MeetingsPage.tsx\`、\`TaskInboxPage.tsx\`
- Frontend: \`xiaou-frontend/src/lib/api/\`、\`src/components/ui/\`

**验收标准：**
- [ ] 首页展示我的代办、今日到期、超期任务、近期会议和未读通知，并可直接跳转详情。
- [ ] 会议发布后的任务通知、任务状态变化和错误状态在页面内可见，刷新后状态与服务端一致。
- [ ] 页面覆盖 loading、empty、error、permission denied、retry 和提交中状态；表单字段校验与后端错误码一致。
- [ ] 键盘可操作、按钮有可读名称、移动宽度下不出现横向溢出；浏览器控制台无未处理异常。

## Task 17：实现文件、部件和交付追溯前端

**前置：** Task 08、Task 10、Task 11、Task 15。

**文件边界：**
- Frontend: \`xiaou-frontend/src/pages/FilesPage.tsx\`、\`ComponentDetailPage.tsx\`
- Frontend: \`xiaou-frontend/src/features/files/\`、\`src/features/components/\`

**验收标准：**
- [ ] 文件页支持上传进度、断点恢复、版本列表、基线、下载、筛选和关联待确认提示。
- [ ] 部件详情页展示属性、双方负责人、关联文件/STEP、任务、变更时间线，并能跳转到任务和查看器。
- [ ] 双方项目成员看到相同项目文件；无上传、删除、基线或批注权限时页面明确禁用并展示原因。
- [ ] 从任务交付文件可反查会议、决议、任务、文件版本和部件，链路至少在 UI 和 API 两侧都能展开。

## Task 18：完成 P0 主链路 E2E

**前置：** Task 06、Task 08、Task 11、Task 14、Task 15、Task 16、Task 17。

**文件边界：**
- Create: \`xiaou-frontend/e2e/mvp-core.spec.ts\`
- Create: \`xiaou-starter/src/test/.../MvpCoreIntegrationTest.java\`
- Modify: \`docs/PRD_TRACEABILITY.md\`

**验收标准：**
- [ ] 测试自动创建项目、导入成员和部件树、配置双方负责人。
- [ ] 测试主持人发布会议纪要和决议，系统只创建一次任务并通知责任人。
- [ ] 测试责任人处理任务并上传交付文件，文件版本、部件关联和任务状态可追溯。
- [ ] 测试普通项目成员可查看项目全部文件和 STEP；无权限用户不能执行受限操作；管理员能查询关键审计。
- [ ] 该 E2E 在空数据库和容器化依赖上可重复运行，失败截图、请求 trace_id 和服务端日志可定位。

## Task 19：STEP 查看器可行性 Spike 与真实性能基线

**前置：** Task 01、Task 09、Task 10、Task 00 的 STEP 样本。

**文件边界：**
- Create: \`xiaou-frontend/src/features/step-viewer/worker/\`
- Create: \`docs/STEP_PERFORMANCE_BASELINE.md\`
- Test: \`xiaou-frontend/e2e/step-samples.spec.ts\`

**验收标准：**
- [ ] 使用真实脱敏 STEP 样本覆盖不同文件大小、装配层级、零件数量、STEP 版本和损坏文件。
- [ ] Web Worker 能解析有效 STEP 并输出可渲染网格；主线程在解析期间保持可交互。
- [ ] 记录解析成功率、首屏可交互时间、完整解析时间、内存、三角形估计值和 FPS。
- [ ] 解析失败、超过 500MB、浏览器内存不足和超过目标耗时均给出降级提示，并保留原始文件下载。
- [ ] 基线未达到 PRD 指标时，必须在文档中记录样本、失败原因和“继续优化/限制试点范围”的明确决策。

## Task 20：实现 STEP 在线查看、剖切、测量和批注

**前置：** Task 19。

**文件边界：**
- Frontend: \`xiaou-frontend/src/pages/ModelViewerPage.tsx\`
- Frontend: \`xiaou-frontend/src/features/step-viewer/\`
- Backend: \`xiaou-modules/xiaou-aecp-step/\`

**验收标准：**
- [ ] 支持旋转、缩放、平移、多视角预设、平面剖切、截面查看、距离/角度/面积测量。
- [ ] 支持在模型表面添加圈点和文字批注，批注与 file_version 绑定，支持列表、回复、权限和审计。
- [ ] 项目成员可查看项目内所有 STEP；只有具备批注权限的角色可以添加/回复，不能删除他人批注。
- [ ] Web Worker 解析失败只回滚查看器状态，不删除原始文件或版本；查看器可重新打开或下载原始文件。
- [ ] 真实样本 E2E 通过，且性能基线达标后才把 \`step_viewer\` Feature Flag 打开。

## Task 21：实现 STEP 版本对比（P1）

**前置：** Task 20。

**验收标准：**
- [ ] 同一部件可选择两个 STEP 版本并加载解析结果，显示版本元数据和基线状态。
- [ ] 几何差异至少支持边界盒/顶点距离级别的差异高亮，明确标注算法限制。
- [ ] 对比失败不影响两个原始版本查看和下载；结果带 file_version 对应关系。
- [ ] 版本对比性能和内存结果加入 STEP 基线，不作为一期 P0 主链路阻塞项。

## Task 22：实现项目里程碑、进度和健康度看板（P1）

**前置：** Task 15、Task 17。

**验收标准：**
- [ ] 支持里程碑名称、计划日期、负责人、交付物和关联部件；变更可审计。
- [ ] 甘特图可按组织、专业、部件筛选，任务状态与里程碑状态来源于同一服务端数据。
- [ ] 健康度看板展示进度偏差、交付及时率、任务完成率、变更数量和风险事项，并标注统计口径。
- [ ] 指标查询不会绕过项目权限；空数据、数据延迟和计算失败有明确状态。

## Task 23：实现 EPICCA 历史文件导入（P1）

**前置：** Task 08、Task 09、Task 12。

**验收标准：**
- [ ] 项目管理员可上传 Excel 清单和 Word/PDF 历史技术协调单；一期固定文件导入，不实现 API、远程连接或实时同步。
- [ ] 导入任务异步执行，记录原文件、解析报告、成功/失败行、关联部件和人工修正入口。
- [ ] 重复导入使用导入批次和幂等键去重；解析失败不影响原始文件留存。
- [ ] 导入进度和结果通过通知/页面可见，API 连接配置和凭证不进入一期数据库。

## Task 24：实现 CR 变更请求（P1）

**前置：** Task 08、Task 11、Task 15。

**验收标准：**
- [ ] 支持 CR 发起、原因/内容/影响范围、双方接口工程师和项目经理审批。
- [ ] 自动分析关联部件、文件、STEP、任务，审批结果形成不可变更的状态历史。
- [ ] 审批通过后上传新版本、关联 CR、创建任务并进入已有 outbox/幂等链路。
- [ ] 重复审批、并发审批、权限不足和审批后撤回均有明确错误和审计；不纳入一期发布门槛。

## Task 25：实现项目即时消息、话题和通知中心（P1）

**前置：** Task 12、Task 06。

**验收标准：**
- [ ] 支持项目内一对一和群组消息、文字/图片/文件、@提及和平台对象分享。
- [ ] 支持话题标签、附件、引用对象和结论标记；消息和话题均受项目权限限制。
- [ ] 通知中心按系统/任务/审批/文件/@提及分类，未读数、已读和补拉行为稳定。
- [ ] WebSocket 断线重连、重复事件和消息顺序异常有测试；消息不影响 P0 任务分发一致性。

## Task 26：实现系统管理页面与运营配置（P0 基础 + P1 配置）

**前置：** Task 05、Task 06、Task 07、Task 12。

**验收标准：**
- [ ] 系统管理员可维护组织、角色、权限、版本号规则、文件类型限制、存储策略、通知模板和任务分发规则。
- [ ] 安全审计员只能查询和导出审计，不能改业务数据、组织和审计记录。
- [ ] 配置变更有版本、操作者、时间、前后值和审计；非法配置不能生效。
- [ ] EPICCA 导入入口、审计入口和安全策略入口按角色展示，后端权限测试覆盖 UI 之外的直接调用。

## Task 27：完成安全基线和敏感数据保护

**前置：** Task 06、Task 07、Task 09、Task 10、Task 26。

**验收标准：**
- [ ] 越权读写、跨项目下载、过期 URL、禁用账号、密码锁定、病毒文件和审计篡改测试全部通过。
- [ ] 上传执行扩展名、文件签名和病毒扫描；下载 URL 有效期 2 小时；日志不包含密码、凭证和原始敏感文件内容。
- [ ] 业务数据只能软删除；审计日志不可由应用角色更新/删除；清理任务有备份和恢复验证。
- [ ] 安全问题按阻断级别分级；P0/P1 高风险问题清零后才能进入 UAT。

## Task 28：完成契约、集成、E2E 和回归测试矩阵

**前置：** Task 18、Task 20、Task 23、Task 26、Task 27。

**验收标准：**
- [ ] 核心业务单元测试覆盖任务匹配、版本号、权限判断、STEP 扩展名/签名、审计清理，核心模块覆盖率达到 80%。
- [ ] 集成测试覆盖数据库事务、outbox 发布、RabbitMQ 重试、Redis 锁、对象存储分片合并；重试不重复建任务，断点续传可恢复。
- [ ] 契约测试覆盖任务、文件、STEP、导入报告 API 和 WebSocket 事件，Schema、错误码和字段稳定。
- [ ] E2E 覆盖 MVP 五条链路；所有失败有 trace_id、服务端日志和浏览器证据。

## Task 29：完成性能、可观测性和告警

**前置：** Task 28。

**验收标准：**
- [ ] 100 人并发操作、500 在线、2GB 分片上传、任务批量分发完成压测，记录 P95/P99、吞吐、内存、错误率。
- [ ] 指标至少覆盖 API、任务分发、Outbox/MQ、文件上传、STEP 解析、自动关联、权限安全和可用性。
- [ ] 配置阈值告警：API P95 连续 5 分钟 >500ms、任务失败率 >1%、Outbox pending 10 分钟或 FAILED>0、上传失败率 >3%、STEP 失败率 >5%。
- [ ] 监控面板、告警联系人和处理 Runbook 已配置；日志不泄露敏感信息。

## Task 30：完成备份、恢复、迁移和回滚演练

**前置：** Task 27、Task 29。

**验收标准：**
- [ ] 演练数据库故障、对象存储短暂不可用、RabbitMQ 积压和审计清理中断，业务数据无丢失。
- [ ] 恢复结果达到 RPO≤1 小时、RTO≤4 小时，演练记录包含时间、命令、失败点和补偿动作。
- [ ] 数据库迁移遵循 expand-contract；不直接删除旧列；回滚优先关闭 Feature Flag，不用删除业务数据回滚。
- [ ] 明确 \`mvp_meeting_task_file_component\`、\`step_viewer\`、\`epicca_file_import\` 的开关、默认值和回滚责任人。

## Task 31：UAT、单项目试点与灰度上线

**前置：** Task 18、Task 20、Task 27、Task 28、Task 29、Task 30。

**验收标准：**
- [ ] UAT 完成 PRD 五条 MVP 验收边界：项目初始化、会议到任务、任务到文件、文件到部件/STEP、审计与保留。
- [ ] 先内部环境，再 1 个项目 10–20 人试点；至少观察 4 周后再扩展到 3–5 个项目；每阶段完成权限抽样和数据一致性检查。
- [ ] 立即回滚条件可执行：系统不可用超过 30 分钟、跨项目越权、数据丢失、Outbox 持续失败、核心功能错误率 >5%。
- [ ] 试点上线前配置监控面板、告警联系人、回滚开关、数据责任人、支持群组和用户培训材料。
- [ ] 试点结束输出任务闭环率、STEP 解析失败率、自动关联准确率、活跃用户、核心功能错误率和用户反馈报告。

---

## 最终发布门禁

只有以下条件全部满足，才可以把版本标记为一期 MVP：

- [ ] \`.\mvnw.cmd clean test\`、前端 \`pnpm typecheck\`、\`pnpm build\`、CI 全部通过。
- [ ] \`docker compose config\` 通过，开发/测试/试点环境可重复启动。
- [ ] MVP E2E 通过，会议重复发布不重复建任务，文件交付可追溯到版本和部件。
- [ ] 项目成员全可见规则和角色操作限制通过越权测试。
- [ ] STEP 样本达到首屏可交互、完整解析和 FPS 指标；失败模型仍可下载原始文件。
- [ ] Outbox、消息重试、断点续传、数据库恢复、审计、病毒扫描、下载 URL 过期、密码锁定和备份恢复全部通过。
- [ ] 监控、告警、回滚开关和试点责任人已配置。
- [ ] 试点环境的人工依赖项已落实：双方组织人员数据、部件树标准、STEP 样本和试点项目。

## 明确不进入本计划一期发布门槛的能力

- PLM/PDM 深度 API、适航符合性验证、试验数据和试验台监控。
- 原生移动 App、多语言、CAD 参数化编辑。
- CATIA/IGES/3DXML 解析和服务端格式转换。
- EPICCA API、远程连接和实时同步。
- SSO、等保三级测评和正式生产部署形态。

这些能力在 MVP 稳定后单独立项，不得通过临时加需求插入 Task 13–20 的 P0 主链路。

## PRD 需求到任务追踪

| PRD 需求组 | 对应任务 | 主要验收证据 |
|---|---|---|
| REQ-MEET-001~005 会议管理 | 13、14、18 | 会议 API/权限测试、幂等分发测试、MVP E2E |
| REQ-ROLE-001~005 用户与职责 | 05、06、08、26 | 登录/权限/项目成员/职责矩阵测试 |
| REQ-TASK-001~005 任务管理 | 12、14、15、16、18 | 消费者去重、状态机、催办、代办页面、E2E |
| REQ-FILE-001~004 文件管理 | 09、10、11、17、18、27 | 分片恢复、版本、匹配、下载 URL、越权测试 |
| REQ-COMP-001~005 系统部件 | 08、11、17、18 | 树导入、负责人、关联、时间线和 E2E |
| REQ-CAD-001~003/005 STEP P0 | 19、20、18 | 真实样本、查看器交互、批注权限和 E2E |
| REQ-CAD-004 STEP 版本对比 P1 | 21 | 双版本差异、高亮和失败降级 |
| REQ-PM-001~004 项目管理 | 06、22 | 项目配置、里程碑、甘特图、健康度权限测试 |
| REQ-CR-001~003 变更请求 P1 | 24 | 审批状态机、影响分析、实施闭环 |
| REQ-COM-001~003 沟通与通知 | 12、25 | WebSocket 重连、未读补拉、对象分享 |
| REQ-SYS-001~003 系统管理 | 07、23、26、27 | 审计、导入报告、配置审计和安全基线 |
