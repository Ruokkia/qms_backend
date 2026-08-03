-- 追溯根节点和批量加载按分公司+物料条码查询，补充复合索引避免全表扫描。
CREATE INDEX IF NOT EXISTS idx_mi_plant_barcode
    ON qms.material_inspection (plant_code, material_barcode)
    WHERE is_deleted = 0 AND material_barcode IS NOT NULL;
