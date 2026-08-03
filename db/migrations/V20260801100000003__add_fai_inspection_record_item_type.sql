-- FAI 首件检验记录：支持产品/物料二选一分类（从变更触发复制）
ALTER TABLE qms.fai_inspection_record
    ADD COLUMN IF NOT EXISTS item_type character varying(16),
    ADD COLUMN IF NOT EXISTS item_code character varying(64);

-- 既有数据：material_code 非空视为物料场景回填
UPDATE qms.fai_inspection_record
SET item_type = 'MATERIAL',
    item_code = material_code
WHERE item_type IS NULL
  AND material_code IS NOT NULL
  AND is_deleted = 0;

COMMENT ON COLUMN qms.fai_inspection_record.item_type IS '分类：PRODUCT(产品)/MATERIAL(物料)';
COMMENT ON COLUMN qms.fai_inspection_record.item_code IS '产品/物料代码（随 item_type 取值）';
