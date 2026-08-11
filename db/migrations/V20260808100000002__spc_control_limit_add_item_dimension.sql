-- 为 spc_control_limit 增加 item_type / item_code 维度列，
-- 使控制限可按 产品/物料代码 维度隔离（避免不同 item 的控制限互相污染）。
-- 纯结构修改，不删不改任何业务数据（遵守 Flyway 铁律）。
ALTER TABLE qms.spc_control_limit
    ADD COLUMN IF NOT EXISTS item_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS item_code VARCHAR(128);

COMMENT ON COLUMN qms.spc_control_limit.item_type IS '关联维度：PRODUCT/MATERIAL，为空表示全局';
COMMENT ON COLUMN qms.spc_control_limit.item_code IS '关联产品/物料代码，为空表示全局';

-- 历史存量控制限无维度信息，保持原样（item_type/item_code 为 NULL），
-- 仍按 param+plant+chartType 取最新，兼容既有全量视图。
