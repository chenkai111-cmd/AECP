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
