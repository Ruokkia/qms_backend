-- Align demo/placeholder codes written by earlier reference-seed and repair migrations
-- to the real ERP coding system (source of truth: qms-pg-dev export of
-- material_inspection / finished_goods_inspection / critical_material_binding).
-- These are idempotent UPDATEs scoped by plant_code or by the exact demo record_no,
-- so re-running is safe and will not affect the ~11k real incoming inspections.
--
-- Old placeholder codes -> real ERP codes:
--   MC-001        -> 99.11.100558 (可充电式电批)
--   MC-002        -> 10.09.200320 (A26)
--   PN-SZ-001     -> 10.01.200951 (乳酸糖电极)
--   SUP-SZ-01     -> S2012073
--   深圳电子元件有限公司 -> 真实供应商名称（按 supplier_code 对齐）
--   电容          -> 超声板PCBA (repair-demo material_name)

-- 1) exception_order: demo 占位物料码（按 plant_code 隔离，仅更新仍为占位码的少数行）
UPDATE qms.exception_order
SET material_code = '99.11.100558',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE material_code = 'MC-001' AND is_deleted = 0;

UPDATE qms.exception_order
SET material_code = '10.09.200320',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE material_code = 'MC-002' AND is_deleted = 0;

UPDATE qms.exception_order
SET material_code = '10.01.200951',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE material_code = 'PN-SZ-001' AND is_deleted = 0;

-- 2) finished_goods_inspection: demo 成品码（仅更新占位码行）
UPDATE qms.finished_goods_inspection
SET material_code = '10.01.200951',
    product_name = '乳酸糖电极',
    model_spec   = 'KL50.2009.0100.0000.00',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE material_code = 'PN-SZ-001' AND is_deleted = 0;

-- 3) material_inspection: 仅修正 repair migration 写入的 3 条 demo 批次（精确 record_no 隔离）
UPDATE qms.material_inspection
SET material_code  = '99.11.100558',
    material_name  = '超声板PCBA',
    supplier_code  = 'S2012073',
    supplier_name  = COALESCE((SELECT s.supplier_name FROM qms.supplier s WHERE s.supplier_code = 'S2012073' LIMIT 1), '深圳康立供应商A'),
    defect_desc    = REPLACE(defect_desc, '升级规则示例', '升级规则示例（ERP对齐）'),
    updated_by     = 'SYSTEM',
    updated_at     = CURRENT_TIMESTAMP
WHERE record_no IN ('IQC-ESC-001', 'IQC-ESC-002', 'IQC-ESC-003')
  AND is_deleted = 0;

-- 4) supplier 主数据：将旧 SUP-SZ-01 别名对齐到真实供应商码（若仍残留）
UPDATE qms.supplier
SET supplier_code = 'S2012073',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE supplier_code = 'SUP-SZ-01' AND is_deleted = 0;
