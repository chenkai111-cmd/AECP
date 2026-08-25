# 飞机-发动机协同研发平台（AECP）技术实现方案

> 本文档基于 AECP PRD V2.1 设计，冻结一期“会议→任务→文件→部件追溯”垂直闭环。数模一期仅支持STEP浏览器解析；EPICCA API、CATIA/IGES、正式部署环境、等保三级和SSO统一认证均为后续阶段能力。

---

## 一、整体架构设计

### 1.1 技术栈选型

| 层级 | 技术选型 | 选型理由 |
|------|----------|----------|
| 前端框架 | React 18 + TypeScript + Vite | 组件生态成熟，TypeScript 保障大型项目类型安全，Vite 构建快 |
| 状态管理 | Zustand（轻量）+ React Query（服务端状态） | 避免 Redux 样板代码，React Query 处理缓存/重试/失效 |
| UI 组件库 | Ant Design 5.x | B 端后台标准组件库，表格/表单/树组件完善 |
| 数模渲染 | Three.js + @react-three/fiber + opencascade.js（WASM） | Three.js负责WebGL渲染，opencascade.js一期仅解析STEP |
| 后端框架 | Java 17 + Spring Boot 3.x + Spring Security | 企业级稳定，生态完善，适合复杂权限和业务规则 |
| 数据库 | PostgreSQL 15（主库）+ Redis 7（缓存/会话/分布式锁） | PostgreSQL 支持 JSONB、树形查询（CTE）、全文检索 |
| 文件存储 | S3兼容抽象；开发/测试默认MinIO，正式存储形态后续确定 | 大文件分片上传、断点续传、预签名 URL；业务代码不绑定具体厂商 |
| 消息队列 | RabbitMQ（任务分发/文件导入/通知） | 可靠消息投递，支持延迟队列（任务催办） |
| 实时通信 | WebSocket（STOMP over SockJS） | 即时消息、通知推送、文件导入进度 |
| 搜索引擎 | PostgreSQL 全文检索（一期）/ Elasticsearch（二期） | 一期数据量小，PG 全文检索足够；二期可扩展 ES |
| 部署 | Docker Compose（开发/测试/试点基线）+ Nginx | 一期提供可复现运行环境；正式部署架构后续确定 |
| CI/CD | GitLab CI / GitHub Actions | 自动化构建测试部署 |

### 1.2 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端层（PC Web）                         │
│  React + AntD + Three.js + opencascade.js(WASM) + WebSocket    │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTPS / WSS
┌───────────────────────────────▼─────────────────────────────────┐
│                        网关层（Nginx）                            │
│              反向代理 / 负载均衡 / SSL 终止 / 静态资源            │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                      应用服务层（Spring Boot）                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ 认证授权  │ │ 项目管理  │ │ 会议管理  │ │ 任务引擎  │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ 文件管理  │ │ 部件管理  │ │ CR管理   │ │ 消息通知  │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐            │
│  │ 系统管理  │ │ 文件导入 │ │  WebSocket 服务      │            │
│  │          │ │ 服务     │ │                      │            │
│  └──────────┘ └──────────┘ └──────────────────────┘            │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                       中间件层                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │PostgreSQL│ │  Redis   │ │ RabbitMQ │ │  S3兼容存储     │  │
│  │  主数据库 │ │ 缓存/锁  │ │ 消息队列 │ │  对象存储        │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                    外部服务 / 微服务                               │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ STEP解析在浏览器 │  │ 文件导入服务 │  │  邮件/短信    │ │
│  │ （WebAssembly）  │  │ （异步任务）  │  │  通知服务     │ │
│  └──────────────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 核心设计原则

1. **模块化单体优先**：一期采用模块化单体（Modular Monolith），按业务域划分包结构，避免微服务过度拆分带来的分布式复杂度。STEP解析在浏览器端执行，不建设服务端数模转换微服务。
2. **读写分离思路**：列表查询使用 Redis 缓存 + 数据库只读副本，写入走主库。
3. **异步化**：大文件分片合并、文件自动关联、任务批量分发和EPICCA文件导入采用异步处理，通过消息队列解耦；STEP解析不进入服务端队列。
4. **软删除 + 审计**：所有业务数据软删除（deleted_at），关键操作记录审计日志。
5. **项目级数据隔离**：项目根实体显式带 `project_id`；子表通过父表外键和仓储层项目上下文校验归属，不接受客户端传入的项目范围。
6. **数据保留分层**：业务数据（文件、数模、任务、会议、消息、通知等）永久保留，软删除不物理删除；审计日志保留180天并由专用清理任务处理。等保三级和正式部署环境不纳入一期。

---

## 二、数据结构设计

### 2.1 核心数据表设计

#### 2.1.1 组织与用户

```sql
-- 组织（商飞/商发两套独立架构）
CREATE TABLE organization (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,          -- 组织名称
    type            VARCHAR(20) NOT NULL,           -- COMAC(商飞) / AECC_CAE(商发)
    parent_id       BIGINT REFERENCES organization(id),
    org_code        VARCHAR(50) UNIQUE,             -- 组织编码
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_org_parent ON organization(parent_id);

-- 用户
CREATE TABLE "user" (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL,
    employee_no     VARCHAR(50) NOT NULL UNIQUE,    -- 工号
    email           VARCHAR(100) UNIQUE,
    phone           VARCHAR(20),
    org_id          BIGINT NOT NULL REFERENCES organization(id),
    department_id   BIGINT REFERENCES organization(id),
    position        VARCHAR(50),                      -- 岗位
    password_hash   VARCHAR(255) NOT NULL,           -- bcrypt
    status          VARCHAR(20) DEFAULT 'ACTIVE',    -- ACTIVE/DISABLED/LOCKED
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_user_org ON "user"(org_id);

-- 系统级角色
CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) UNIQUE NOT NULL,         -- SUPER_ADMIN/SYS_ADMIN/SECURITY_AUDITOR
    name        VARCHAR(50) NOT NULL,
    permissions JSONB NOT NULL DEFAULT '[]',          -- 权限码列表
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 用户-系统角色关联
CREATE TABLE user_sys_role (
    user_id     BIGINT NOT NULL REFERENCES "user"(id),
    role_id     BIGINT NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);
```

#### 2.1.2 项目与成员

```sql
-- 项目
CREATE TABLE project (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(50) NOT NULL UNIQUE,      -- 项目缩写，如 CJ1000A
    security_level  VARCHAR(20) DEFAULT 'INTERNAL',   -- INTERNAL/SECRET/CONFIDENTIAL
    status          VARCHAR(20) DEFAULT 'ACTIVE',      -- ACTIVE/ARCHIVED
    start_date      DATE,
    end_date        DATE,
    description     TEXT,
    created_by      BIGINT REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 项目级角色（可自定义）
CREATE TABLE project_role (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES project(id),
    code        VARCHAR(50) NOT NULL,                  -- PM/COMPONENT_OWNER/INTERFACE_ENG/ENGINEER/GUEST
    name        VARCHAR(50) NOT NULL,
    permissions JSONB NOT NULL DEFAULT '[]',
    is_builtin  BOOLEAN DEFAULT FALSE,                   -- 内置角色不可删除
    UNIQUE(project_id, code)
);

-- 项目成员
CREATE TABLE project_member (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES project(id),
    user_id     BIGINT NOT NULL REFERENCES "user"(id),
    role_id     BIGINT NOT NULL REFERENCES project_role(id),
    joined_at   TIMESTAMPTZ DEFAULT NOW(),
    joined_by   BIGINT REFERENCES "user"(id),
    UNIQUE(project_id, user_id)
);
CREATE INDEX idx_pm_project ON project_member(project_id);
```

#### 2.1.3 系统部件（树形结构）

```sql
-- 系统部件（邻接表 + path 字段，支持高效子树查询）
CREATE TABLE component (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id),
    code            VARCHAR(50) NOT NULL,               -- 部件编号，项目内唯一
    name            VARCHAR(100) NOT NULL,
    parent_id       BIGINT REFERENCES component(id),
    path            VARCHAR(500) NOT NULL,              -- 物化路径，如 /1/5/12/，支持子树查询
    depth           INT NOT NULL DEFAULT 1,              -- 层级深度
    type            VARCHAR(20) NOT NULL,                -- SYSTEM/SUBSYSTEM/COMPONENT/PART
    owner_side      VARCHAR(20) NOT NULL,                -- COMAC/AECC_CAE/BOTH
    phase           VARCHAR(30) DEFAULT 'SCHEME',        -- REQUIREMENT/SCHEME/DETAIL_DESIGN/PROTOTYPE/TEST/CERTIFICATION/DELIVERY
    tech_status     VARCHAR(20) DEFAULT 'NOT_STARTED',   -- NOT_STARTED/DESIGNING/REVIEWED/BASELINED/FROZEN
    sort_order      INT DEFAULT 0,
    created_by      BIGINT REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,                          -- 软删除
    UNIQUE(project_id, code)
);
CREATE INDEX idx_comp_project ON component(project_id);
CREATE INDEX idx_comp_parent ON component(parent_id);
CREATE INDEX idx_comp_path ON component(path);
CREATE INDEX idx_comp_owner_side ON component(owner_side);

-- 部件负责人
CREATE TABLE component_owner (
    id              BIGSERIAL PRIMARY KEY,
    component_id    BIGINT NOT NULL REFERENCES component(id),
    user_id         BIGINT NOT NULL REFERENCES "user"(id),
    side            VARCHAR(20) NOT NULL,                 -- COMAC/AECC_CAE
    role            VARCHAR(20) NOT NULL DEFAULT 'PRIMARY', -- PRIMARY/BACKUP
    effective_date  DATE NOT NULL DEFAULT CURRENT_DATE,
    created_by      BIGINT REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_co_component ON component_owner(component_id);
CREATE INDEX idx_co_user ON component_owner(user_id);

-- 职责矩阵
CREATE TABLE responsibility_matrix (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id),
    component_id    BIGINT NOT NULL REFERENCES component(id),
    duty_type       VARCHAR(30) NOT NULL,                 -- DESIGN_LEAD/DESIGN_SUPPORT/APPROVAL/VERIFICATION/DELIVERY
    responsible_side VARCHAR(20) NOT NULL,                -- COMAC/AECC_CAE/BOTH/UNASSIGNED
    created_by      BIGINT REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(project_id, component_id, duty_type)
);

-- 部件技术参数（JSONB 存储，版本化）
CREATE TABLE component_param (
    id              BIGSERIAL PRIMARY KEY,
    component_id    BIGINT NOT NULL REFERENCES component(id),
    version         INT NOT NULL,
    params          JSONB NOT NULL DEFAULT '{}',           -- 键值对参数
    change_note     VARCHAR(500),
    created_by      BIGINT REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(component_id, version)
);
```

#### 2.1.4 文件与版本

```sql
-- 文件（元数据）
CREATE TABLE file (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT NOT NULL REFERENCES project(id),
    name                VARCHAR(255) NOT NULL,             -- 原始文件名
    category            VARCHAR(30) NOT NULL,               -- ICD/DESIGN_REPORT/TEST_DATA/MODEL/PARAM_TABLE/OTHER
    security_level      VARCHAR(20) DEFAULT 'INTERNAL',
    upload_side         VARCHAR(20) NOT NULL,               -- COMAC/AECC_CAE（上传方，仅筛选统计用）
    folder_id           BIGINT,
    current_version_id  BIGINT,
    created_by          BIGINT NOT NULL REFERENCES "user"(id),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);
CREATE INDEX idx_file_project ON file(project_id);
CREATE INDEX idx_file_folder ON file(folder_id);

-- 文件夹
CREATE TABLE folder (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES project(id),
    name        VARCHAR(100) NOT NULL,
    parent_id   BIGINT REFERENCES folder(id),
    path        VARCHAR(500) NOT NULL,
    created_by  BIGINT REFERENCES "user"(id),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(project_id, parent_id, name)
);

-- 文件版本
CREATE TABLE file_version (
    id                  BIGSERIAL PRIMARY KEY,
    file_id             BIGINT NOT NULL REFERENCES file(id),
    version_no          VARCHAR(20) NOT NULL,               -- V1.0 / V1.1 / V2.0
    storage_path        VARCHAR(500) NOT NULL,              -- OSS/MinIO 对象路径
    file_size           BIGINT NOT NULL,                     -- 字节
    md5                 VARCHAR(32) NOT NULL,
    file_ext            VARCHAR(10) NOT NULL,                -- 一期数模仅允许 step/stp
    change_note         VARCHAR(500) NOT NULL,
    uploaded_by         BIGINT NOT NULL REFERENCES "user"(id),
    uploaded_at         TIMESTAMPTZ DEFAULT NOW(),
    is_baseline         BOOLEAN DEFAULT FALSE,
    row_version         BIGINT NOT NULL DEFAULT 0,           -- 乐观锁
    deleted_at          TIMESTAMPTZ
);
CREATE INDEX idx_fv_file ON file_version(file_id);
CREATE INDEX idx_fv_md5 ON file_version(md5);
CREATE UNIQUE INDEX uq_fv_file_version ON file_version(file_id, version_no);
CREATE UNIQUE INDEX uq_fv_id_file ON file_version(id, file_id);

-- 文件创建顺序：folder 和 file_version 均创建后再补充循环外键
ALTER TABLE file
    ADD CONSTRAINT fk_file_folder FOREIGN KEY (folder_id) REFERENCES folder(id),
    ADD CONSTRAINT fk_file_current_version FOREIGN KEY (current_version_id, id) REFERENCES file_version(id, file_id) DEFERRABLE INITIALLY DEFERRED;

-- 分片上传会话：服务端保存进度，不能只依赖浏览器 localStorage
CREATE TABLE file_upload_session (
    id                  UUID PRIMARY KEY,
    project_id          BIGINT NOT NULL REFERENCES project(id),
    file_name           VARCHAR(255) NOT NULL,
    file_ext            VARCHAR(10) NOT NULL,
    file_size           BIGINT NOT NULL,
    md5                 VARCHAR(32) NOT NULL,
    chunk_size          INT NOT NULL DEFAULT 5242880,
    total_chunks        INT NOT NULL,
    uploaded_chunks     JSONB NOT NULL DEFAULT '[]',
    status              VARCHAR(20) NOT NULL DEFAULT 'UPLOADING', -- UPLOADING/COMPLETING/COMPLETED/ABORTED/EXPIRED
    created_by          BIGINT NOT NULL REFERENCES "user"(id),
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_upload_session_project ON file_upload_session(project_id, status);

-- 文件-部件关联（多对多）
CREATE TABLE file_component_rel (
    id                  BIGSERIAL PRIMARY KEY,
    file_id             BIGINT NOT NULL REFERENCES file(id),
    component_id        BIGINT NOT NULL REFERENCES component(id),
    match_confidence    VARCHAR(10) NOT NULL,                -- HIGH/MEDIUM/LOW
    is_confirmed        BOOLEAN DEFAULT FALSE,
    confirm_deadline    TIMESTAMPTZ,                          -- 中置信度7天后自动确认
    created_by          BIGINT REFERENCES "user"(id),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(file_id, component_id)
);

-- 文件-数模关联（参数表与数模的关联）
CREATE TABLE file_cad_rel (
    id                  BIGSERIAL PRIMARY KEY,
    param_file_id       BIGINT NOT NULL REFERENCES file(id),  -- 参数表/文档
    cad_file_id         BIGINT NOT NULL REFERENCES file(id),  -- 数模文件
    component_id        BIGINT REFERENCES component(id),
    created_by          BIGINT REFERENCES "user"(id),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(param_file_id, cad_file_id)
);
```

#### 2.1.5 会议管理

```sql
-- 会议
CREATE TABLE meeting (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id),
    code            VARCHAR(50) NOT NULL UNIQUE,             -- MTG-CJDX-2026-0001
    title           VARCHAR(200) NOT NULL,
    type            VARCHAR(30) NOT NULL,                     -- WEEKLY/TECH_REVIEW/INTERFACE_COORD/CR_REVIEW/MILESTONE_REVIEW/OTHER
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    location        VARCHAR(200),
    online_url      VARCHAR(500),
    status          VARCHAR(20) DEFAULT 'DRAFT',              -- DRAFT/NOT_STARTED/IN_PROGRESS/ENDED/CANCELLED
    minutes_status  VARCHAR(20) DEFAULT 'DRAFT',              -- DRAFT/PUBLISHED
    host_id         BIGINT NOT NULL REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_meeting_project ON meeting(project_id);
CREATE INDEX idx_meeting_time ON meeting(start_time);

-- 会议参会人
CREATE TABLE meeting_attendee (
    id              BIGSERIAL PRIMARY KEY,
    meeting_id      BIGINT NOT NULL REFERENCES meeting(id),
    user_id         BIGINT NOT NULL REFERENCES "user"(id),
    role            VARCHAR(20) DEFAULT 'ATTENDEE',           -- HOST/RECORDER/ATTENDEE
    attendance_status VARCHAR(20) DEFAULT 'INVITED',           -- INVITED/ACCEPTED/DECLINED/ATTENDED/ABSENT
    UNIQUE(meeting_id, user_id)
);

-- 会议议程
CREATE TABLE meeting_agenda (
    id                  BIGSERIAL PRIMARY KEY,
    meeting_id          BIGINT NOT NULL REFERENCES meeting(id),
    topic               VARCHAR(200) NOT NULL,
    presenter_id        BIGINT REFERENCES "user"(id),
    duration_minutes    INT NOT NULL,
    related_component_id BIGINT REFERENCES component(id),
    sort_order          INT NOT NULL,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- 会议纪要
CREATE TABLE meeting_minutes (
    id          BIGSERIAL PRIMARY KEY,
    meeting_id  BIGINT NOT NULL UNIQUE REFERENCES meeting(id),
    content     JSONB NOT NULL DEFAULT '{}',                   -- 按议程分段的纪要内容
    published_at TIMESTAMPTZ,
    published_by BIGINT REFERENCES "user"(id),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 纪要修订历史：发布版本不可覆盖，修订生成新版本
CREATE TABLE meeting_minutes_revision (
    id                  BIGSERIAL PRIMARY KEY,
    meeting_minutes_id  BIGINT NOT NULL REFERENCES meeting_minutes(id),
    revision_no         INT NOT NULL,
    content             JSONB NOT NULL DEFAULT '{}',
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT/PUBLISHED/SUPERSEDED
    created_by          BIGINT NOT NULL REFERENCES "user"(id),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    UNIQUE(meeting_minutes_id, revision_no)
);

-- 会议决议
CREATE TABLE meeting_resolution (
    id                      BIGSERIAL PRIMARY KEY,
    meeting_id              BIGINT NOT NULL REFERENCES meeting(id),
    agenda_id               BIGINT REFERENCES meeting_agenda(id),
    content                 TEXT NOT NULL,
    responsible_user_ids    JSONB NOT NULL DEFAULT '[]',       -- 责任人ID数组
    deadline                DATE NOT NULL,
    priority                VARCHAR(10) DEFAULT 'MEDIUM',       -- HIGH/MEDIUM/LOW
    related_component_ids   JSONB DEFAULT '[]',
    created_at              TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_resolution_meeting ON meeting_resolution(meeting_id);

-- 一条决议可以拆分为多个任务
CREATE TABLE resolution_task (
    id              BIGSERIAL PRIMARY KEY,
    resolution_id   BIGINT NOT NULL REFERENCES meeting_resolution(id),
    task_id         BIGINT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(resolution_id, task_id)
);
```

#### 2.1.6 任务管理（自动分发引擎核心）

```sql
-- 任务
CREATE TABLE task (
    id                      BIGSERIAL PRIMARY KEY,
    project_id              BIGINT NOT NULL REFERENCES project(id),
    parent_task_id          BIGINT REFERENCES task(id),
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    source_type             VARCHAR(20) NOT NULL,               -- MEETING/CR/MILESTONE/MANUAL
    source_id               BIGINT,                              -- 来源对象ID（会议ID/CR ID/里程碑ID）
    idempotency_key         VARCHAR(100) NOT NULL UNIQUE,         -- 自动分发/重试幂等键
    assignee_id             BIGINT NOT NULL REFERENCES "user"(id),
    collaborator_ids        JSONB DEFAULT '[]',
    priority                VARCHAR(10) DEFAULT 'MEDIUM',
    deadline                DATE,
    status                  VARCHAR(20) DEFAULT 'PENDING',      -- PENDING/IN_PROGRESS/COMPLETED/OVERDUE/CLOSED/REJECTED
    progress                INT DEFAULT 0,                       -- 0/25/50/75/100
    progress_weight         NUMERIC(8,3) NOT NULL DEFAULT 1,     -- 子任务汇总权重
    related_component_ids   JSONB DEFAULT '[]',
    related_file_ids        JSONB DEFAULT '[]',
    deliverable_required    BOOLEAN DEFAULT TRUE,
    rejected_by             BIGINT REFERENCES "user"(id),       -- 拒绝人（任务被拒绝时记录）
    rejected_at             TIMESTAMPTZ,                          -- 拒绝时间
    rejected_reason         VARCHAR(500),                         -- 拒绝原因
    escalated_to            BIGINT REFERENCES "user"(id),       -- 拒绝后升级到的上级领导
    created_by              BIGINT NOT NULL REFERENCES "user"(id),
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    row_version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_task_project ON task(project_id);
CREATE INDEX idx_task_assignee ON task(assignee_id);
CREATE INDEX idx_task_status ON task(status);
CREATE INDEX idx_task_deadline ON task(deadline);
CREATE INDEX idx_task_escalated ON task(escalated_to);  -- 上级待重新分配的任务
ALTER TABLE resolution_task
    ADD CONSTRAINT fk_resolution_task_task FOREIGN KEY (task_id) REFERENCES task(id);

-- 任务进展记录
CREATE TABLE task_progress_log (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES task(id),
    progress    INT NOT NULL,
    note        TEXT,
    created_by  BIGINT NOT NULL REFERENCES "user"(id),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 任务讨论
CREATE TABLE task_comment (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES task(id),
    user_id     BIGINT NOT NULL REFERENCES "user"(id),
    content     TEXT NOT NULL,
    mention_ids JSONB DEFAULT '[]',                              -- @提及的用户ID
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 任务转派记录
CREATE TABLE task_reassignment (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT NOT NULL REFERENCES task(id),
    from_user_id    BIGINT NOT NULL REFERENCES "user"(id),
    to_user_id      BIGINT NOT NULL REFERENCES "user"(id),
    reason          VARCHAR(500),
    created_by      BIGINT NOT NULL REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 任务交付物
CREATE TABLE task_deliverable (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES task(id),
    file_version_id BIGINT NOT NULL REFERENCES file_version(id),
    uploaded_by BIGINT NOT NULL REFERENCES "user"(id),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 事务消息外发：业务事务提交后由发布器可靠投递到RabbitMQ
CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT REFERENCES project(id),
    event_type      VARCHAR(80) NOT NULL,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/PUBLISHED/FAILED
    attempt_count   INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_event(status, next_attempt_at);
```

#### 2.1.7 变更请求（CR）

```sql
-- 变更请求
CREATE TABLE change_request (
    id                      BIGSERIAL PRIMARY KEY,
    project_id              BIGINT NOT NULL REFERENCES project(id),
    code                    VARCHAR(50) NOT NULL UNIQUE,
    title                   VARCHAR(200) NOT NULL,
    type                    VARCHAR(30) NOT NULL,                -- DESIGN_CHANGE/PARAM_CHANGE/INTERFACE_CHANGE/OTHER
    reason                  TEXT NOT NULL,
    content                 TEXT NOT NULL,
    impact_analysis         TEXT,
    status                  VARCHAR(20) DEFAULT 'DRAFT',         -- DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/IMPLEMENTING/COMPLETED/CLOSED
    creator_id              BIGINT NOT NULL REFERENCES "user"(id),
    related_component_ids   JSONB DEFAULT '[]',
    related_file_ids        JSONB DEFAULT '[]',
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_cr_project ON change_request(project_id);

-- CR审批节点
CREATE TABLE cr_approval_node (
    id          BIGSERIAL PRIMARY KEY,
    cr_id       BIGINT NOT NULL REFERENCES change_request(id),
    node_type   VARCHAR(30) NOT NULL,                -- INTERFACE_ENG_COMAC/INTERFACE_ENG_AECC/PM_COMAC/PM_AECC
    approver_id BIGINT REFERENCES "user"(id),
    status      VARCHAR(20) DEFAULT 'PENDING',        -- PENDING/APPROVED/REJECTED
    opinion     TEXT,
    approved_at TIMESTAMPTZ,
    deadline    TIMESTAMPTZ,
    sort_order  INT NOT NULL
);
```

#### 2.1.8 里程碑

```sql
CREATE TABLE milestone (
    id                      BIGSERIAL PRIMARY KEY,
    project_id              BIGINT NOT NULL REFERENCES project(id),
    name                    VARCHAR(200) NOT NULL,
    phase                   VARCHAR(30),
    plan_start              DATE,
    plan_end                DATE NOT NULL,
    owner_id                BIGINT REFERENCES "user"(id),
    status                  VARCHAR(20) DEFAULT 'NOT_STARTED',   -- NOT_STARTED/IN_PROGRESS/COMPLETED/DELAYED
    related_component_ids   JSONB DEFAULT '[]',
    deliverable_desc        TEXT,
    task_id                 BIGINT,                                  -- 关联的交付任务
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);
```

#### 2.1.9 数模批注

```sql
CREATE TABLE cad_annotation (
    id              BIGSERIAL PRIMARY KEY,
    file_version_id BIGINT NOT NULL REFERENCES file_version(id),
    user_id         BIGINT NOT NULL REFERENCES "user"(id),
    type            VARCHAR(20) NOT NULL,             -- CIRCLE/TEXT/ARROW
    content         TEXT,
    position_xyz    JSONB NOT NULL,                    -- {"x":0,"y":0,"z":0}
    camera_params   JSONB NOT NULL,                    -- 相机视角参数，用于定位
    parent_id       BIGINT REFERENCES cad_annotation(id), -- 回复批注
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_anno_version ON cad_annotation(file_version_id);
```

#### 2.1.10 消息与通知

```sql
-- 会话（1对1或群组）
CREATE TABLE conversation (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT REFERENCES project(id),
    type        VARCHAR(20) NOT NULL,                 -- DIRECT/GROUP
    name        VARCHAR(200),                          -- 群组名称
    is_default  BOOLEAN DEFAULT FALSE,                 -- 项目全员群
    created_by  BIGINT REFERENCES "user"(id),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 会话成员
CREATE TABLE conversation_member (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversation(id),
    user_id         BIGINT NOT NULL REFERENCES "user"(id),
    last_read_msg_id BIGINT,
    joined_at       TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(conversation_id, user_id)
);

-- 即时消息
CREATE TABLE message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversation(id),
    sender_id       BIGINT NOT NULL REFERENCES "user"(id),
    content         TEXT,
    msg_type        VARCHAR(20) DEFAULT 'TEXT',        -- TEXT/IMAGE/FILE/SYSTEM/SHARE_OBJECT
    share_obj_type  VARCHAR(30),                        -- FILE/COMPONENT/CR/TASK/MEETING
    share_obj_id    BIGINT,
    is_recalled     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_msg_conv ON message(conversation_id);

-- 通知
CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES "user"(id),
    type            VARCHAR(30) NOT NULL,               -- SYSTEM/TASK/APPROVAL/FILE/MENTION/MEETING
    title           VARCHAR(200) NOT NULL,
    content         TEXT,
    related_obj_type VARCHAR(30),
    related_obj_id    BIGINT,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_notif_user ON notification(user_id, is_read);
```

#### 2.1.11 审计日志

```sql
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT REFERENCES project(id),
    user_id     BIGINT REFERENCES "user"(id),
    user_name   VARCHAR(50),
    action      VARCHAR(50) NOT NULL,                   -- LOGIN/FILE_UPLOAD/FILE_DOWNLOAD/PERMISSION_CHANGE/CR_APPROVE等
    target_type VARCHAR(30),
    target_id   BIGINT,
    detail      JSONB,
    ip_address  VARCHAR(50),
    user_agent  VARCHAR(500),
    trace_id    VARCHAR(64),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_time ON audit_log(created_at);
```

#### 2.1.12 EPICCA 导入记录

```sql
CREATE TABLE epicca_import (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id),
    import_mode     VARCHAR(20) NOT NULL DEFAULT 'FILE', -- 一期固定FILE，API为二期
    total_count     INT DEFAULT 0,
    success_count   INT DEFAULT 0,
    fail_count      INT DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PENDING',       -- PENDING/PROCESSING/COMPLETED/FAILED
    error_report    JSONB,                                -- 失败明细
    created_by      BIGINT NOT NULL REFERENCES "user"(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

-- EPICCA导入的历史参考文档
CREATE TABLE epicca_reference_doc (
    id              BIGSERIAL PRIMARY KEY,
    import_id       BIGINT NOT NULL REFERENCES epicca_import(id),
    project_id      BIGINT NOT NULL REFERENCES project(id),
    coord_no        VARCHAR(100) NOT NULL,               -- 协调单编号
    title           VARCHAR(500),
    coord_date      DATE,
    initiator       VARCHAR(100),                         -- 发起方
    component_id    BIGINT REFERENCES component(id),
    file_id         BIGINT REFERENCES file(id),            -- 关联的文件
    content         TEXT,                                   -- 解析后的内容
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(project_id, coord_no)
);
```

### 2.2 数据库设计要点

1. **树形结构**：部件树和文件夹采用「邻接表 + 物化路径（path）」双模式。邻接表便于增删改，path 字段便于一次性查询子树（`WHERE path LIKE '/1/5/%'`），避免递归 CTE 的性能问题。
2. **JSONB 字段**：关联 ID 数组（related_component_ids、collaborator_ids 等）使用 JSONB 存储，避免大量关联表。查询时用 `?` 操作符判断包含关系。PG 12+ 支持 JSONB 索引。
3. **软删除**：业务表带 deleted_at，查询时默认过滤；业务数据永久保留。审计日志不允许业务接口修改，按180天策略由专用任务清理。
4. **项目隔离**：项目根实体带 project_id；子表通过父表外键和仓储层项目上下文校验归属，禁止仅依赖字符串拼接或客户端传入的 project_id。
5. **乐观锁**：文件版本、任务、部件属性等可编辑表带 row_version，更新使用 `WHERE id=? AND row_version=?`，冲突返回409并要求前端刷新。
6. **STEP解析状态**：一期不做服务端数模转换，file_version只记录原始STEP；解析进度和失败原因通过前端事件、服务端审计和可观测指标记录。
7. **秒传不跨项目**：MD5 秒传仅在同一项目内生效。秒传检查时通过 `file_version JOIN file ON file_version.file_id = file.id` 联合判断 `file.project_id + file_version.md5`，不同项目即使 MD5 相同也需重新上传，确保项目间数据隔离。
8. **任务拒绝与升级**：task 表的 rejected_by/rejected_at/rejected_reason 记录拒绝信息，escalated_to 记录拒绝后升级到的上级领导。上级重新分配后更新 assignee_id 并将状态重置为 PENDING，同时在 task_reassignment 表中记录转派历史。

### 2.3 一期数据访问与迁移约束

| 约束 | 实现方式 | 验证方式 |
|------|----------|----------|
| 项目范围 | 登录后选择项目写入服务端上下文；所有仓储查询必须带根实体项目条件，子表查询必须通过父表JOIN校验 | 越权集成测试：用户A无法读取/修改项目B数据 |
| 项目成员可见性 | 项目成员默认可读取项目内全部文件和STEP数模；角色只限制上传、删除、基线、批注等操作 | RBAC矩阵测试 + 下载URL权限测试 |
| 数据永久保留 | 业务表只软删除；对象存储删除接口仅供受控运维使用并记录审计 | 删除后查询、恢复和审计回放测试 |
| 审计180天 | 定时清理任务按created_at分批处理，先备份、再删除，清理批次写入运维日志 | 到期清理演练 + 恢复演练 |
| Schema变更 | 使用版本化迁移脚本；新增字段先可空，再回填，再加约束，禁止直接破坏旧字段 | Migration dry-run + 回滚演练 |

---

## 三、主要页面与组件设计

### 3.1 页面路由结构

```
/login                          登录页
/                               首页（个人工作台）
/projects                       项目列表
/projects/:id                   项目空间（布局容器，左侧导航）
  ├── /overview                 项目概览/健康度看板
  ├── /meetings                 会议管理
  │   ├── /list                 会议列表
  │   ├── /calendar             日历视图
  │   ├── /create               创建会议
  │   └── /:meetingId           会议详情（概览/议程/纪要/任务/附件）
  ├── /tasks                    任务管理
  │   ├── /todo                 我的代办
  │   ├── /done                 我的已办
  │   ├── /all                  项目全部任务（项目经理）
  │   └── /:taskId              任务详情
  ├── /files                    文件管理
  │   ├── /list                 文件列表（文件夹树+文件表格）
  │   ├── /upload               上传（弹窗/拖拽区）
  │   └── /:fileId              文件详情（概览/版本历史/关联任务/变更记录/操作日志）
  ├── /components               系统部件管理
  │   ├── /tree                 部件树浏览
  │   └── /:componentId         部件详情（概览/属性参数/负责人/关联文件/关联数模/变更记录/相关任务/相关CR）
  ├── /cad-viewer/:fileVersionId  数模查看器（全屏独立页）
  ├── /project                  项目管理
  │   ├── /milestones           里程碑管理（甘特图）
  │   ├── /health               健康度看板
  │   └── /settings             项目设置（成员/角色/职责矩阵）
  ├── /crs                      变更请求管理
  │   ├── /list                 CR列表
  │   ├── /create               创建CR
  │   └── /:crId                CR详情
  ├── /communication            沟通交流
  │   ├── /messages             即时消息
  │   ├── /topics               话题讨论区
  │   └── /notifications        通知中心
  └── /system                   系统管理（项目管理员）
      ├── /audit                审计日志
      ├── /config               系统配置
      └── /epicca-import        EPICCA数据导入
```

### 3.2 核心页面与组件职责

#### 3.2.1 布局与通用组件

| 组件名 | 职责 | 关键 props/state |
|--------|------|-----------------|
| `AppLayout` | 全局布局，顶部导航栏+内容区 | 当前用户、未读通知数 |
| `ProjectLayout` | 项目空间布局，左侧导航+右侧内容 | 当前项目、用户角色权限 |
| `SideNav` | 项目左侧导航菜单，根据角色显示不同菜单项 | 菜单配置、当前路由 |
| `TopBar` | 顶部导航，项目切换、搜索、通知、用户菜单 | 项目列表、通知未读数 |
| `PermissionGuard` | 权限守卫组件，无权限时显示占位 | permissionCode、fallback |
| `StatusTag` | 通用状态标签，统一颜色映射 | status、type |
| `PriorityTag` | 优先级标签（高/中/低，红/橙/灰） | priority |
| `UserAvatar` | 用户头像+姓名+组织标签 | userId、size |
| `DateRangePicker` | 日期范围选择器 | value、onChange |
| `EmptyState` | 空状态占位组件 | icon、title、action |
| `LoadingState` | 加载状态 | size、text |
| `ErrorBoundary` | 错误边界，捕获组件渲染异常 | - |

#### 3.2.2 个人工作台（首页）

| 组件名 | 职责 |
|--------|------|
| `DashboardPage` | 首页容器，聚合各模块数据 |
| `TodoSummaryCard` | 代办统计卡片（待处理/今日到期/已超期），点击跳转代办列表 |
| `TodoListPreview` | 最近5条代办任务预览，支持快速标记完成 |
| `RecentFilesCard` | 最近上传/更新的文件列表 |
| `MyProjectsCard` | 我参与的项目列表，显示各项目未读数 |
| `MyComponentsCard` | 我负责的部件列表，显示最新文件/数模/待办 |
| `ActivityFeed` | 与我相关的近期动态时间线 |

#### 3.2.3 会议管理

| 组件名 | 职责 |
|--------|------|
| `MeetingListPage` | 会议列表页，列表/日历视图切换，筛选搜索 |
| `MeetingTable` | 会议列表表格（主题/类型/时间/地点/参会人数/状态/操作） |
| `MeetingCalendar` | 日历视图，按天/周/月展示会议 |
| `MeetingCreateModal` | 创建会议弹窗（基本信息+参会人选择+议程） |
| `MeetingDetailPage` | 会议详情页容器，标签页切换 |
| `MeetingOverview` | 会议概览（基本信息+参会人列表+状态） |
| `MeetingAgendaEditor` | 议程编辑器（增删改+拖拽排序+关联部件/文件） |
| `MeetingMinutesEditor` | 纪要编辑器（按议程分段+富文本编辑） |
| `ResolutionList` | 决议列表（添加/编辑/删除决议，显示关联任务状态） |
| `ResolutionForm` | 决议表单（内容/责任人/时限/优先级/关联部件） |
| `TaskDispatchConfirm` | 任务分发确认弹窗（显示自动匹配结果，可手动调整） |
| `MeetingAttachmentList` | 会议关联文件/数模列表 |

#### 3.2.4 任务管理

| 组件名 | 职责 |
|--------|------|
| `TaskListPage` | 任务列表页容器（代办/已办/全部标签） |
| `TaskStatsBar` | 顶部统计栏（待处理/今日到期/已超期） |
| `TaskFilterBar` | 筛选器（来源/优先级/关联部件/截止日期/搜索） |
| `TaskTable` | 任务列表表格（标题/来源/关联部件/优先级/截止日期/状态/快速操作） |
| `TaskDetailPage` | 任务详情页 |
| `TaskInfoPanel` | 任务基本信息（责任人/协作人/创建人/时间/关联部件/项目） |
| `TaskDescription` | 任务描述展示 |
| `TaskProgressBar` | 进度条+进展备注时间线 |
| `TaskDeliverableUpload` | 交付物上传区 |
| `TaskCommentList` | 任务讨论列表（支持@提及/附件） |
| `TaskCommentInput` | 评论输入框 |
| `TaskActionBar` | 底部操作栏（标记完成/申请延期/转派/催办/添加协作人/关闭） |
| `TaskReassignModal` | 任务转派弹窗（选择新责任人+原因） |
| `TaskExtendModal` | 申请延期弹窗（新截止日期+原因） |

#### 3.2.5 文件管理

| 组件名 | 职责 |
|--------|------|
| `FileListPage` | 文件列表页容器（左侧文件夹树+右侧文件列表） |
| `FolderTree` | 文件夹树（展开/折叠/新建文件夹/拖拽移动） |
| `FileFilterBar` | 筛选器（上传方/分类/密级/上传人/时间/关联部件/文件类型/搜索） |
| `FileTable` | 文件列表表格（文件名+版本/上传方/分类/关联部件/关联数模/上传人/时间/大小/操作） |
| `FileGridView` | 网格视图（缩略图+文件名） |
| `FileUploadModal` | 上传弹窗（拖拽区+文件列表+变更说明+分类+密级+自动关联部件显示+关联数模提示） |
| `FileUploadProgress` | 上传进度条（支持断点续传/暂停/取消） |
| `FileDetailPage` | 文件详情页 |
| `FileOverview` | 文件概览（基本信息+当前版本+关联部件+关联数模） |
| `FileVersionList` | 版本历史列表（版本号/上传人/上传方/时间/变更说明/基线标记/操作） |
| `FileVersionDiff` | 版本对比（参数表单元格级对比/文档文本对比） |
| `FileRelatedTasks` | 关联任务列表 |
| `FileChangeRecords` | 变更记录（关联CR列表） |
| `FileAuditLog` | 操作日志 |
| `AutoMatchResult` | 自动关联结果展示（匹配置信度+确认/修改按钮） |
| `ComponentSelectTree` | 部件选择树弹窗（用于手动关联部件） |

#### 3.2.6 系统部件管理

| 组件名 | 职责 |
|--------|------|
| `ComponentTreePage` | 部件树浏览页（左侧树+右侧详情） |
| `ComponentTree` | 部件树（展开/折叠/拖拽排序/搜索定位/颜色标识所属方） |
| `ComponentSummary` | 部件摘要信息（编号/名称/负责人/技术状态/关联文件数/数模数/待办数） |
| `ComponentDetailPage` | 部件详情页容器（多标签页） |
| `ComponentOverview` | 概览（基本属性+负责人+技术状态+统计数据） |
| `ComponentParamEditor` | 技术参数表编辑器（在线编辑+字段级版本对比+导入导出） |
| `ComponentOwnerConfig` | 负责人配置（商飞侧/商发侧负责人+备份+生效日期+变更历史） |
| `ComponentFileList` | 关联文件列表（按上传方/分类/密级筛选+上传关联文件） |
| `ComponentCadList` | 关联数模列表（在线查看/版本对比） |
| `ComponentChangeTimeline` | 变更记录时间线（文件版本/数模版本/参数修改/CR/会议决议/负责人变更/技术状态变更） |
| `ComponentTaskList` | 相关任务列表（代办/已办） |
| `ComponentCrList` | 相关CR列表 |
| `ComponentCreateModal` | 新增部件弹窗（编号/名称/上级/类型/所属方/阶段/状态） |
| `ComponentBatchImport` | 批量导入（下载模板/上传/校验/错误报告） |
| `ResponsibilityMatrixPage` | 职责划分矩阵页（行=部件，列=职责类型，下拉选择责任方） |
| `ResponsibilityMatrixTable` | 职责矩阵表格（批量配置+导出+变更历史） |

#### 3.2.7 数模查看器

| 组件名 | 职责 |
|--------|------|
| `CadViewerPage` | 数模查看器全屏页 |
| `CadViewerCanvas` | Three.js 渲染画布（WebGL场景+相机+控制器） |
| `ModelTreePanel` | 左侧模型树（装配体零部件层级+显示/隐藏/单独显示） |
| `ViewerToolbar` | 顶部工具栏（视角预设/适合窗口/显示模式/爆炸图/剖切/测量/批注/截图/对比/分享/全屏） |
| `PropertyPanel` | 右侧属性面板（选中零件属性） |
| `MeasurementPanel` | 测量结果列表 |
| `AnnotationPanel` | 批注列表（批注人/时间/内容/定位/回复） |
| `AnnotationEditor` | 批注编辑器（圈点/文字/箭头工具+三维坐标拾取） |
| `DiffPanel` | 版本对比差异列表 |
| `LoadingProgress` | 模型加载进度条+低精度预览按钮 |
| `ModelInfoBar` | 底部模型信息（三角形数/顶点数/操作提示） |

**数模查看器实现要点**：
- STEP 前端解析：使用 `opencascade.js`（WebAssembly 版 OpenCascade），在 Web Worker 中解析STEP文件为三角网格，再交给Three.js渲染，避免阻塞主线程。
- CATIA/IGES：一期上传校验直接拒绝，不进入解析和转换流程。
- 大模型优化：使用 Three.js 的 `BufferGeometry` + `InstancedMesh`，LOD（细节层次）技术，按需加载零部件。
- 批注定位：批注存储三维坐标 + 相机参数，点击定位时恢复相机视角并高亮批注位置。

#### 3.2.8 EPICCA 导入

| 组件名 | 职责 |
|--------|------|
| `EpiccaImportPage` | EPICCA历史资料文件导入页 |
| `CoordDocList` | 待导入协调单清单（编号/标题/日期/发起方/关联部件/选择） |
| `FileImportPanel` | 文件导入面板（下载模板/上传文档+清单/校验/进度） |
| `ImportProgressBar` | 导入进度条（总数/成功/失败/当前处理项） |
| `ImportReport` | 导入报告（成功数/失败数/失败原因列表/导出报告） |
| `ImportHistoryList` | 历史导入记录列表 |

---

## 四、核心流程实现思路

### 4.1 会议决议 → 任务自动分发

```
用户点击"发布纪要"
    │
    ▼
后端校验纪要内容（至少1条决议或标注无决议）
    │
    ▼
保存纪要 + 决议（status=PUBLISHED）
    │
    ▼
触发任务分发引擎（异步，RabbitMQ）
    │
    ├── 对每条决议执行责任人匹配：
    │   1. 决议手动指定了责任人？→ 使用指定责任人
    │   2. 决议关联了部件？
    │      ├── 查职责矩阵：该部件+任务类型的责任方
    │      ├── 查部件负责人：责任方对应的负责人
    │      └── 职责为"双方"？→ 双方负责人都为责任人（协作任务）
    │   3. 以上都不匹配？→ 默认指派给会议主持人，标记"待重新指派"
    │
    ▼
创建任务（source_type=MEETING, source_id=meeting_id）
    │
    ▼
更新决议的 task_id 字段
    │
    ▼
发送通知（WebSocket + 站内通知 + 邮件）给责任人
    │
    ▼
返回分发结果给前端（成功X条/失败X条/待重新指派X条）
```

**实现要点**：
- 任务分发异步化：通过 RabbitMQ 解耦，避免会议发布接口超时。
- 匹配规则引擎：使用策略模式，每个匹配规则是一个 `TaskAssigneeMatcher` 实现，按优先级链式调用。
- 事务一致性：纪要、决议和 `outbox_event` 在同一数据库事务中提交；后台发布器再投递RabbitMQ，消费者通过 `idempotency_key` 保证任务和通知不重复。
- 分发确认：前端弹出确认对话框，用户可手动调整责任人后再确认分发。

### 4.2 文件上传 → 自动关联部件和数模

```
用户选择文件 + 填写变更说明/分类/密级
    │
    ▼
前端计算文件 MD5（大文件分片计算，Web Worker）
    │
    ▼
请求后端"秒传检查"（MD5 + project_id 联合判断，秒传不跨项目）
    │
    ├── 同项目内已存在 → 创建新版本引用已有存储对象（秒传）
    └── 不存在（或仅其他项目存在）→ 分片上传（每片5MB，支持断点续传）
    │
    ▼
上传完成 → 后端创建 file + file_version 记录
    │
    ▼
触发自动关联引擎（异步）
    │
    ├── 第一重：部件编号匹配
    │   ├── 从文件名中正则提取部件编号
    │   └── 在 component 表中查找（project_id + code）
    │
    ├── 第二重：文件名规则匹配
    │   ├── 检测文件名后缀（_参数表/_数模/_ICD等）
    │   └── 同前缀部件编号的参数表与数模自动建立 file_cad_rel
    │
    ├── 第三重：元数据标签匹配
    │   ├── 读取 STEP 文件属性 / Excel 表头
    │   └── 提取部件编号/名称进行匹配
    │
    ▼
匹配结果判定：
    ├── ≥2重匹配成功 → 高置信度，自动确认（is_confirmed=true）
    ├── 1重匹配成功 → 中置信度，待确认（is_confirmed=false，7天后自动确认）
    └── 0重匹配 → 低置信度，不关联，通知用户手动关联
    │
    ▼
创建 file_component_rel 记录
    │
    ▼
如果文件扩展名为 .step/.stp：标记为STEP数模，前端在线查看时使用WebAssembly解析
    │
    ▼
通知部件双方负责人 + 文件关注者（WebSocket + 站内通知）
```

**实现要点**：
- 大文件分片上传：前端使用 `spark-md5` 计算 MD5，分片上传到 MinIO/OSS，后端合并。断点续传通过记录已上传分片索引实现。
- 自动关联引擎：三重匹配独立执行，匹配置信度累加。使用 CompletableFuture 并行执行匹配规则。
- 中置信度自动确认：使用 RabbitMQ 延迟队列，7天后自动将 is_confirmed 置为 true。
- STEP查看不需要服务端转换；前端在Web Worker中解析原始STEP，解析失败只影响在线查看，不影响原始文件下载。

### 4.3 数模在线查看

```
用户点击"在线查看"
    │
    ▼
前端判断文件格式：
    │
    ├── STEP → 前端解析模式
    │   ├── 下载原始 STEP 文件（预签名 URL，支持 Range 请求）
    │   ├── Web Worker 中加载 opencascade.js WASM
    │   ├── 解析 STEP 为三角网格（B-Rep → Mesh）
    │   ├── 传输网格数据到主线程（Transferable 对象）
    │   └── Three.js 渲染 BufferGeometry
    │
    └── 其他格式 → 上传时拒绝，并提示一期仅支持 STEP 数模
    │
    ▼
模型加载完成 → 初始化交互控制器（OrbitControls）
    │
    ▼
用户操作（旋转/缩放/平移/剖切/测量/批注）
    │
    ├── 剖切：Three.js ClippingPlane，修改材质 clippingPlanes
    ├── 测量：Raycaster 拾取模型表面点，计算两点距离/角度/面积
    ├── 批注：Raycaster 拾取三维坐标，创建 Sprite/Line 标注，存储坐标+相机参数
    └── 版本对比：加载两个STEP解析结果，几何差异检测（边界盒对比+顶点距离），颜色高亮
```

**实现要点**：
- opencascade.js 体积较大（~10MB WASM），使用懒加载，首次查看时下载并缓存到 IndexedDB。
- STEP 解析在 Web Worker 中执行，避免阻塞 UI；≤500万三角形目标首屏可交互≤5秒，500-2000万目标≤15秒，超过目标展示进度和降级提示。
- Three.js 渲染优化：使用 `BufferGeometry` 而非 `Geometry`，大装配体使用 `InstancedMesh` 实例化重复零件，材质共享。
- 内存管理：离开查看器页面时，主动 dispose 几何体和材质，避免内存泄漏。
- 批注持久化：批注存储到 cad_annotation 表，包含三维坐标和相机参数。定位时恢复相机视角。
- **批注无需审批，即时可见**：批注保存后立即对所有有该文件版本查看权限的用户可见，无需对方确认或审批。通过 WebSocket 向项目内在线用户推送 `annotation_created` 事件，接收方实时刷新批注列表和 3D 场景中的标注。批注回复（parent_id）同样即时推送。
- **支持的格式范围**：一期数模仅支持 STEP/STEP AP 文件（.step/.stp），上传时同时校验扩展名和文件签名；IGES、CATIA、3DXML及其他专有格式直接拒绝。

### 4.4 EPICCA历史数据文件导入

一期不实现EPICCA API、连接测试、远程拉取或实时同步，仅支持项目管理员上传Excel清单和Word/PDF历史技术协调单。

```
项目管理员下载Excel模板并填写协调单编号/标题/部件编号/日期/发起方
    │
    ▼
上传Excel清单 + Word/PDF文档（单次最多50个，单文件最大50MB）
    │
    ▼
同步校验格式、必填字段、部件编号存在性、协调单编号重复性
    │
    ├── 校验失败 → 返回逐项错误，不创建导入任务
    └── 校验通过 → 创建导入任务和outbox事件
    │
    ▼
异步解析固定模板并匹配部件
    │
    ├── 部件存在 → 创建epicca_reference_doc和文件关联
    └── 部件不存在/解析失败 → 记录失败原因，不创建参考资料
    │
    ▼
生成导入报告并通过站内通知/WebSocket推送结果
```

**实现要点**：
- 导入任务使用 `idempotency_key` 和 `(project_id, coord_no)` 唯一约束，重复上传只返回原任务结果。
- 文档作为历史参考资料保存，不参与版本管理、不触发数模自动关联、不创建任务。
- 导入失败按单条记录隔离，成功记录不因其他记录失败而回滚。
- API配置、API凭证、远程调用重试和实时同步接口全部作为二期扩展，不进入一期代码路径。

### 4.5 任务催办与超期

```
系统定时任务（每小时执行）
    │
    ├── 查询到期前3天的 PENDING/IN_PROGRESS 任务 → 发送提醒通知
    ├── 查询今天到期的任务 → 发送"今日到期"通知
    └── 查询已超期（deadline < today 且 status≠COMPLETED/CLOSED）的任务
        ├── 更新 status=OVERDUE
        ├── 每日发送催办通知给责任人+其上级（项目经理）
        └── 记录催办日志
```

**实现要点**：
- 使用 Spring `@Scheduled` 或 XXL-Job 定时任务，每小时执行一次。
- 超期判断基于 deadline 日期（不考虑时分秒），当天结束时判断。
- 手动催办限流：每人每天最多催办同一任务 3 次，避免骚扰。

### 4.6 任务拒绝 → 升级上级 → 重新分配

```
责任人在任务详情页点击"拒绝任务"
    │
    ▼
填写拒绝原因（必填，最多500字）
    │
    ▼
后端校验：当前用户 == task.assignee_id 且 status ∈ {PENDING, IN_PROGRESS}
    │
    ▼
更新 task：
  - status = REJECTED
  - rejected_by = 当前用户ID
  - rejected_at = NOW()
  - rejected_reason = 拒绝原因
  - progress 保持不变（不重置）
    │
    ▼
查找拒绝人的"上一级领导"：
  - 基于组织架构：查 user.department_id → organization.parent_id → 该上级部门的负责人
  - 若上级部门无负责人，则继续向上查找，直到找到有负责人的部门或到达组织根节点
  - 【需确认】"上一级领导"是组织架构上级，还是项目中的项目经理/角色上级？
    │
    ▼
设置 task.escalated_to = 上级领导ID
    │
    ▼
发送通知（WebSocket + 站内通知 + 邮件）给上级领导：
  "XX拒绝了任务「{任务标题}」，原因：{拒绝原因}，请重新分配"
    │
    ▼
上级领导在"待重新分配"任务列表中看到该任务（筛选 escalated_to = 当前用户 且 status = REJECTED）
    │
    ▼
上级领导点击"重新分配" → 选择新责任人（可选择原拒绝人或项目内其他成员）+ 可选备注
    │
    ▼
后端校验：当前用户 == task.escalated_to
    │
    ▼
更新 task：
  - assignee_id = 新责任人ID
  - status = PENDING（重置为待处理）
  - escalated_to = NULL（清空，标记已处理）
  - rejected_by / rejected_at / rejected_reason 保留（作为历史审计，不清除）
    │
    ▼
写入 task_reassignment 记录：
  - from_user_id = 原拒绝人ID
  - to_user_id = 新责任人ID
  - reason = "任务被拒绝后由{上级姓名}重新分配：{备注}"
  - created_by = 上级领导ID
    │
    ▼
发送通知给新责任人 + 原拒绝人（告知任务已重新分配）
```

**实现要点**：
- **上级领导查找**：通过 `organization` 表的 `parent_id` 递归查找。用户表存 `department_id`，部门表存 `parent_id`，上级部门的负责人通过 `component_owner` 或单独的部门负责人表确定。一期可简化为：部门负责人 = 该部门中职位最高（position 字段排序）且状态为 ACTIVE 的用户。
- **拒绝权限**：只有任务当前责任人（assignee_id）可以拒绝，协作人（collaborator_ids）不能拒绝。项目经理可直接转派，无需走拒绝升级流程。
- **重新分配权限**：只有 escalated_to 指向的上级领导可以重新分配。系统管理员可强制重新分配（需记录审计日志）。
- **拒绝次数限制**：同一任务同一责任人只能拒绝一次（重新分配后如果又分配给同一人，可再次拒绝）。防止反复拒绝。
- **超期处理**：REJECTED 状态的任务不参与超期催办（因为责任人已拒绝，等待上级分配）。但如果上级超过3天未处理，发送提醒给上级的上级（二级升级）。
- **状态机**：PENDING/IN_PROGRESS → REJECTED → PENDING（重新分配后）。REJECTED 状态不可直接标记完成或关闭，必须先重新分配。
- **前端组件**：任务详情页 `TaskActionBar` 增加"拒绝"按钮（仅责任人可见）；新增 `TaskRejectModal`（填写拒绝原因）；任务列表新增"待重新分配"筛选标签（仅上级领导可见）；`TaskReassignModal` 复用于重新分配场景。

---

## 五、技术难点与解决方案

### 5.1 大文件上传与断点续传

**难点**：数模文件几十到几百 MB，甚至 2GB，传统表单上传易超时、断网后需重新上传。

**解决方案**：
- 分片上传：文件按 5MB 分片，每片独立上传，支持并发上传（3-5 片并发）。
- MD5 秒传：上传前计算文件 MD5，后端已存在则直接引用，秒传。
- 断点续传：前端记录已上传分片索引到 localStorage，断网后恢复时跳过已上传分片。
- 后端合并：所有分片上传完成后，后端调用 MinIO/OSS 的 `composeObject` 合并分片。
- 上传进度：实时显示总进度和单片进度，支持暂停/取消/继续。

### 5.2 STEP解析与WebGL渲染性能

**难点**：STEP文件几十到几百MB，解析和三角网格传输可能占用大量浏览器内存；大装配体（上千零件、数百万三角形）在浏览器中渲染可能卡顿。

**解决方案**：
- STEP 前端解析：使用 opencascade.js（WASM）在Web Worker中解析STEP，无需服务端转换。
- 渲染优化：
  - Three.js `BufferGeometry` + 顶点数据压缩（Draco 压缩）
  - LOD（细节层次）：远距离显示低精度模型，近距离切换高精度
  - 实例化渲染（`InstancedMesh`）：重复零件只存一份几何体
  - 视锥体剔除：不可见零件不渲染
  - 大模型默认按装配体层级和视锥体分块加载，超过上限提示拆分或下载本地
- 解析失败：保留原始STEP下载能力，记录失败原因、浏览器版本、文件大小和三角形估计值，便于定位兼容性问题。

### 5.3 任务自动分发规则引擎

**难点**：分发规则复杂（手动指定>部件负责人>职责矩阵>主持人），未来可能新增规则来源，需要可扩展的规则引擎。

**解决方案**：
- 策略模式 + 责任链：定义 `TaskAssigneeMatcher` 接口，每个匹配规则是一个实现类，按 `@Order` 排序组成责任链。
- 规则配置化：匹配优先级和规则参数存储在数据库系统配置表，可动态调整，无需改代码。
- 匹配结果缓存：部件负责人和职责矩阵查询结果缓存到 Redis（TTL 5分钟），减少数据库查询。
- 分发结果可追溯：记录每次自动分发的匹配过程（命中了哪条规则、为什么匹配到这个人），存储到 task 的扩展字段，便于排查问题。

### 5.4 文件自动关联匹配算法

**难点**：文件名命名不规范，部件编号提取准确率不稳定；元数据读取依赖文件格式解析库。

**解决方案**：
- 三重匹配独立执行，置信度累加，不依赖单一规则。
- 部件编号正则可配置：不同项目可能有不同编码规则，正则表达式存储在项目配置中。
- 文件名规则学习：统计用户手动修正的关联关系，优化匹配规则（二期可引入简单机器学习）。
- 关联质量监控：每月生成自动关联准确率报告，准确率低于 80% 时触发优化。
- 手动关联便捷操作：提供拖拽文件到部件树、批量关联等便捷操作，降低手动关联成本。

### 5.5 实时通知与 WebSocket 连接管理

**难点**：多用户同时在线，WebSocket 连接管理、消息可靠投递、断线重连。

**解决方案**：
- STOMP over WebSocket：使用 Spring WebSocket + STOMP 协议，支持订阅主题（用户个人主题、项目主题）。
- 连接管理：用户登录时建立 WebSocket 连接，绑定 userId 到 session。心跳检测（每 30s ping），断线自动重连（指数退避）。
- 消息可靠投递：通知先存数据库（notification 表），WebSocket 推送成功后标记已推送。用户上线时拉取未推送的通知。
- 多端同步：用户多设备登录时，所有设备都收到通知，已读状态同步。
- 降级方案：WebSocket 不可用时，前端轮询通知接口（每 30s），确保通知不丢失。

### 5.6 部件树高效查询与操作

**难点**：部件树最大 5 级、单项目约 100 个部件（未来可能更多），树形结构的查询、拖拽排序、批量操作效率。

**解决方案**：
- 邻接表 + 物化路径双模式：parent_id 用于增删改，path 字段用于子树查询（`WHERE path LIKE '/1/5/%'`），避免递归 CTE。
- path 维护：新增/移动节点时，在事务中更新当前节点和所有子节点的 path（使用 PG 的字符串替换）。
- 树节点缓存：部件树结构变化少，缓存整棵树到 Redis（TTL 10分钟），增删改时失效缓存。
- 虚拟滚动：部件树节点多时使用虚拟滚动（react-window），只渲染可视区域节点。
- 拖拽排序：使用 dnd-kit 库实现拖拽，后端更新 sort_order 字段。

### 5.7 并发控制与数据一致性

**难点**：多用户同时编辑同一文件版本、同一部件属性、审批同一 CR，可能导致数据覆盖。

**解决方案**：
- 乐观锁：关键业务表带 row_version 字段，更新时 `WHERE id=? AND row_version=?`，版本不匹配则返回409，前端提示刷新。
- 文件上传并发：同一文件新版本上传时，后端检查是否有更新版本，有则提示用户基于最新版本。
- 审批并发：后续CR审批节点使用状态机，已审批节点不可重复审批，数据库层面 `WHERE status='PENDING'` 乐观锁。
- 幂等与锁：任务自动分发、文件导入和定时清理使用 `idempotency_key`；Redis锁只用于降低重复执行，不作为一致性的唯一保障。
- 事务边界：文件元数据、版本、关联关系和outbox事件在短事务内提交；对象存储上传采用上传会话状态机，合并成功后才写入当前版本。

### 5.8 数据安全与保密

**难点**：航空发动机数据属于国家敏感信息，文件下载泄密溯源、传输加密、存储加密。

**解决方案**：
- 传输加密：全站 HTTPS/TLS 1.2+，WebSocket 使用 WSS。
- 存储加密：文件存储服务端加密（SSE-S3/SSE-KMS），数据库敏感字段加密。
- 下载水印：下载文件时添加隐形水印（用户ID+时间戳），用于泄密溯源。PDF/Office 文件支持水印注入。
- 下载链接时效：文件下载使用预签名 URL，2小时有效，防止链接泄露后长期可用。
- 操作审计：所有文件上传/下载/查看、权限变更、CR审批等操作记录审计日志，不可删除。
- 一期角色边界：系统管理员负责配置和运维，安全审计员只读审计日志；等保三级要求的完整三权分立移至后续阶段。
- 登录安全：密码 bcrypt 加盐哈希，登录失败 5 次锁定 30 分钟，支持 2FA。

### 5.9 EPICCA文件导入稳定性

**难点**：批量文件格式不一致、清单与文档错配、部件编号不存在以及部分记录失败。

**解决方案**：
- 导入前同步校验Excel字段、文档数量、文件名映射、部件编号和协调单编号唯一性。
- 导入任务按协调单逐条处理，单条失败不影响其他记录；失败原因写入error_report并支持下载。
- 导入任务使用idempotency_key和`UNIQUE(project_id, coord_no)`防止重复导入；中断后从未完成记录继续。
- 文件导入仅访问平台自身对象存储，不调用外部API；API连接配置和凭证不进入一期数据库。

---

## 六、需要确认的边缘情况

以下是 PRD 中未明确、但技术实现需要确认的问题：

### 6.1 数模相关

1. **一期数模格式范围？**
   ✅ **已确认：一期仅支持STEP（.step/.stp）。** IGES、CATIA、3DXML及其他专有格式上传时拒绝，不建设服务端格式转换。

2. **数模查看是否需要支持装配体爆炸图动画？** PRD 提到了爆炸图开关，但是否需要自动拆解动画（逐步爆炸）还是仅静态爆炸位置？

3. **数模版本对比的精度要求？** 几何差异检测的阈值默认 1mm，是否需要可配置到 0.1mm？高精度对比可能需要更复杂的算法（如 Hausdorff 距离）。

4. **数模批注是否需要审批流程？** ~~如批注需对方确认/回复后才算闭环，还是仅作为评论？~~
   ✅ **已确认：批注无需审批，保存后即时可见。** 批注保存后立即对所有有该文件版本查看权限的用户可见，通过 WebSocket 实时推送给项目内在线用户。批注作为评论性质，不设审批/确认环节。cad_annotation 表不设 status 字段。

### 6.2 文件相关

5. **文件秒传（MD5去重）是否跨项目？** ~~同一文件在不同项目中上传，是否共享存储对象（节省空间）还是每个项目独立存储（数据隔离）？~~
   ✅ **已确认：秒传不跨项目。** MD5 秒传仅在同一项目内生效，不同项目即使 MD5 相同也需重新上传，确保项目间数据隔离。秒传检查时通过 `file_version JOIN file` 联合判断 `project_id + md5`。

6. **文件删除后，关联的数模和任务是否自动解除关联？** 还是保留关联记录但标记文件已删除？

7. **文件版本是否支持回滚（将旧版本设为当前版本）？** 还是只能上传新版本？

8. **大文件（>500MB）上传是否需要支持"上传后通知"异步体验？** 即用户提交后可关闭页面，上传完成后收到通知。

### 6.3 任务相关

9. **任务自动分发后，如果责任人拒绝任务，流程如何？** ~~是否需要填写拒绝原因并退回发起人重新指派？还是只能转派？~~
   ✅ **已确认：拒绝任务时将流程转至拒绝该任务的人的上一级领导，由他重新分配。** 责任人填写拒绝原因后，系统自动查找其组织架构上一级领导，任务状态变为 REJECTED 并升级给上级。上级在"待重新分配"列表中选择新责任人（可选择原拒绝人或其他人），重新分配后任务状态回到 PENDING。详见 4.6 节完整流程设计。

10. **子任务的进度如何汇总到主任务？** 简单平均还是按权重？权重如何分配？

11. **任务超期后是否自动升级通知？** 如超期 3 天通知责任人上级，超期 7 天通知项目经理，超期 15 天通知项目总监？

### 6.4 会议相关

12. **会议是否需要支持视频会议集成（如腾讯会议/飞书会议）？** 还是仅生成外部链接，用户手动加入？

13. **周期性会议（如周例会）的某一次会议取消，是否影响后续会议？** 修改系列会议时是否支持"仅修改本次/修改全部后续"？

### 6.5 系统部件相关

14. **部件树是否需要支持版本管理？** 即部件结构变更（新增/删除/移动节点）是否需要记录历史版本，可回溯？

15. **部件合并功能是否一期需要？** PRD 提到了分支流程支持部件合并，但合并涉及大量关联数据迁移，复杂度较高。

16. **部件负责人变更时，未完成任务是否自动转派？** 还是需要原负责人确认？项目经理是否可强制转派？

### 6.6 数据与安全相关

17. **数据保留期限是否有合规要求？**
    ✅ **已确认：业务数据永久保留，审计日志保留180天。** 业务数据只软删除；审计日志按批次清理并记录清理审计，不做无记录的物理删除。

18. **是否需要支持多项目间的数据复制（如模板项目复制）？** 如从模板项目复制部件树结构、职责矩阵配置到新项目。

19. **文件水印是隐形水印还是可见水印？** 隐形水印用于泄密溯源，可见水印（如用户名+时间）用于威慑。是否两者结合？

20. **EPICCA API 的认证方式具体是什么？**
    ✅ **已确认：EPICCA API 对接不纳入一期。** 一期仅保留文件导入页面、任务和历史参考文档数据结构；API连接配置、凭证和远程调用服务作为二期扩展。

---

> 以上问题中，STEP样本性能、文件版本回滚、子任务进度汇总、部署形态和SSO属于后续阶段或验证项；一期范围已冻结，不再阻塞会议→任务→文件→部件追溯主链路。

---

## 七、一期工程保障设计

### 7.1 事务一致性与幂等

一期所有会产生异步副作用的写操作采用“业务数据 + outbox_event 同库事务”模式：

1. API在一个短事务中写入业务数据、状态和 `outbox_event`，事务提交前不发送RabbitMQ消息。
2. Outbox发布器按 `status=PENDING AND next_attempt_at<=NOW()` 拉取事件，发布成功后标记 `PUBLISHED`；失败按指数退避重试，超过5次进入 `FAILED` 并告警。
3. 消费者以 `idempotency_key` 去重。任务自动分发的幂等键格式为 `project:{projectId}:resolution:{resolutionId}:assignee:{userId}:rule:{ruleVersion}`；同一键只能创建一个任务。
4. 通知允许重复投递但不得重复生成业务任务；前端以事件ID去重，断线后通过通知表补拉。
5. 文件上传采用 `file_upload_session` 状态机：`UPLOADING→COMPLETING→COMPLETED`。只有对象存储合并成功、MD5校验通过后，才创建 `file_version` 并更新 `file.current_version_id`。

### 7.2 可观测性与告警

所有请求、异步消息和文件任务携带 `trace_id`。日志不得输出密码、API凭证或原始敏感文件内容。

| 信号 | 指标/日志 | 告警或处理阈值 |
|------|-----------|----------------|
| API健康 | `http_requests_total`、`http_request_duration_ms` | 常规接口P95连续5分钟>500ms告警 |
| 任务分发 | `task_dispatch_total{status}`、`task_dispatch_duration_ms` | 失败率>1%告警；>5%暂停灰度 |
| Outbox/MQ | `outbox_pending_count`、`outbox_failed_total`、消息重试次数 | pending持续10分钟或FAILED>0告警 |
| 文件上传 | `upload_success_total`、`upload_failure_total`、断点恢复次数 | 失败率>3%告警；不得丢失已完成分片 |
| STEP解析 | `step_parse_total{status}`、`step_parse_duration_ms`、浏览器内存错误 | 失败率>5%暂停扩大试点；单模型超时记录样本 |
| 自动关联 | `file_match_total{confidence}`、人工修正率 | 月度准确率<80%触发规则复盘 |
| 权限安全 | `authorization_denied_total`、审计写入失败数 | 审计写入失败立即阻断敏感操作并告警 |
| 可用性 | `health_check`、数据库切换事件、WebSocket重连数 | 可用性低于99.9%或数据丢失立即停止发布 |

### 7.3 测试与验证矩阵

| 测试层级 | 一期覆盖内容 | 通过标准 |
|----------|--------------|----------|
| 单元测试 | 任务匹配规则、版本号、权限判断、STEP扩展名/签名校验、审计清理批次 | 核心业务模块覆盖率≥80% |
| 集成测试 | PostgreSQL事务、Outbox发布、RabbitMQ重试、Redis锁、对象存储分片合并 | 重试不重复建任务；断点续传可恢复 |
| 契约测试 | 前后端任务、文件、STEP查看、导入报告接口；WebSocket事件 | Schema兼容，错误码和字段稳定 |
| E2E测试 | 会议发布→任务→文件交付→部件追溯；项目成员全可见；角色操作限制 | MVP五项验收链路全部通过 |
| STEP样本测试 | 不同文件大小、装配层级、零件数量、STEP版本和损坏文件 | 成功率、首屏时间、完整解析时间、FPS达到PRD指标 |
| 性能测试 | 100人并发操作、500在线、2GB文件分片上传、任务批量分发 | P95/P99、吞吐、内存和错误率达标 |
| 安全测试 | 越权读写、下载URL过期、密码策略、病毒文件、审计不可改 | 无跨项目访问；2小时URL过期；安全基线问题清零 |
| 恢复演练 | 数据库主库故障、对象存储短暂不可用、RabbitMQ积压、审计清理恢复 | RPO≤1小时、RTO≤4小时；业务数据无丢失 |

### 7.4 灰度、迁移与回滚

1. 采用项目级 Feature Flag：`mvp_meeting_task_file_component`、`step_viewer`、`epicca_file_import`分别控制能力，不将CATIA/IGES/API/SSO代码路径混入一期开关。
2. 数据库迁移采用 expand-contract：先新增可空字段和新表，完成回填与双读，再切换读写，验证后才增加非空和唯一约束；禁止直接删除旧列。
3. 发布顺序为内部环境→单项目试点→3–5个项目灰度→全量。每阶段至少观察两周，并在进入下一阶段前完成数据一致性和权限抽样检查。
4. 立即停止扩大的触发条件：系统不可用超过30分钟、发现跨项目越权、业务数据丢失、Outbox持续失败、核心功能错误率>5%。
5. 回滚优先关闭Feature Flag；代码回滚前先执行兼容性检查。已执行的数据迁移不通过删除数据回滚，而通过反向兼容读取、补偿脚本或恢复备份处理。
6. STEP解析失败只回滚查看器状态，不回滚原始文件上传和文件版本记录；任务分发失败通过Outbox重试或人工补偿，不删除会议纪要。

### 7.5 一期安全与保留策略

- 认证：账号密码、bcrypt哈希、失败5次锁定30分钟；SSO不纳入一期，2FA按项目安全等级配置。
- 授权：项目成员默认可查看项目内全部文件和STEP数模；上传、删除、基线、批注、系统配置按RBAC控制；一期不提供访客/观察员文件级ACL。
- 文件：下载必须经过权限校验，预签名URL有效期2小时；上传执行扩展名、签名和病毒扫描校验。
- 审计：记录登录、文件上传/下载/查看、权限变更、任务转派、部件修改和系统配置变更；应用角色不可更新或删除审计记录。
- 保留：业务数据永久保留并只做软删除；审计日志保留180天，清理前备份，清理后保留批次摘要和执行审计。
- 边界：等保三级、正式保密部署、SSO统一认证和具体生产云形态不作为一期发布门槛。

### 7.6 一期发布门禁

以下条件全部满足才允许试点：

1. MVP主链路E2E通过，会议任务无重复创建，文件交付可追溯到版本和部件。
2. 项目成员全可见规则和角色操作限制通过越权测试。
3. STEP样本测试达到首屏可交互、完整解析和帧率指标；失败模型仍可下载原始文件。
4. Outbox、消息重试、文件断点续传和数据库恢复演练通过。
5. 审计、病毒扫描、下载URL过期、密码锁定和备份恢复通过安全基线检查。
6. 监控面板、告警联系人、回滚开关和试点数据责任人已配置。
