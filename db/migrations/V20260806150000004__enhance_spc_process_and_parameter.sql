-- =============================================================================
-- SPC 工序与参数字典增强
--   - spc_process: 补充启用/停用开关、变更备注
--   - spc_parameter: 补充小数位数、是否关键特性、变更备注
--   - spc_process.process_code: 在业务层保证同厂区唯一（SQL 层加部分唯一索引防御）
-- =============================================================================

-- 1. 工序表增强
ALTER TABLE qms.spc_process
    ADD COLUMN IF NOT EXISTS is_active VARCHAR(8) DEFAULT '是' NOT NULL,
    ADD COLUMN IF NOT EXISTS change_remark VARCHAR(500);

COMMENT ON COLUMN qms.spc_process.is_active IS '是否启用：是/否';
COMMENT ON COLUMN qms.spc_process.change_remark IS '变更备注';

-- 业务层强制 codereview 同厂区不重复编码，SQL 层以部分唯一索引做兜底
CREATE UNIQUE INDEX IF NOT EXISTS uq_spc_process_code_plant
    ON qms.spc_process (process_code, plant_code) WHERE is_deleted = 0;

-- 2. 参数字典表增强（规格/子组/控制图字段保留不动，供物料‑工序‑参数标准层使用）
ALTER TABLE qms.spc_parameter
    ADD COLUMN IF NOT EXISTS decimal_places INTEGER DEFAULT 3,
    ADD COLUMN IF NOT EXISTS is_critical VARCHAR(8) DEFAULT '否',
    ADD COLUMN IF NOT EXISTS change_remark VARCHAR(500);

COMMENT ON COLUMN qms.spc_parameter.decimal_places IS '小数位数';
COMMENT ON COLUMN qms.spc_parameter.is_critical IS '是否关键特性：是/否';
COMMENT ON COLUMN qms.spc_parameter.change_remark IS '变更备注';
