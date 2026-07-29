-- M1-3 关键物料绑定自然键唯一索引（纵深防御，对齐成品 uq_fgi_report_no）。
-- 自然键：(work_order_no, product_barcode, material_barcode, process_code)
-- 说明：material_barcode 为空时由服务层回退 material_code 判定（PostgreSQL 部分唯一索引对 NULL 不判重）。
-- 当前种子数据自然键无冲突，可安全创建。
DO $$
BEGIN
    IF to_regclass('qms.critical_material_binding') IS NOT NULL THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS uq_cmb_nat_key '
             || 'ON qms.critical_material_binding USING btree '
             || '(work_order_no, product_barcode, material_barcode, process_code) '
             || 'WHERE (is_deleted = 0)';
    END IF;
END $$;
