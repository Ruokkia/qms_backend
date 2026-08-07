-- 补救迁移：V20260805200000001 在 flyway_schema_history 中被标记为已执行，
-- 但运行时表实际不存在（冲突重推导致 history 与物理表不一致）。
-- 使用 IF NOT EXISTS / ON CONFLICT 保证幂等，重复执行安全。
CREATE TABLE IF NOT EXISTS notification_config (
    id              BIGSERIAL PRIMARY KEY,
    scenario_code   VARCHAR(50)  NOT NULL,
    scenario_name   VARCHAR(100) NOT NULL,
    role_codes      TEXT         NOT NULL DEFAULT '[]',
    severity_extra_roles TEXT,
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT     DEFAULT 0,
    version         INT          DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nc_scenario_code ON notification_config (scenario_code) WHERE is_deleted = 0;

COMMENT ON TABLE  notification_config                IS '通知场景配置表';
COMMENT ON COLUMN notification_config.scenario_code    IS '场景编码：EXCEPTION_CREATED / EXCEPTION_CLOSED / ESCALATION_TRIGGERED';
COMMENT ON COLUMN notification_config.scenario_name    IS '场景名称';
COMMENT ON COLUMN notification_config.role_codes       IS '接收角色编码 JSON 数组';
COMMENT ON COLUMN notification_config.severity_extra_roles IS '严重等级追加角色 JSON 数组（仅 EXCEPTION_CREATED 使用）';
COMMENT ON COLUMN notification_config.enabled          IS '是否启用：0 禁用 / 1 启用';

-- 默认配置（对照原硬编码逻辑）
INSERT INTO notification_config (scenario_code, scenario_name, role_codes, severity_extra_roles, enabled, created_by)
VALUES
    ('EXCEPTION_CREATED',   '异常单创建通知',     '["R02","R05"]',         '["R06","R07"]', 1, 'system'),
    ('EXCEPTION_CLOSED',    '异常单闭环通知',     '["R06"]',               NULL,             1, 'system'),
    ('ESCALATION_TRIGGERED','供应商升级审核通知',  '["R05","R06","R07"]',   NULL,             1, 'system')
ON CONFLICT DO NOTHING;
