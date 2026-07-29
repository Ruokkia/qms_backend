-- Repair seed-data supplier codes so incoming inspections can resolve to supplier master data.
UPDATE qms.supplier AS s
SET supplier_name = v.supplier_name,
    risk_level = v.risk_level,
    status = '启用',
    is_deleted = 0,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('SUP-SZD-01', '深科电子元件有限公司', '高'),
    ('SUP-SZD-02', '华南电路科技有限公司', '高'),
    ('SUP-SZD-03', '华芯半导体有限公司', '中'),
    ('SUP-SZD-04', '粤东精密注塑有限公司', '中')
) AS v(supplier_code, supplier_name, risk_level)
WHERE s.supplier_code = v.supplier_code;

INSERT INTO qms.supplier
    (supplier_code, supplier_name, risk_level, status, plant_code, plant_name, created_by, updated_by)
SELECT v.supplier_code, v.supplier_name, v.risk_level, '启用', 'SZ', '深圳', 'SYSTEM', 'SYSTEM'
FROM (VALUES
    ('SUP-SZD-01', '深科电子元件有限公司', '高'),
    ('SUP-SZD-02', '华南电路科技有限公司', '高'),
    ('SUP-SZD-03', '华芯半导体有限公司', '中'),
    ('SUP-SZD-04', '粤东精密注塑有限公司', '中')
) AS v(supplier_code, supplier_name, risk_level)
WHERE NOT EXISTS (
    SELECT 1 FROM qms.supplier s WHERE s.supplier_code = v.supplier_code
);

-- Keep the legacy exception demo truthful: it is a single recorded occurrence, not a live third occurrence.
UPDATE qms.exception_order
SET defect_desc = '电容容值偏差超标（历史示例异常）',
    repeat_count_30_days = 1,
    repeat_count_90_days = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE exception_no = 'EX-20260715-001'
  AND material_code = 'MC-001';

-- Provide three rolling-window demo batches so the default 90-day / 3-batch check has a real candidate.
INSERT INTO qms.material_inspection
    (record_no, inspection_date, inspection_result, supplier_name, supplier_code,
     material_code, material_name, material_batch_no, qualified_qty, unqualified_qty,
     submitted_qty, unit, defect_desc, plant_code, plant_name, created_by, updated_by)
SELECT v.record_no, v.inspection_date, '不合格', '深圳电子元件有限公司', 'SUP-SZ-01',
       'MC-001', '电容', v.material_batch_no, 490, 10, 500, 'PCS',
       '容值偏差超标（升级规则示例）', 'SZ', '深圳', 'SYSTEM', 'SYSTEM'
FROM (VALUES
    ('IQC-ESC-001', CURRENT_DATE - 3, 'ESC-MC001-001'),
    ('IQC-ESC-002', CURRENT_DATE - 2, 'ESC-MC001-002'),
    ('IQC-ESC-003', CURRENT_DATE - 1, 'ESC-MC001-003')
) AS v(record_no, inspection_date, material_batch_no)
WHERE NOT EXISTS (
    SELECT 1 FROM qms.material_inspection m WHERE m.record_no = v.record_no
);
