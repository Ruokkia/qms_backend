-- 唯一索引厂区隔离改造 + 删除绑定表无用字段 process_code/process_name
-- 1) material_inspection：条码/报告号唯一索引加上 plant_code（不同厂区允许相同条码）
-- 2) critical_material_binding：自然键唯一索引加上 plant_code，去掉 process_code
-- 3) finished_goods_inspection：删除旧的全局唯一索引 uq_fgi_report_no（已有含 plant_code 的 uq_finished_goods_report_no_active）

-- ========================================================================
-- material_inspection：材料条码唯一索引 → 加 plant_code
-- ========================================================================
DROP INDEX IF EXISTS qms.uq_mi_material_barcode;
CREATE UNIQUE INDEX uq_mi_material_barcode_plant
    ON qms.material_inspection USING btree (plant_code, material_barcode)
    WHERE (material_barcode IS NOT NULL AND is_deleted = 0);

-- ========================================================================
-- material_inspection：报告号唯一索引 → 加 plant_code
-- ========================================================================
DROP INDEX IF EXISTS qms.uq_mi_record_no;
CREATE UNIQUE INDEX uq_mi_record_no_plant
    ON qms.material_inspection USING btree (plant_code, record_no)
    WHERE (is_deleted = 0);

-- ========================================================================
-- finished_goods_inspection：删除旧的全局唯一索引（已有含 plant_code 的索引）
-- ========================================================================
DROP INDEX IF EXISTS qms.uq_fgi_report_no;

-- ========================================================================
-- critical_material_binding：自然键唯一索引 → 加 plant_code、去掉 process_code
-- ========================================================================
DROP INDEX IF EXISTS qms.uq_cmb_nat_key;
CREATE UNIQUE INDEX uq_cmb_nat_key_plant
    ON qms.critical_material_binding USING btree (plant_code, work_order_no, product_barcode, material_barcode)
    WHERE (is_deleted = 0);

-- ========================================================================
-- critical_material_binding：删除无用字段 process_code / process_name
-- ========================================================================
ALTER TABLE qms.critical_material_binding DROP COLUMN IF EXISTS process_code;
ALTER TABLE qms.critical_material_binding DROP COLUMN IF EXISTS process_name;
