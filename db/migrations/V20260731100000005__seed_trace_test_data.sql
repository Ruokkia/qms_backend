-- 清除业务表旧孤立数据（trace_relation 已在迁移 4 中 DROP，这些记录失去了关联关系），
-- 然后插入一套完整的模拟追溯数据，覆盖 成品→半成品→物料 三级链路。

-- ============================================================
-- 1. 清空旧数据 + 重置序列
-- ============================================================

TRUNCATE TABLE qms.critical_material_binding RESTART IDENTITY CASCADE;
TRUNCATE TABLE qms.finished_goods_inspection RESTART IDENTITY CASCADE;
TRUNCATE TABLE qms.material_inspection       RESTART IDENTITY CASCADE;

-- ============================================================
-- 2. 成品 / 半成品（finished_goods_inspection）
--    追溯链路：
--      成品 KL-DSO-2024-001（数字示波器）
--        ├─ 半成品 KL-PCB-A01-2024-001（主控板组件）
--        └─ 半成品 KL-PWR-B01-2024-001（电源模块）
-- ============================================================

INSERT INTO qms.finished_goods_inspection
    (report_no, inspection_request_no, production_order_no,
     material_code, product_name, model_spec, prod_batch_or_sn,
     production_date, submitted_qty, inspected_qty, qualified_qty, unqualified_qty, unit,
     inspector_name, category, inspection_result,
     qc_review, mgr_approval, is_valid, is_urgent, is_entrusted,
     plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
VALUES
-- 成品：数字示波器
('FG-2024-0001', 'SJR-20240315-001', 'WO-2024-0301',
 'FG-DSO-2024', '康立数字示波器 KL-DSO-2024', 'KL-DSO-2024', 'KL-DSO-2024-001',
 '2024-03-15', 10.000, 10.000, 9.000, 1.000, '台',
 '张工', '成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 半成品：主控板组件
('SFG-2024-0001', 'SJR-20240310-001', 'WO-2024-0280',
 'SFG-PCB-A01', '主控板组件 PCB-A01', 'PCB-A01-REV3', 'KL-PCB-A01-2024-001',
 '2024-03-10', 20.000, 20.000, 19.000, 1.000, '块',
 '李工', '半成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 半成品：电源模块
('SFG-2024-0002', 'SJR-20240310-002', 'WO-2024-0281',
 'SFG-PWR-B01', '电源模块 PWR-B01', 'PWR-B01-REV2', 'KL-PWR-B01-2024-001',
 '2024-03-10', 20.000, 20.000, 20.000, 0.000, '块',
 '李工', '半成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now());

-- ============================================================
-- 3. 物料（material_inspection）— 含 material_barcode
--    4 种物料，条码全局唯一，参与 SN 级追溯
-- ============================================================

INSERT INTO qms.material_inspection
    (record_no, material_category, material_code, material_name, spec_model,
     material_batch_no, material_barcode,
     inspection_date, supplier_name, supplier_code,
     submitted_qty, qualified_qty, unqualified_qty, unit,
     inspector, inspection_result, arrival_date,
     review_status, signature_status,
     is_valid, is_urgent, is_customer_supplied, data_record_flag, is_invalid, report_generated,
     plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
VALUES
-- 物料1：ARM处理器
('MI-2024-0101', 'IC', 'IC-STM32H750', 'ARM处理器 STM32H750', 'STM32H750VBT6',
 'B-STM32H750-240301', 'MAT-IC-20240301-001',
 '2024-03-01', '意法半导体(上海)', 'SUP-001',
 100.000, 98.000, 2.000, '个',
 '王工', '合格', '2024-02-28',
 '已审核', '已签',
 '是', '否', '否', '否', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 物料2：DDR4内存
('MI-2024-0102', 'IC', 'IC-MT40A512', 'DDR4内存 MT40A512', 'MT40A512M16TB-062',
 'B-MT40A512-240301', 'MAT-IC-20240301-002',
 '2024-03-01', '美光科技(上海)', 'SUP-002',
 100.000, 100.000, 0.000, '个',
 '王工', '合格', '2024-02-28',
 '已审核', '已签',
 '是', '否', '否', '否', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 物料3：电源芯片
('MI-2024-0103', 'IC', 'IC-LM2596', '电源芯片 LM2596', 'LM2596S-5.0',
 'B-LM2596-240305', 'MAT-IC-20240301-003',
 '2024-03-05', '德州仪器(深圳)', 'SUP-003',
 200.000, 195.000, 5.000, '个',
 '赵工', '合格', '2024-03-04',
 '已审核', '已签',
 '是', '否', '否', '否', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 物料4：OLED显示屏
('MI-2024-0104', 'DISPLAY', 'DIS-SSD1306', 'OLED显示屏 SSD1306', 'SSD1306-0.96-I2C',
 'B-SSD1306-240308', 'MAT-DIS-20240301-004',
 '2024-03-08', '中景园电子(深圳)', 'SUP-004',
 50.000, 50.000, 0.000, '块',
 '赵工', '合格', '2024-03-07',
 '已审核', '已签',
 '是', '否', '否', '否', '否', '否',
 'SZ', '深圳分公司', 'system', 0, 1, now(), now());

-- ============================================================
-- 4. 绑定关系（critical_material_binding）— 6 条
--    category = "半成品" → 子类在 finished_goods_inspection 中查找
--    category = "物料"   → 子类在 material_inspection 中查找
--
--    成品 KL-DSO-2024-001
--      ├─ [半成品] KL-PCB-A01-2024-001（主控板组件）
--      │    ├─ [物料] MAT-IC-20240301-001（ARM处理器）
--      │    └─ [物料] MAT-IC-20240301-002（DDR4内存）
--      ├─ [半成品] KL-PWR-B01-2024-001（电源模块）
--      │    └─ [物料] MAT-IC-20240301-003（电源芯片）
--      └─ [物料]   MAT-DIS-20240301-004（OLED显示屏，直绑成品）
-- ============================================================

INSERT INTO qms.critical_material_binding
    (category, work_order_no, product_barcode, product_material_no, product_name,
     work_order_qty, material_barcode, material_code, material_name, spec_model,
     scanner, scan_time, process_code, process_name,
     is_active, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
VALUES
-- 成品 → 主控板组件（半成品）
('半成品', 'WO-2024-0301', 'KL-DSO-2024-001', 'FG-DSO-2024', '康立数字示波器 KL-DSO-2024',
 1.0000, 'KL-PCB-A01-2024-001', 'SFG-PCB-A01', '主控板组件 PCB-A01', 'PCB-A01-REV3',
 '操作员A', '2024-03-15 08:30:00', 'PROC-ASM-01', '装配',
 '是', 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 成品 → 电源模块（半成品）
('半成品', 'WO-2024-0301', 'KL-DSO-2024-001', 'FG-DSO-2024', '康立数字示波器 KL-DSO-2024',
 1.0000, 'KL-PWR-B01-2024-001', 'SFG-PWR-B01', '电源模块 PWR-B01', 'PWR-B01-REV2',
 '操作员A', '2024-03-15 08:35:00', 'PROC-ASM-01', '装配',
 '是', 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 成品 → OLED显示屏（物料，直绑）
('物料', 'WO-2024-0301', 'KL-DSO-2024-001', 'FG-DSO-2024', '康立数字示波器 KL-DSO-2024',
 1.0000, 'MAT-DIS-20240301-004', 'DIS-SSD1306', 'OLED显示屏 SSD1306', 'SSD1306-0.96-I2C',
 '操作员A', '2024-03-15 09:00:00', 'PROC-ASM-01', '装配',
 '是', 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 主控板组件 → ARM处理器（物料）
('物料', 'WO-2024-0280', 'KL-PCB-A01-2024-001', 'SFG-PCB-A01', '主控板组件 PCB-A01',
 1.0000, 'MAT-IC-20240301-001', 'IC-STM32H750', 'ARM处理器 STM32H750', 'STM32H750VBT6',
 '操作员B', '2024-03-10 10:00:00', 'PROC-SMT-01', '焊接',
 '是', 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 主控板组件 → DDR4内存（物料）
('物料', 'WO-2024-0280', 'KL-PCB-A01-2024-001', 'SFG-PCB-A01', '主控板组件 PCB-A01',
 2.0000, 'MAT-IC-20240301-002', 'IC-MT40A512', 'DDR4内存 MT40A512', 'MT40A512M16TB-062',
 '操作员B', '2024-03-10 10:05:00', 'PROC-SMT-01', '焊接',
 '是', 'SZ', '深圳分公司', 'system', 0, 1, now(), now()),

-- 电源模块 → 电源芯片（物料）
('物料', 'WO-2024-0281', 'KL-PWR-B01-2024-001', 'SFG-PWR-B01', '电源模块 PWR-B01',
 1.0000, 'MAT-IC-20240301-003', 'IC-LM2596', '电源芯片 LM2596', 'LM2596S-5.0',
 '操作员C', '2024-03-10 14:00:00', 'PROC-SMT-02', '焊接',
 '是', 'SZ', '深圳分公司', 'system', 0, 1, now(), now());
