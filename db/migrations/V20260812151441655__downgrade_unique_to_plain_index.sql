-- Downgrade UNIQUE indexes → plain indexes for traceability + system management modules.
-- Rationale: traceability records (finished goods inspection) and system accounts may
-- legitimately have duplicates due to logical-delete re-creation workflows; enforcing
-- UNIQUE at the index level blocks these flows and causes production errors.

-- 1. finished_goods_inspection: report_no uniqueness relaxed
DROP INDEX IF EXISTS qms.uq_finished_goods_report_no_active;
CREATE INDEX IF NOT EXISTS idx_fgi_report_no_active
    ON qms.finished_goods_inspection (plant_code, report_no) WHERE is_deleted = 0;

-- 2. sys_user: account uniqueness relaxed
DROP INDEX IF EXISTS qms.uq_su_account;
CREATE INDEX IF NOT EXISTS idx_su_account
    ON qms.sys_user (account) WHERE is_deleted = 0;

-- 3. sys_role: role_code uniqueness relaxed
DROP INDEX IF EXISTS qms.uq_sr_role_code;
CREATE INDEX IF NOT EXISTS idx_sr_role_code
    ON qms.sys_role (role_code) WHERE is_deleted = 0;

-- 4. sys_module: module_code uniqueness relaxed
DROP INDEX IF EXISTS qms.uq_sys_module_code;
CREATE INDEX IF NOT EXISTS idx_sys_module_code
    ON qms.sys_module (module_code) WHERE is_deleted = 0;

-- 5. sys_role_permission: composite uniqueness relaxed
DROP INDEX IF EXISTS qms.uq_sys_role_permission;
CREATE INDEX IF NOT EXISTS idx_sys_role_permission
    ON qms.sys_role_permission (role_code, module_code, action_code) WHERE is_deleted = 0;
