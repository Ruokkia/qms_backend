-- 将厂区维度唯一索引改为普通索引。
-- 保留索引字段及软删除过滤条件，仅取消唯一性约束，兼容已执行
-- V20260811104811469 的数据库。

DROP INDEX IF EXISTS qms.uq_mi_material_barcode_plant;
CREATE INDEX uq_mi_material_barcode_plant
    ON qms.material_inspection USING btree (plant_code, material_barcode)
    WHERE (material_barcode IS NOT NULL AND is_deleted = 0);

DROP INDEX IF EXISTS qms.uq_mi_record_no_plant;
CREATE INDEX uq_mi_record_no_plant
    ON qms.material_inspection USING btree (plant_code, record_no)
    WHERE (is_deleted = 0);

DROP INDEX IF EXISTS qms.uq_cmb_nat_key_plant;
CREATE INDEX uq_cmb_nat_key_plant
    ON qms.critical_material_binding USING btree (plant_code, work_order_no, product_barcode, material_barcode)
    WHERE (is_deleted = 0);
