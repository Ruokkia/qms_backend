-- FAI 检验标准模板：支持产品/物料二选一分类，区分产品与物料标准模板
ALTER TABLE qms.fai_inspection_standard
    ADD COLUMN IF NOT EXISTS item_type character varying(16),
    ADD COLUMN IF NOT EXISTS item_code character varying(64),
    ADD COLUMN IF NOT EXISTS item_name character varying(128);

-- 既有数据：material_code 非空视为物料场景回填
UPDATE qms.fai_inspection_standard
SET item_type = 'MATERIAL',
    item_code = material_code,
    item_name = material_name
WHERE item_type IS NULL
  AND material_code IS NOT NULL
  AND is_deleted = 0;

COMMENT ON COLUMN qms.fai_inspection_standard.item_type IS '分类：PRODUCT(产品)/MATERIAL(物料)';
COMMENT ON COLUMN qms.fai_inspection_standard.item_code IS '产品/物料代码（随 item_type 取值）';
COMMENT ON COLUMN qms.fai_inspection_standard.item_name IS '产品/物料名称（随 item_type 取值）';
