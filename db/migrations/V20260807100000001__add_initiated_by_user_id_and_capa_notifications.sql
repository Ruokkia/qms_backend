-- ============================================================================
-- 2026-08-07: BOTH 模式 CAPA-8D 交错流程 - 通知推送
-- 1. 为 exception_order 表添加 initiated_by_user_id 字段，用于通知推送
-- 2. 新增 5 条 CAPA 相位审批通知配置种子数据
-- ============================================================================

-- 1. 新增发起整改人用户ID字段
ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS initiated_by_user_id BIGINT;

COMMENT ON COLUMN qms.exception_order.initiated_by_user_id IS '发起整改人用户ID（用于通知推送）';

-- 2. notification_config 新增 5 条 CAPA 场景种子数据
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'EIGHT_D_D4_SUBMITTED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('EIGHT_D_D4_SUBMITTED', 'D4根因分析提交', '["R04","R06"]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'CAPA_ROOT_CAUSE_APPROVED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('CAPA_ROOT_CAUSE_APPROVED', 'CAPA根因审批通过', '["R04","R06"]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'EIGHT_D_D5_SUBMITTED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('EIGHT_D_D5_SUBMITTED', 'D5措施方案提交', '["R04","R06"]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'CAPA_MEASURES_APPROVED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('CAPA_MEASURES_APPROVED', 'CAPA措施审批通过', '["R04","R06"]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
    IF NOT EXISTS (SELECT 1 FROM qms.notification_config WHERE scenario_code = 'CAPA_CLOSED' AND is_deleted = 0) THEN
        INSERT INTO qms.notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by, updated_by, created_at, updated_at)
        VALUES ('CAPA_CLOSED', 'CAPA闭环', '["R04","R06"]', NULL, 1, 'system', 'system', NOW(), NOW());
    END IF;
END $$;
