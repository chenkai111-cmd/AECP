# AECP 脱敏演示数据与试点样本清单

- 数据集名称：AECP-DEMO-V1
- 建立日期：2026-08-25
- 用途：本地种子、契约测试、P0 主链路 E2E 和单项目试点准备
- 数据原则：所有名称、账号、编号、会议内容和文件元数据均为合成或脱敏值；不得导入真实身份证号、手机号、邮箱、涉密型号、真实 CAD 文件或生产凭据。

## 1. 固定标识和初始化顺序

所有种子数据使用固定业务标识，重复执行必须幂等。推荐初始化顺序：

1. 创建两套组织。
2. 创建演示账号和组织成员关系。
3. 创建项目和项目成员。
4. 创建部件树、双方负责人和职责矩阵。
5. 创建会议、决议、任务和文件元数据。
6. 注册 STEP 样本清单；实际二进制由 Task 19 的样本接收流程注入对象存储。

固定前缀如下：

| 类型 | 前缀 | 示例 |
|---|---|---|
| 组织 | ORG-DEMO- | ORG-DEMO-COMAC |
| 用户 | USR-DEMO- | USR-DEMO-PM |
| 项目 | PRJ-DEMO- | PRJ-DEMO-CJ1000A |
| 部件 | CMP-DEMO- | CMP-DEMO-ENG |
| 会议 | MTG-DEMO- | MTG-DEMO-001 |
| 任务 | TSK-DEMO- | TSK-DEMO-001 |
| 文件 | FIL-DEMO- | FIL-DEMO-001 |

## 2. 两个脱敏组织

| 组织 ID | 显示名称 | 组织类型 | 说明 |
|---|---|---|---|
| ORG-DEMO-COMAC | 商飞演示组织 A | 主机侧 | 合成名称，不代表真实组织数据 |
| ORG-DEMO-AECC | 商发演示组织 B | 发动机侧 | 合成名称，不代表真实组织数据 |

组织管理员使用 USR-DEMO-ADMIN-A 和 USR-DEMO-ADMIN-B。账号密码不得写入仓库；本地测试通过环境变量或测试 fixture 注入，默认密码只允许在临时环境使用。

## 3. 项目和成员

| 项目字段 | 值 |
|---|---|
| 项目 ID | PRJ-DEMO-CJ1000A |
| 项目名称 | CJ1000A 接口协同演示项目 |
| 项目密级 | INTERNAL-DEMO |
| 项目周期 | 2026-09-01 至 2026-12-31 |
| 试点项目负责人 | USR-DEMO-PM / 登录名 demo-pilot-pm |
| 试点状态 | PILOT-DRAFT |

| 用户 ID | 登录名 | 所属组织 | 项目角色 | 用途 |
|---|---|---|---|---|
| USR-DEMO-PM | demo-pilot-pm | ORG-DEMO-COMAC | 项目负责人/主持人 | 创建会议、确认决议、验收主链路 |
| USR-DEMO-ADMIN-A | demo-admin-a | ORG-DEMO-COMAC | 组织管理员 | 管理 A 侧成员 |
| USR-DEMO-ADMIN-B | demo-admin-b | ORG-DEMO-AECC | 组织管理员 | 管理 B 侧成员 |
| USR-DEMO-ENG-A | demo-engineer-a | ORG-DEMO-COMAC | A 侧工程师 | 处理接口任务和交付文件 |
| USR-DEMO-ENG-B | demo-engineer-b | ORG-DEMO-AECC | B 侧工程师 | 处理发动机侧任务和交付文件 |
| USR-DEMO-AUDITOR | demo-auditor | ORG-DEMO-COMAC | 审计查看者 | 验证关键操作和保留策略 |

这些账号是最小演示成员集，不代表真实组织架构；测试必须验证 A/B 侧成员均能按项目规则访问项目文件，同时不能访问其他项目。

## 4. 部件树和职责矩阵

项目 PRJ-DEMO-CJ1000A 使用以下 5 层以内的合成部件树：

| 部件 ID | 父部件 | 层级 | 编号 | 名称 | A 侧负责人 | B 侧负责人 |
|---|---|---:|---|---|---|---|
| CMP-DEMO-ENG | 无 | 0 | DEMO-ENG | 发动机接口系统 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |
| CMP-DEMO-IFACE | CMP-DEMO-ENG | 1 | DEMO-IFACE | 接口子系统 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |
| CMP-DEMO-MOUNT | CMP-DEMO-IFACE | 2 | DEMO-MOUNT | 安装节 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |
| CMP-DEMO-PIPE | CMP-DEMO-IFACE | 2 | DEMO-PIPE | 管路组件 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |
| CMP-DEMO-SENSOR | CMP-DEMO-IFACE | 2 | DEMO-SENSOR | 传感器接口 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |
| CMP-DEMO-FLANGE | CMP-DEMO-MOUNT | 3 | DEMO-FLANGE | 法兰连接 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |
| CMP-DEMO-BOLT | CMP-DEMO-FLANGE | 4 | DEMO-BOLT | 紧固件示例 | USR-DEMO-ENG-A | USR-DEMO-ENG-B |

职责矩阵最少包含三种关系：A 侧主责、B 侧配合；B 侧主责、A 侧配合；项目负责人审批。Task 08 的种子验收必须证明部件负责人变更会留下历史，并可被 Task 14 的任务分发使用。

## 5. 会议、决议、任务和文件

### 5.1 会议和决议

| 类型 | ID | 脱敏内容 |
|---|---|---|
| 会议 | MTG-DEMO-001 | 接口安装节协同例会；主持人 USR-DEMO-PM；状态 CONFIRMED |
| 议程 | AGD-DEMO-001 | 安装节接口尺寸确认；汇报人 USR-DEMO-ENG-A；20 分钟 |
| 决议 | RES-DEMO-001 | 完成合成接口装配文件复核并提交版本 V1 |
| 决议 | RES-DEMO-002 | 对管路组件样本补充 B 侧配合意见 |

会议时间使用固定的测试时间 2026-09-03T09:00:00+08:00，不得使用生产会议链接。重复确认 MTG-DEMO-001 时，决议 ID 和幂等键保持不变。

### 5.2 任务

| 任务 ID | 来源决议 | 标题 | 负责人 | 部件 | 状态 |
|---|---|---|---|---|---|
| TSK-DEMO-001 | RES-DEMO-001 | 复核安装节接口装配文件 | USR-DEMO-ENG-A | CMP-DEMO-MOUNT | TODO |
| TSK-DEMO-002 | RES-DEMO-002 | 补充管路组件配合意见 | USR-DEMO-ENG-B | CMP-DEMO-PIPE | TODO |

任务验收必须包括：重复消费不重复创建、负责人可更新状态、超期可触发催办、交付文件带回任务和部件关联。

### 5.3 文件元数据

| 文件 ID | 文件名 | 版本 | 关联部件 | 来源任务 | 文件状态 |
|---|---|---:|---|---|---|
| FIL-DEMO-001 | DEMO-MOUNT-INTERFACE-REVIEW.pdf | 1 | CMP-DEMO-MOUNT | TSK-DEMO-001 | SYNTHETIC |
| FIL-DEMO-002 | DEMO-PIPE-COORDINATION.xlsx | 1 | CMP-DEMO-PIPE | TSK-DEMO-002 | SYNTHETIC |
| FIL-DEMO-003 | DEMO-SENSOR-INTERFACE.step | 1 | CMP-DEMO-SENSOR | TSK-DEMO-002 | SAMPLE-MANIFEST |

以上文件名和元数据是合成样本。仓库不提交真实附件；Task 09/10 通过对象存储 fixture 注入小型文本/占位文件，Task 19 再注入 STEP 二进制。

## 6. 三组 STEP 样本清单

三组样本用于覆盖小、中、大文件和不同装配复杂度。样本必须移除真实项目名称、序列号、客户标识、人员信息、真实坐标系命名和任何可回溯生产信息。

| 样本组 | 建议对象 key | 目标大小 | 装配特征 | 脱敏要求 | 当前状态 |
|---|---|---:|---|---|---|
| STEP-DEMO-S | step/demo/small/assembly.step | 10–50 MB | 单一接口小装配，少于 100 个可见实体 | 使用程序生成几何或已批准脱敏件 | MANIFEST-READY |
| STEP-DEMO-M | step/demo/medium/assembly.step | 100–500 MB | 管路和安装节组合，含多层部件 | 删除真实编号并重写属性文本 | MANIFEST-READY |
| STEP-DEMO-L | step/demo/large/assembly.step | 500 MB–2 GB | 多层核心接口大装配，用于加载和剖切压力 | 仅保留性能所需几何，不保留生产元数据 | MANIFEST-READY |

当前仓库未发现可核验的 STEP 二进制文件，因此这里冻结的是脱敏样本接收清单和对象 key，不虚构文件已经上传或已经达到性能指标。Task 19 必须在样本二进制到位后记录真实文件大小、解析耗时、首屏耗时、内存峰值、旋转/剖切/测量响应和失败原因；未完成前不得宣称 STEP 性能通过。

## 7. 数据安全和可重复性约束

- 所有密码、对象存储密钥、RabbitMQ 凭据和真实试点账号只从环境变量或临时 secret 注入。
- 演示数据的组织、账号、项目、部件和业务文本可以进入本地种子；真实生产数据、真实人员数据和真实 CAD 文件禁止进入 Git。
- 文件 fixture 使用固定内容和固定业务 ID，重复执行采用 upsert/幂等键；不得用当前时间生成不可重复的验收数据。
- 业务数据按 ADR-001 永久保存；审计数据按 180 天保留，并通过 Task 07/30 验证删除和恢复边界。
- 任何脱敏样本进入试点前，由双方数据责任人确认元数据和二进制均不可回溯真实项目。

## 8. Task 00 验收清单

- [x] 已定义两套脱敏组织。
- [x] 已定义 1 个脱敏项目、项目负责人、成员和角色。
- [x] 已定义部件树、双方负责人和职责矩阵。
- [x] 已定义会议、议程、决议、任务和文件元数据。
- [x] 已定义 3 组 STEP 样本的大小档位、脱敏要求和接收路径。
- [ ] 真实 STEP 二进制已到位并完成性能基线；这是 Task 19 的外部依赖，不在 Task 00 静态文档中冒充完成。
- [ ] 真实试点实名负责人已确认；演示负责人 demo-pilot-pm 已冻结，不阻塞本地骨架。
