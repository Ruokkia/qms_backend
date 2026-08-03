-- FAI 首件检验记录：补充产品/物料名称列（从变更触发复制），解决产品名称展示为空的问题
ALTER TABLE qms.fai_inspection_record
    ADD COLUMN IF NOT EXISTS item_name character varying(128);

-- 既有数据：以 material_name 回填，与 V20260801100000003 的 item_code = material_code 对称
UPDATE qms.fai_inspection_record
SET item_name = material_name
WHERE item_name IS NULL
  AND material_name IS NOT NULL
  AND is_deleted = 0;

COMMENT ON COLUMN qms.fai_inspection_record.item_name IS '产品/物料名称（随 item_type 取值）';
