-- SPC 子组：支持产品/物料二选一分类，用于控制图按代码关联
-- 新增 item_type(产品/物料), item_code 列

ALTER TABLE qms.spc_subgroup
    ADD COLUMN IF NOT EXISTS item_type character varying(16),
    ADD COLUMN IF NOT EXISTS item_code character varying(64);

-- 既有数据：material_code 非空视为物料场景，回填 item_type/item_code
UPDATE qms.spc_subgroup
SET item_type = 'MATERIAL',
    item_code = material_code
WHERE item_type IS NULL
  AND material_code IS NOT NULL
  AND is_deleted = 0;

COMMENT ON COLUMN qms.spc_subgroup.item_type IS '分类：PRODUCT(产品)/MATERIAL(物料)';
COMMENT ON COLUMN qms.spc_subgroup.item_code IS '产品/物料代码（随 item_type 取值，控制图关联维度）';
