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
