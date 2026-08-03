-- FAI 变更触发：支持产品/物料二选一分类，移除工单号
-- 新增 item_type(产品/物料), item_code, item_name, item_barcode 列
-- 删除 work_order_no 列（按业务需求直接移除）
-- 将既有 material_code/material_name 视为物料场景兼容数据

ALTER TABLE qms.fai_change_trigger
    ADD COLUMN IF NOT EXISTS item_type character varying(16),
    ADD COLUMN IF NOT EXISTS item_code character varying(64),
    ADD COLUMN IF NOT EXISTS item_name character varying(128),
    ADD COLUMN IF NOT EXISTS item_barcode character varying(64);

-- 既有数据：material_code 非空视为物料场景，回填 item_type/item_code/item_name
UPDATE qms.fai_change_trigger
SET item_type  = 'MATERIAL',
    item_code  = material_code,
    item_name  = material_name
WHERE item_type IS NULL
  AND material_code IS NOT NULL
  AND is_deleted = 0;

-- 移除工单号列
ALTER TABLE qms.fai_change_trigger DROP COLUMN IF EXISTS work_order_no;

COMMENT ON COLUMN qms.fai_change_trigger.item_type IS '分类：PRODUCT(产品)/MATERIAL(物料)';
COMMENT ON COLUMN qms.fai_change_trigger.item_code IS '产品/物料代码（随 item_type 取值）';
COMMENT ON COLUMN qms.fai_change_trigger.item_name IS '产品/物料名称（随 item_type 取值）';
COMMENT ON COLUMN qms.fai_change_trigger.item_barcode IS '产品/物料条码（随 item_type 取值）';
