-- ============================================================
-- 深圳追溯验收演示数据：来料检验、成品检验、追溯节点与关系
-- 可重复执行：以业务编号、条码和父子关系去重，不删除已有数据。
-- ============================================================

INSERT INTO qms.material_inspection (
    record_no, material_code, material_name, spec_model, material_batch_no,
    inspection_result, supplier_name, supplier_code, qualified_qty, unqualified_qty,
    submitted_qty, unit, handling_method, defect_desc, review_status,
    signature_status, is_urgent, is_valid, purchase_order, inspection_date,
    inspector, reviewer, review_date, arrival_date, plant_code, plant_name, created_by, updated_by
)
SELECT *
FROM (VALUES
    ('IQC-SZD-20260701', 'SZD-MAT-RES',    '精密贴片电阻',   '0603 10K 1%',      'SZD-LOT-20260722', '不合格', '深科电子元件有限公司', 'SUP-SZD-01',  950,  50, 1000, 'PCS', '挑选', '阻值漂移超出规格上限', '已审核', '已签', '否', '是', 'PO-SZD-001', DATE '2026-07-18', '李四', '赵六', DATE '2026-07-18', DATE '2026-07-17', 'SZ', '深圳', '李四', '赵六'),
    ('IQC-SZD-20260702', 'SZD-MAT-CAP',    '低阻抗电容',     '0805 22uF 16V',    'SZD-LOT-20260721', '合格',   '深科电子元件有限公司', 'SUP-SZD-01', 2000,   0, 2000, 'PCS', NULL,   NULL,                 '已审核', '已签', '否', '是', 'PO-SZD-002', DATE '2026-07-18', '李四', '赵六', DATE '2026-07-18', DATE '2026-07-17', 'SZ', '深圳', '李四', '赵六'),
    ('IQC-SZD-20260703', 'SZD-MAT-PCB',    '监护仪主控板',   '6层 HDI',          'SZD-LOT-20260722', '不合格', '华南电路科技有限公司', 'SUP-SZD-02',  780, 220, 1000, 'PCS', '退货', '阻焊露铜及孔位偏移',   '已审核', '已签', '是', '是', 'PO-SZD-003', DATE '2026-07-19', '王五', '赵六', DATE '2026-07-19', DATE '2026-07-18', 'SZ', '深圳', '王五', '赵六'),
    ('IQC-SZD-20260704', 'SZD-MAT-IC',     '信号处理芯片',   'AFE4490',          'SZD-LOT-20260720', '合格',   '华芯半导体有限公司',   'SUP-SZD-03', 1500,   0, 1500, 'PCS', NULL,   NULL,                 '待审核', '未签', '否', '是', 'PO-SZD-004', DATE '2026-07-19', '王五', NULL,   NULL,               DATE '2026-07-18', 'SZ', '深圳', '王五', '王五'),
    ('IQC-SZD-20260705', 'SZD-MAT-CON',    '医疗级连接器',   '2.54mm 20P',       'SZD-LOT-20260723', '合格',   '深科电子元件有限公司', 'SUP-SZD-01', 1200,   0, 1200, 'PCS', NULL,   NULL,                 '已审核', '已签', '是', '是', 'PO-SZD-005', DATE '2026-07-20', '李四', '赵六', DATE '2026-07-20', DATE '2026-07-19', 'SZ', '深圳', '李四', '赵六'),
    ('IQC-SZD-20260706', 'SZD-MAT-TUBE',   '医用硅胶管',     '内径 3mm',         'SZD-LOT-20260723', '合格',   '华南电路科技有限公司', 'SUP-SZD-02',  800,   0,  800, 'M',   NULL,   NULL,                 '待审核', '未签', '否', '是', 'PO-SZD-006', DATE '2026-07-20', '王五', NULL,   NULL,               DATE '2026-07-19', 'SZ', '深圳', '王五', '王五'),
    ('IQC-SZD-20260707', 'SZD-MAT-SHELL',  '阻燃外壳',       'PC+ABS V0',        'SZD-LOT-20260724', '合格',   '粤东精密注塑有限公司', 'SUP-SZD-04',  600,   0,  600, 'PCS', NULL,   NULL,                 '已审核', '已签', '否', '是', 'PO-SZD-007', DATE '2026-07-21', '李四', '赵六', DATE '2026-07-21', DATE '2026-07-20', 'SZ', '深圳', '李四', '赵六'),
    ('IQC-SZD-20260708', 'SZD-MAT-SENSOR', '压力传感器',     '0-40kPa',          'SZD-LOT-20260724', '不合格', '粤东精密注塑有限公司', 'SUP-SZD-04',  470,  30,  500, 'PCS', '特采', '零点漂移，需加严筛选', '待审核', '未签', '是', '是', 'PO-SZD-008', DATE '2026-07-21', '王五', NULL,   NULL,               DATE '2026-07-20', 'SZ', '深圳', '王五', '王五'),
    ('IQC-SZD-20260709', 'SZD-MAT-CAP',    '低阻抗电容',     '0805 22uF 16V',    'SZD-LOT-20260725', '合格',   '深科电子元件有限公司', 'SUP-SZD-01', 1800,   0, 1800, 'PCS', NULL,   NULL,                 '已审核', '已签', '否', '是', 'PO-SZD-009', DATE '2026-07-22', '李四', '赵六', DATE '2026-07-22', DATE '2026-07-21', 'SZ', '深圳', '李四', '赵六'),
    ('IQC-SZD-20260710', 'SZD-MAT-RES',    '精密贴片电阻',   '0603 10K 1%',      'SZD-LOT-20260726', '合格',   '华南电路科技有限公司', 'SUP-SZD-02', 1600,   0, 1600, 'PCS', NULL,   NULL,                 '已审核', '已签', '否', '是', 'PO-SZD-010', DATE '2026-07-22', '王五', '赵六', DATE '2026-07-22', DATE '2026-07-21', 'SZ', '深圳', '王五', '赵六'),
    ('IQC-SZD-20260711', 'SZD-MAT-PCB',    '监护仪主控板',   '6层 HDI',          'SZD-LOT-20260727', '合格',   '华芯半导体有限公司',   'SUP-SZD-03',  900,   0,  900, 'PCS', NULL,   NULL,                 '待审核', '未签', '否', '是', 'PO-SZD-011', DATE '2026-07-23', '李四', NULL,   NULL,               DATE '2026-07-22', 'SZ', '深圳', '李四', '李四'),
    ('IQC-SZD-20260712', 'SZD-MAT-IC',     '信号处理芯片',   'AFE4490',          'SZD-LOT-20260728', '不合格', '粤东精密注塑有限公司', 'SUP-SZD-04',  980,  20, 1000, 'PCS', '挑选', '引脚共面度异常',       '已审核', '已签', '是', '是', 'PO-SZD-012', DATE '2026-07-24', '王五', '赵六', DATE '2026-07-24', DATE '2026-07-23', 'SZ', '深圳', '王五', '赵六')
) AS seed(record_no, material_code, material_name, spec_model, material_batch_no, inspection_result, supplier_name, supplier_code, qualified_qty, unqualified_qty, submitted_qty, unit, handling_method, defect_desc, review_status, signature_status, is_urgent, is_valid, purchase_order, inspection_date, inspector, reviewer, review_date, arrival_date, plant_code, plant_name, created_by, updated_by)
WHERE NOT EXISTS (SELECT 1 FROM qms.material_inspection existing WHERE existing.record_no = seed.record_no AND existing.is_deleted = 0);

INSERT INTO qms.finished_goods_inspection (
    report_no, inspection_request_no, production_order_no, material_code, product_name,
    model_spec, prod_batch_or_sn, production_date, submitted_qty, inspected_qty,
    qualified_qty, unqualified_qty, unit, inspector_name, category, inspection_result,
    qc_review, mgr_approval, is_urgent, is_valid, plant_code, plant_name, created_by, updated_by
)
SELECT *
FROM (VALUES
    ('FGR-SZD-20260701', 'FIR-SZD-001', 'WO-SZD-001', 'SZD-FG-MON',  '深圳监护仪标准版', 'QMS-M900', 'SZD-FG-001', DATE '2026-07-22', 120, 120, 120, 0, '台', '赵六', '成品', '合格',   '已审核', '已审核', '否', '是', 'SZ', '深圳', '赵六', '钱七'),
    ('FGR-SZD-20260702', 'FIR-SZD-002', 'WO-SZD-002', 'SZD-FG-MON',  '深圳监护仪增强版', 'QMS-M900', 'SZD-FG-002', DATE '2026-07-22', 100, 100,  98, 2, '台', '赵六', '成品', '不合格', '已审核', '驳回',   '是', '是', 'SZ', '深圳', '赵六', '钱七'),
    ('FGR-SZD-20260703', 'FIR-SZD-003', 'WO-SZD-003', 'SZD-FG-OX',   '血氧监测模块',     'QMS-OX100','SZD-FG-003', DATE '2026-07-23', 150, 150, 150, 0, '台', '赵六', '成品', '合格',   '待审核', '待审核', '否', '是', 'SZ', '深圳', '赵六', '赵六'),
    ('FGR-SZD-20260704', 'FIR-SZD-004', 'WO-SZD-004', 'SZD-FG-BP',   '血压监测模块',     'QMS-BP50', 'SZD-FG-004', DATE '2026-07-23',  80,  80,  78, 2, '台', '赵六', '成品', '不合格', '已审核', '驳回',   '是', '是', 'SZ', '深圳', '赵六', '钱七'),
    ('FGR-SZD-20260705', 'FIR-SZD-005', 'WO-SZD-005', 'SZD-FG-ECG',  '心电监测模块',     'QMS-ECG2', 'SZD-FG-005', DATE '2026-07-24',  90,  90,  90, 0, '台', '赵六', '成品', '合格',   '已审核', '已审核', '否', '是', 'SZ', '深圳', '赵六', '钱七'),
    ('FGR-SZD-20260706', 'FIR-SZD-006', 'WO-SZD-006', 'SZD-FG-FLUID','流路控制组件',     'QMS-FL20', 'SZD-FG-006', DATE '2026-07-24', 110, 110, 110, 0, '台', '赵六', '成品', '合格',   '待审核', '待审核', '否', '是', 'SZ', '深圳', '赵六', '赵六')
) AS seed(report_no, inspection_request_no, production_order_no, material_code, product_name, model_spec, prod_batch_or_sn, production_date, submitted_qty, inspected_qty, qualified_qty, unqualified_qty, unit, inspector_name, category, inspection_result, qc_review, mgr_approval, is_urgent, is_valid, plant_code, plant_name, created_by, updated_by)
WHERE NOT EXISTS (SELECT 1 FROM qms.finished_goods_inspection existing WHERE existing.report_no = seed.report_no AND existing.is_deleted = 0);

INSERT INTO qms.trace_node (node_type, barcode, name, product_code, material_code, material_batch_no, specification)
SELECT *
FROM (VALUES
    ('MATERIAL',      'SZD-MAT-RES-LOT-20260722',    '精密贴片电阻批次',   NULL,          'SZD-MAT-RES',    'SZD-LOT-20260722', '0603 10K 1%'),
    ('MATERIAL',      'SZD-MAT-CAP-LOT-20260721',    '低阻抗电容批次',     NULL,          'SZD-MAT-CAP',    'SZD-LOT-20260721', '0805 22uF 16V'),
    ('MATERIAL',      'SZD-MAT-PCB-LOT-20260722',    '监护仪主控板批次',   NULL,          'SZD-MAT-PCB',    'SZD-LOT-20260722', '6层 HDI'),
    ('MATERIAL',      'SZD-MAT-IC-LOT-20260720',     '信号处理芯片批次',   NULL,          'SZD-MAT-IC',     'SZD-LOT-20260720', 'AFE4490'),
    ('MATERIAL',      'SZD-MAT-CON-LOT-20260723',    '医疗级连接器批次',   NULL,          'SZD-MAT-CON',    'SZD-LOT-20260723', '2.54mm 20P'),
    ('MATERIAL',      'SZD-MAT-TUBE-LOT-20260723',   '医用硅胶管批次',     NULL,          'SZD-MAT-TUBE',   'SZD-LOT-20260723', '内径 3mm'),
    ('MATERIAL',      'SZD-MAT-SHELL-LOT-20260724',  '阻燃外壳批次',       NULL,          'SZD-MAT-SHELL',  'SZD-LOT-20260724', 'PC+ABS V0'),
    ('MATERIAL',      'SZD-MAT-SENSOR-LOT-20260724', '压力传感器批次',     NULL,          'SZD-MAT-SENSOR', 'SZD-LOT-20260724', '0-40kPa'),
    ('SEMI_FINISHED', 'SZD-SF-BOARD-A',              '监护仪控制板 A',     'SZD-SF-BOARD-A', NULL,             NULL,                 '控制板组件'),
    ('SEMI_FINISHED', 'SZD-SF-BOARD-B',              '监护仪控制板 B',     'SZD-SF-BOARD-B', NULL,             NULL,                 '控制板组件'),
    ('SEMI_FINISHED', 'SZD-SF-FLUID',                '流路控制半成品',     'SZD-SF-FLUID',   NULL,             NULL,                 '流路组件'),
    ('SEMI_FINISHED', 'SZD-SF-SHELL',                '监护仪外壳半成品',   'SZD-SF-SHELL',   NULL,             NULL,                 '外壳组件'),
    ('SEMI_FINISHED', 'SZD-SF-SENSOR',               '压力检测半成品',     'SZD-SF-SENSOR',  NULL,             NULL,                 '传感组件'),
    ('FINISHED_GOOD', 'SZD-FG-001',                  '深圳监护仪标准版',   'SZD-FG-MON',     NULL,             NULL,                 'QMS-M900'),
    ('FINISHED_GOOD', 'SZD-FG-002',                  '深圳监护仪增强版',   'SZD-FG-MON',     NULL,             NULL,                 'QMS-M900'),
    ('FINISHED_GOOD', 'SZD-FG-003',                  '血氧监测模块',       'SZD-FG-OX',      NULL,             NULL,                 'QMS-OX100'),
    ('FINISHED_GOOD', 'SZD-FG-004',                  '血压监测模块',       'SZD-FG-BP',      NULL,             NULL,                 'QMS-BP50'),
    ('FINISHED_GOOD', 'SZD-FG-005',                  '心电监测模块',       'SZD-FG-ECG',     NULL,             NULL,                 'QMS-ECG2'),
    ('FINISHED_GOOD', 'SZD-FG-006',                  '流路控制组件',       'SZD-FG-FLUID',   NULL,             NULL,                 'QMS-FL20')
) AS seed(node_type, barcode, name, product_code, material_code, material_batch_no, specification)
WHERE NOT EXISTS (SELECT 1 FROM qms.trace_node existing WHERE existing.barcode = seed.barcode);

INSERT INTO qms.trace_relation (parent_node_id, child_node_id, quantity, work_order_no, process_name)
SELECT parent.id, child.id, seed.quantity, seed.work_order_no, seed.process_name
FROM (VALUES
    ('SZD-SF-BOARD-A', 'SZD-MAT-RES-LOT-20260722', 12.000, 'WO-SZD-001', '贴片'),
    ('SZD-SF-BOARD-A', 'SZD-MAT-CAP-LOT-20260721', 18.000, 'WO-SZD-001', '贴片'),
    ('SZD-SF-BOARD-A', 'SZD-MAT-IC-LOT-20260720',   1.000, 'WO-SZD-001', '贴片'),
    ('SZD-SF-BOARD-B', 'SZD-MAT-RES-LOT-20260722', 10.000, 'WO-SZD-002', '贴片'),
    ('SZD-SF-BOARD-B', 'SZD-MAT-PCB-LOT-20260722',  1.000, 'WO-SZD-002', '组装'),
    ('SZD-SF-BOARD-B', 'SZD-MAT-CAP-LOT-20260721', 16.000, 'WO-SZD-002', '贴片'),
    ('SZD-SF-FLUID',   'SZD-MAT-TUBE-LOT-20260723', 2.000, 'WO-SZD-003', '流路装配'),
    ('SZD-SF-FLUID',   'SZD-MAT-CON-LOT-20260723',  4.000, 'WO-SZD-003', '流路装配'),
    ('SZD-SF-SHELL',   'SZD-MAT-SHELL-LOT-20260724',1.000, 'WO-SZD-004', '外壳装配'),
    ('SZD-SF-SENSOR',  'SZD-MAT-SENSOR-LOT-20260724',1.000,'WO-SZD-005', '检测'),
    ('SZD-FG-001',     'SZD-SF-BOARD-A',            1.000, 'WO-SZD-001', '总装'),
    ('SZD-FG-001',     'SZD-SF-FLUID',              1.000, 'WO-SZD-001', '总装'),
    ('SZD-FG-002',     'SZD-SF-BOARD-B',            1.000, 'WO-SZD-002', '总装'),
    ('SZD-FG-002',     'SZD-SF-FLUID',              1.000, 'WO-SZD-002', '总装'),
    ('SZD-FG-003',     'SZD-SF-BOARD-A',            1.000, 'WO-SZD-003', '总装'),
    ('SZD-FG-003',     'SZD-SF-SHELL',              1.000, 'WO-SZD-003', '总装'),
    ('SZD-FG-004',     'SZD-SF-BOARD-B',            1.000, 'WO-SZD-004', '总装'),
    ('SZD-FG-004',     'SZD-SF-SENSOR',             1.000, 'WO-SZD-004', '总装'),
    ('SZD-FG-005',     'SZD-SF-BOARD-A',            1.000, 'WO-SZD-005', '总装'),
    ('SZD-FG-006',     'SZD-SF-FLUID',              1.000, 'WO-SZD-006', '总装')
) AS seed(parent_barcode, child_barcode, quantity, work_order_no, process_name)
JOIN qms.trace_node parent ON parent.barcode = seed.parent_barcode
JOIN qms.trace_node child ON child.barcode = seed.child_barcode
WHERE NOT EXISTS (
    SELECT 1 FROM qms.trace_relation existing
    WHERE existing.parent_node_id = parent.id
      AND existing.child_node_id = child.id
      AND existing.work_order_no = seed.work_order_no
);
