[
  {
    "id": "F00",
    "behavior": "GET / returns HTTP 200 and an HTML page containing '欢迎使用AECP' and '会议 → 任务 → 文件 → 部件追溯'",
    "verification": ".\\mvnw.cmd clean test",
    "state": "passing",
    "evidence": "docs/STARTUP_CHECKLIST.md；xiaou-starter/src/test/java/com/xiaou/web/controller/IndexControllerTest.java"
  },
  {
    "id": "F01",
    "behavior": "本地演示登录后可以访问 /workspace、/dashboard 等受保护路由，退出后再次访问会重定向到 /login",
    "verification": "pnpm --dir xiaou-frontend test:routes",
    "state": "passing",
    "evidence": "xiaou-frontend/src/app/__tests__/shell-routes.test.tsx；xiaou-frontend/src/app/route-config.ts"
  },
  {
    "id": "F02",
    "behavior": "POST /api/v1/auth/login with {username, password} returns 200 and POST /api/v1/auth/logout invalidates the session",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/auth/login -H \"Content-Type: application/json\" -d \"{\\\"username\\\":\\\"demo-pilot-pm\\\",\\\"password\\\":\\\"$env:AECP_TEST_PASSWORD\\\"}\" | jq -e '.status == 200 and .data.token != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-ROLE-001/002；验证未执行"
  },
  {
    "id": "F03",
    "behavior": "管理员可以为组织新增、移除和变更成员角色，GET /api/v1/organizations/{organizationId}/members 返回当前成员列表",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/organizations/ORG-DEMO-COMAC/members -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"user_id\\\":\\\"USR-DEMO-ENG-A\\\",\\\"role\\\":\\\"ENGINEER\\\"}\" | jq -e '.status == 201'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-ROLE-001/004；验证未执行"
  },
  {
    "id": "F04",
    "behavior": "项目管理员可以创建项目、绑定双方组织并选择项目成员，创建后 GET /api/v1/projects/{projectId} 返回项目配置",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"name\\\":\\\"接口协同演示项目\\\",\\\"organization_ids\\\":[\\\"ORG-DEMO-COMAC\\\",\\\"ORG-DEMO-AECC\\\"]}\" | jq -e '.status == 201 and .data.id != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-PM-001；验证未执行"
  },
  {
    "id": "F05",
    "behavior": "项目成员只能读取所属项目数据，用户 A 读取项目 B 的详情或文件时返回 403",
    "verification": "curl.exe -sS -o $null -w '%{http_code}' http://localhost:8080/api/v1/projects/PRJ-DEMO-OTHER -H \"Authorization: Bearer $env:AECP_TEST_TOKEN_USER_A\" | jq -e '.[0] == 4 and .[1] == 0 and .[2] == 3'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-ROLE-002；验证未执行"
  },
  {
    "id": "F06",
    "behavior": "管理员可以创建和导入系统→子系统→部件→零件层级，GET /api/v1/projects/{projectId}/components/tree 返回完整树结构",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/components/import -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -F file=@docs/fixtures/components.csv | jq -e '.status == 201 and .data.imported_count > 0'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-COMP-001/002；验证未执行"
  },
  {
    "id": "F07",
    "behavior": "管理员可以为部件配置双方负责人和职责矩阵，保存后 GET /api/v1/components/{componentId}/responsibility 返回双方责任关系",
    "verification": "curl.exe -sS -X PUT http://localhost:8080/api/v1/components/CMP-DEMO-IFACE/responsibility -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"primary_owner_id\\\":\\\"USR-DEMO-ENG-A\\\",\\\"secondary_owner_id\\\":\\\"USR-DEMO-ENG-B\\\"}\" | jq -e '.status == 200 and .data.primary_owner_id == \"USR-DEMO-ENG-A\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-ROLE-003/005、REQ-COMP-003；验证未执行"
  },
  {
    "id": "F08",
    "behavior": "主持人可以创建会议、选择参会人并维护议程，GET /api/v1/meetings/{meetingId} 返回会议基本信息和有序议程",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/meetings -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"subject\\\":\\\"接口协调周例会\\\",\\\"attendee_ids\\\":[\\\"USR-DEMO-ENG-A\\\",\\\"USR-DEMO-ENG-B\\\"],\\\"agenda\\\":[{\\\"title\\\":\\\"安装节接口\\\",\\\"sort\\\":1}]}\" | jq -e '.status == 201 and .data.agenda[0].sort == 1'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-MEET-001/002；验证未执行"
  },
  {
    "id": "F09",
    "behavior": "主持人可以保存会议纪要和结构化决议，决议包含负责人、截止时间、优先级和关联部件并可重新查看",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/meetings/MTG-DEMO-001/minutes -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"summary\\\":\\\"已确认接口尺寸\\\",\\\"decisions\\\":[{\\\"content\\\":\\\"提交最新参数表\\\",\\\"assignee_id\\\":\\\"USR-DEMO-ENG-A\\\",\\\"component_id\\\":\\\"CMP-DEMO-IFACE\\\"}]}\" | jq -e '.status == 201 and .data.decisions | length == 1'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-MEET-003；验证未执行"
  },
  {
    "id": "F10",
    "behavior": "同一会议决议重复确认只创建一个任务并产生一条通知，重复请求返回同一个 idempotency_key 结果",
    "verification": "$r1 = curl.exe -sS -X POST http://localhost:8080/api/v1/meetings/MTG-DEMO-001/decisions/DEC-DEMO-001/confirm -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\"; $r2 = curl.exe -sS -X POST http://localhost:8080/api/v1/meetings/MTG-DEMO-001/decisions/DEC-DEMO-001/confirm -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\"; jq -n --argjson a $r1 --argjson b $r2 -e '$a.data.task_id == $b.data.task_id and $b.data.created == false'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-MEET-004、REQ-TASK-001、REQ-COM-003；验证未执行"
  },
  {
    "id": "F11",
    "behavior": "责任人可以查看我的代办和已办、更新进度并将任务标记为完成，任务详情保留来源会议和关联部件",
    "verification": "curl.exe -sS -X PATCH http://localhost:8080/api/v1/tasks/TSK-DEMO-001 -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"status\\\":\\\"COMPLETED\\\",\\\"progress\\\":100}\" | jq -e '.status == 200 and .data.status == \"COMPLETED\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-TASK-002/003/004；验证未执行"
  },
  {
    "id": "F12",
    "behavior": "项目经理可以转派任务或手动催办，系统记录转派原因和催办记录且重复催办受限流保护",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/tasks/TSK-DEMO-001/reminders -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 202 and .data.reminder_id != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-TASK-004；验证未执行"
  },
  {
    "id": "F13",
    "behavior": "项目成员可以上传项目文件并完成分片合并，首次上传生成版本 v1，后续上传同一文件生成递增版本并可查看版本历史",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/files/upload-complete -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -F file=@docs/fixtures/demo-icd.xlsx -F change_note='initial upload' | jq -e '.status == 201 and .data.version == 1'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-FILE-001/002；验证未执行"
  },
  {
    "id": "F14",
    "behavior": "上传文件可以按部件编号和文件名自动关联部件/数模，用户可以在匹配失败时手动修正关联",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/files/FIL-DEMO-001/associations -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"component_id\\\":\\\"CMP-DEMO-IFACE\\\",\\\"association_type\\\":\\\"MANUAL\\\"}\" | jq -e '.status == 201 and .data.component_id == \"CMP-DEMO-IFACE\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-FILE-003、REQ-COMP-004；验证未执行"
  },
  {
    "id": "F15",
    "behavior": "项目成员可按权限访问项目文件，授权下载返回有效期不超过 2 小时的预签名 URL，越权下载返回 403",
    "verification": "curl.exe -sS http://localhost:8080/api/v1/files/FIL-DEMO-001/download-url -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and .data.expires_in_seconds <= 7200'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-FILE-004、REQ-ROLE-002；验证未执行"
  },
  {
    "id": "F16",
    "behavior": "部件详情页可以展示关联文件、数模、任务、会议决议和负责人变更形成的时间线",
    "verification": "curl.exe -sS http://localhost:8080/api/v1/components/CMP-DEMO-IFACE/timeline -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and (.data | length) > 0'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-COMP-005、MVP-03；验证未执行"
  },
  {
    "id": "F17",
    "behavior": "项目成员打开 STEP/STEP AP 文件后可以在浏览器中旋转、缩放、平移、剖切和测量，解析失败仍可下载原始文件",
    "verification": "curl.exe -sS http://localhost:8080/api/v1/file-versions/FV-DEMO-STEP-001/viewer-manifest -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and .data.format == \"STEP\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-CAD-001/002/005、MVP-04；验证未执行"
  },
  {
    "id": "F18",
    "behavior": "用户可以在 STEP 版本上创建批注并回复，批注绑定具体文件版本且对有权限的项目成员即时可见",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/file-versions/FV-DEMO-STEP-001/annotations -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"content\\\":\\\"检查安装面间隙\\\",\\\"position\\\":{\\\"x\\\":1,\\\"y\\\":2,\\\"z\\\":3}}\" | jq -e '.status == 201 and .data.file_version_id == \"FV-DEMO-STEP-001\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-CAD-003；验证未执行"
  },
  {
    "id": "F19",
    "behavior": "审计查看者可以按用户、项目、动作和时间查询登录、下载、授权和审批日志，业务角色不能修改审计记录",
    "verification": "curl.exe -sS 'http://localhost:8080/api/v1/audit-logs?project_id=PRJ-DEMO-CJ1000A&action=FILE_DOWNLOAD' -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and (.data.items | type) == \"array\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-SYS-001、MVP-05；验证未执行"
  },
  {
    "id": "F20",
    "behavior": "项目经理可以查看按项目、人员、部件和状态聚合的任务完成率、周期和超期率",
    "verification": "curl.exe -sS 'http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/task-stats?group_by=component' -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and .data.completion_rate != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-TASK-005；P1，验证未执行"
  },
  {
    "id": "F21",
    "behavior": "项目经理可以创建里程碑并查看按组织、专业和部件筛选的项目进度与健康度指标",
    "verification": "curl.exe -sS http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/health -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and .data.progress != null and .data.risk_count != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-PM-002/003/004；P1，验证未执行"
  },
  {
    "id": "F22",
    "behavior": "用户可以提交变更请求并按双方接口工程师、项目经理的审批链推进，变更状态和审批意见可追溯",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/change-requests -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"title\\\":\\\"安装面变更\\\",\\\"reason\\\":\\\"接口尺寸调整\\\"}\" | jq -e '.status == 201 and .data.status == \"PENDING_APPROVAL\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-CR-001/003；P1，验证未执行"
  },
  {
    "id": "F23",
    "behavior": "变更请求详情可以展示受影响的部件、文件、数模和任务，并在实施完成后支持闭环确认",
    "verification": "curl.exe -sS http://localhost:8080/api/v1/change-requests/CR-DEMO-001/impact -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" | jq -e '.status == 200 and (.data.components | type) == \"array\" and (.data.files | type) == \"array\"'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-CR-002/003；P1，验证未执行"
  },
  {
    "id": "F24",
    "behavior": "项目成员可以在项目内发送文字、文件和对象消息，消息中心支持未读数、已读和通知列表",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/messages -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"content\\\":\\\"请查看最新接口文件\\\"}\" | jq -e '.status == 201 and .data.message_id != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-COM-001/003；P1，验证未执行"
  },
  {
    "id": "F25",
    "behavior": "项目成员可以创建带标签和附件的话题、引用消息并标记结论，话题支持按状态筛选",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/topics -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"title\\\":\\\"安装面讨论\\\",\\\"tags\\\":[\\\"接口\\\",\\\"安装节\\\"]}\" | jq -e '.status == 201 and .data.topic_id != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-COM-002；P1，验证未执行"
  },
  {
    "id": "F26",
    "behavior": "用户可以选择两个 STEP 文件版本进行几何差异对比，并在查看器中高亮差异区域",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/model-comparisons -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -H \"Content-Type: application/json\" -d \"{\\\"base_version_id\\\":\\\"FV-DEMO-STEP-001\\\",\\\"target_version_id\\\":\\\"FV-DEMO-STEP-002\\\"}\" | jq -e '.status == 200 and .data.diff_count != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-CAD-004；P1，验证未执行"
  },
  {
    "id": "F27",
    "behavior": "项目管理员可以上传 EPICCA 历史 Excel 清单和 Word/PDF 文档，系统异步生成逐条导入结果并报告失败原因",
    "verification": "curl.exe -sS -X POST http://localhost:8080/api/v1/projects/PRJ-DEMO-CJ1000A/epicca/imports -H \"Authorization: Bearer $env:AECP_TEST_TOKEN\" -F manifest=@docs/fixtures/epicca-manifest.xlsx -F documents=@docs/fixtures/epicca-reference.pdf | jq -e '.status == 202 and .data.import_id != null'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-SYS-003；P1，验证未执行"
  },
  {
    "id": "F28",
    "behavior": "系统管理员可以修改项目规则、存储限制和通知模板，配置变更产生审计记录并按权限生效",
    "verification": "curl.exe -sS -X PUT http://localhost:8080/api/v1/admin/config/notification-templates -H \"Authorization: Bearer $env:AECP_TEST_TOKEN_ADMIN\" -H \"Content-Type: application/json\" -d \"{\\\"task_due\\\":\\\"任务即将到期：{title}\\\"}\" | jq -e '.status == 200 and .data.updated == true'",
    "state": "planned",
    "evidence": "规划项；来源：PRD_TRACEABILITY.md REQ-SYS-002；P1，验证未执行"
  }
]
