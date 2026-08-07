-- ============================================================
-- Flyway 迁移：指派通知配置 + Escalation ownerId 字段 + 8D 通知配置
-- ============================================================

-- 1. escalation 表新增 ownerId 字段（可空，点对点 WebSocket 通知用）
ALTER TABLE qms.escalation
    ADD COLUMN IF NOT EXISTS owner_id BIGINT;

COMMENT ON COLUMN qms.escalation.owner_id IS '升级措施责任人ID（点对点通知使用）';

-- 2. notification_config 新增 7 条场景种子数据（含 8D 和指派场景）
-- 使用 DO $$ 块 + WHERE NOT EXISTS 代替 ON CONFLICT（因表仅有 partial unique index，非全量唯一约束）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'EIGHT_D_LEADER_ASSIGNED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('EIGHT_D_LEADER_ASSIGNED', '整改负责人指派通知', '[]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'EIGHT_D_TEAM_PENDING_REVIEW' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('EIGHT_D_TEAM_PENDING_REVIEW', '8D团队待审核通知', '["R04","R06"]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'EIGHT_D_TEAM_APPROVED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('EIGHT_D_TEAM_APPROVED', '8D团队审核通过通知', '[]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'ACTION_OWNER_ASSIGNED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('ACTION_OWNER_ASSIGNED', '改善措施指派通知', '[]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'PLAN_OWNER_ASSIGNED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('PLAN_OWNER_ASSIGNED', '整改计划指派通知', '[]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'ESCALATION_OWNER_ASSIGNED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('ESCALATION_OWNER_ASSIGNED', '升级措施指派通知', '[]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
END $$;
