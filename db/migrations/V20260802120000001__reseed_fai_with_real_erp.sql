-- ============================================================================
-- 重建 FAI 演示数据：丢弃旧 FG-/SFG-/IC-/DIS- 友好码，改用真实 ERP 编码风格
-- 作用范围：finished_goods_inspection / material_inspection /
--           fai_change_trigger / critical_material_binding 四张演示表
-- 设计要点：
--   1) 旧演示行采用逻辑删除（is_deleted=1, version+1）而非物理 TRUNCATE，
--      保留审计且兼容 @TableLogic；
--   2) 成品 5 条（3 SZ + 2 DG），物料 5 条（3 SZ + 2 DG）；
--   3) FAI 变更触发：item_code 严格等于成品/物料 material_code（真实 ERP 号）；
--   4) 关键物料绑定：成品↔物料严格同厂区，保证追溯链路 plant_code 自洽。
-- 幂等：逻辑删除 WHERE is_deleted=0；INSERT 直接追加（演示库重建）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 逻辑删除四表既有演示数据（保留审计）
-- ---------------------------------------------------------------------------
UPDATE qms.finished_goods_inspection
SET is_deleted = 1, version = version + 1, updated_at = CURRENT_TIMESTAMP,
    updated_by = 'reseed'
WHERE is_deleted = 0;

UPDATE qms.material_inspection
SET is_deleted = 1, version = version + 1, updated_at = CURRENT_TIMESTAMP,
    updated_by = 'reseed'
WHERE is_deleted = 0;

UPDATE qms.fai_change_trigger
SET is_deleted = 1, version = version + 1, updated_at = CURRENT_TIMESTAMP,
    updated_by = 'reseed'
WHERE is_deleted = 0;

UPDATE qms.critical_material_binding
SET is_deleted = 1, version = version + 1, updated_at = CURRENT_TIMESTAMP,
    updated_by = 'reseed'
WHERE is_deleted = 0;

-- ---------------------------------------------------------------------------
-- 1. 成品 / 半成品（finished_goods_inspection）
--    prod_batch_or_sn 为 SN 级追溯标识；material_code 为真实 ERP 产品代码
-- ---------------------------------------------------------------------------
INSERT INTO qms.finished_goods_inspection
    (report_no, inspection_request_no, production_order_no,
     material_code, product_name, model_spec, prod_batch_or_sn,
     production_date, submitted_qty, inspected_qty, qualified_qty, unqualified_qty, unit,
     inspector_name, category, inspection_result,
     qc_review, mgr_approval, is_valid, is_urgent, is_entrusted,
     plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
VALUES
-- 成品 SZ：数字示波器
('RPT-2026-0001', 'SJR-20260315-001', 'WO-2026-0301',
 '10.09.001.001', '康立数字示波器', 'KL-DSO-2026', 'KL-DSO-2026-001',
 '2026-03-15', 10.000, 10.000, 9.000, 1.000, '台',
 '张工', '成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- 半成品 SZ：主控板组件
('RPT-2026-0002', 'SJR-20260310-001', 'WO-2026-0280',
 '10.09.001.002', '主控板组件', 'PCB-A01-REV3', 'KL-PCB-A01-2026-001',
 '2026-03-10', 20.000, 20.000, 19.000, 1.000, '块',
 '李工', '半成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- 半成品 SZ：电源模块
('RPT-2026-0003', 'SJR-20260310-002', 'WO-2026-0281',
 '10.09.001.003', '电源模块', 'PWR-B01-REV2', 'KL-PWR-B01-2026-001',
 '2026-03-10', 20.000, 20.000, 19.000, 1.000, '块',
 '李工', '半成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- 成品 DG：工业控制器
('RPT-2026-1001', 'SJR-20260320-001', 'WO-2026-0401',
 '10.09.002.001', '工业控制器', 'KLI-CTRL-2026', 'KL-CTRL-2026-001',
 '2026-03-20', 15.000, 15.000, 15.000, 0.000, '台',
 '王工', '成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

-- 半成品 DG：通信模块
('RPT-2026-1002', 'SJR-20260320-002', 'WO-2026-0402',
 '10.09.002.002', '通信模块', 'COMM-MOD-REV1', 'KL-COMM-2026-001',
 '2026-03-20', 15.000, 15.000, 15.000, 0.000, '块',
 '王工', '半成品', '合格',
 '已审核', '已审核', '是', '否', '否',
 'DG', '东莞分公司', 'reseed', 0, 1, now(), now());

-- ---------------------------------------------------------------------------
-- 2. 物料（material_inspection）
--    material_code 为真实 ERP 物料代码；material_barcode 唯一（非空）
-- ---------------------------------------------------------------------------
INSERT INTO qms.material_inspection
    (record_no, material_code, material_name, spec_model, material_batch_no,
     material_barcode, supplier_code, supplier_name,
     inspection_result, review_status, inspection_category,
     submitted_qty, qualified_qty, unit,
     plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
VALUES
-- 物料 SZ
('REC-2026-0001', '99.11.100501', 'ARM处理器', 'STM32H750VBT6', 'MB-IC-20260301-001',
 'BAR-IC-100501', 'S2012073', '深圳电子元件有限公司',
 '合格', '已审核', '电子料', 100.000, 99.000, 'pcs',
 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('REC-2026-0002', '99.11.100502', 'DDR4内存', 'MT40A512M16TB-062', 'MB-IC-20260301-002',
 'BAR-IC-100502', 'S2012073', '深圳电子元件有限公司',
 '合格', '已审核', '电子料', 100.000, 98.000, 'pcs',
 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('REC-2026-0003', '99.11.100503', '电源芯片', 'LM2596S', 'MB-IC-20260301-003',
 'BAR-IC-100503', 'S2012074', '东莞电源科技',
 '合格', '已审核', '电源料', 200.000, 198.000, 'pcs',
 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- 物料 DG
('REC-2026-1001', '99.11.200501', 'OLED显示屏', 'SSD1306-0.96-I2C', 'MB-DIS-20260301-001',
 'BAR-DIS-200501', 'S2012080', '东莞显示器件',
 '合格', '已审核', '显示料', 50.000, 50.000, 'pcs',
 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

('REC-2026-1002', '99.11.200502', '继电器', 'JQC-3FF-05V', 'MB-RLY-20260301-001',
 'BAR-RLY-200502', 'S2012081', '东莞电气',
 '合格', '已审核', '电气料', 80.000, 79.000, 'pcs',
 'DG', '东莞分公司', 'reseed', 0, 1, now(), now());

-- ---------------------------------------------------------------------------
-- 3. FAI 变更触发（fai_change_trigger）
--    item_type=PRODUCT -> item_code = 成品 material_code
--    item_type=MATERIAL -> item_code = 物料 material_code
--    material_code / material_name 与 item 列同步；batch_no 引用对应 SN/批号
-- ---------------------------------------------------------------------------
INSERT INTO qms.fai_change_trigger
    (trigger_type, item_type, item_code, item_name, item_barcode,
     material_code, material_name, batch_no, process_name, process_code,
     trigger_reason, status, plant_code, plant_name,
     created_by, is_deleted, version, created_at, updated_at)
VALUES
-- ---- PRODUCT（5 条，对应成品表）----
('换批次', 'PRODUCT', '10.09.001.001', '康立数字示波器', 'KL-DSO-2026-001',
 '10.09.001.001', '康立数字示波器', 'KL-DSO-2026-001', '装配', 'PROC-ASM-01',
 '批量投产切换批次', '待检验', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('换模具', 'PRODUCT', '10.09.001.002', '主控板组件', 'KL-PCB-A01-2026-001',
 '10.09.001.002', '主控板组件', 'KL-PCB-A01-2026-001', '焊接', 'PROC-SMT-01',
 '新开模具首件确认', '待检验', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('升级系统', 'PRODUCT', '10.09.001.003', '电源模块', 'KL-PWR-B01-2026-001',
 '10.09.001.003', '电源模块', 'KL-PWR-B01-2026-001', '装配', 'PROC-ASM-01',
 '产线系统升级后首件', '待检验', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('换设备', 'PRODUCT', '10.09.002.001', '工业控制器', 'KL-CTRL-2026-001',
 '10.09.002.001', '工业控制器', 'KL-CTRL-2026-001', '装配', 'PROC-ASM-02',
 '更换生产设备首件', '待检验', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

('材料批次', 'PRODUCT', '10.09.002.002', '通信模块', 'KL-COMM-2026-001',
 '10.09.002.002', '通信模块', 'KL-COMM-2026-001', '焊接', 'PROC-SMT-02',
 '关键材料批次变更', '待检验', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

-- ---- MATERIAL（5 条，对应物料表）----
('换批次', 'MATERIAL', '99.11.100501', 'ARM处理器', 'BAR-IC-100501',
 '99.11.100501', 'ARM处理器', 'MB-IC-20260301-001', '焊接', 'PROC-SMT-01',
 '来料批次切换', '待检验', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('换批次', 'MATERIAL', '99.11.100502', 'DDR4内存', 'BAR-IC-100502',
 '99.11.100502', 'DDR4内存', 'MB-IC-20260301-002', '焊接', 'PROC-SMT-01',
 '来料批次切换', '待检验', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('换批次', 'MATERIAL', '99.11.100503', '电源芯片', 'BAR-IC-100503',
 '99.11.100503', '电源芯片', 'MB-IC-20260301-003', '装配', 'PROC-ASM-01',
 '来料批次切换', '待检验', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

('换批次', 'MATERIAL', '99.11.200501', 'OLED显示屏', 'BAR-DIS-200501',
 '99.11.200501', 'OLED显示屏', 'MB-DIS-20260301-001', '装配', 'PROC-ASM-02',
 '来料批次切换', '待检验', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

('换批次', 'MATERIAL', '99.11.200502', '继电器', 'BAR-RLY-200502',
 '99.11.200502', '继电器', 'MB-RLY-20260301-001', '装配', 'PROC-ASM-02',
 '来料批次切换', '待检验', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now());

-- ---------------------------------------------------------------------------
-- 4. 关键物料绑定（critical_material_binding）
--    严格同厂区：SZ 成品↔SZ 物料，DG 成品↔DG 物料
--    product_barcode = 成品 prod_batch_or_sn
--    material_barcode = 物料 material_barcode
-- ---------------------------------------------------------------------------
INSERT INTO qms.critical_material_binding
    (category, work_order_no, product_barcode, product_material_no, product_name,
     work_order_qty, material_barcode, material_code, material_name, spec_model,
     scanner, scan_time, process_code, process_name,
     is_active, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
VALUES
-- SZ 区：成品数字示波器 -> 主控板组件（半成品）
('半成品', 'WO-2026-0301', 'KL-DSO-2026-001', '10.09.001.001', '康立数字示波器',
 1.0000, 'KL-PCB-A01-2026-001', '10.09.001.002', '主控板组件', 'PCB-A01-REV3',
 '操作员A', '2026-03-15 08:30:00', 'PROC-ASM-01', '装配',
 '是', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- SZ 区：成品数字示波器 -> 电源模块（半成品）
('半成品', 'WO-2026-0301', 'KL-DSO-2026-001', '10.09.001.001', '康立数字示波器',
 1.0000, 'KL-PWR-B01-2026-001', '10.09.001.003', '电源模块', 'PWR-B01-REV2',
 '操作员A', '2026-03-15 08:35:00', 'PROC-ASM-01', '装配',
 '是', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- SZ 区：主控板组件 -> ARM处理器（物料）
('物料', 'WO-2026-0280', 'KL-PCB-A01-2026-001', '10.09.001.002', '主控板组件',
 1.0000, 'BAR-IC-100501', '99.11.100501', 'ARM处理器', 'STM32H750VBT6',
 '操作员B', '2026-03-10 10:00:00', 'PROC-SMT-01', '焊接',
 '是', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- SZ 区：主控板组件 -> DDR4内存（物料）
('物料', 'WO-2026-0280', 'KL-PCB-A01-2026-001', '10.09.001.002', '主控板组件',
 2.0000, 'BAR-IC-100502', '99.11.100502', 'DDR4内存', 'MT40A512M16TB-062',
 '操作员B', '2026-03-10 10:05:00', 'PROC-SMT-01', '焊接',
 '是', 'SZ', '深圳分公司', 'reseed', 0, 1, now(), now()),

-- DG 区：工业控制器 -> 通信模块（半成品）
('半成品', 'WO-2026-0401', 'KL-CTRL-2026-001', '10.09.002.001', '工业控制器',
 1.0000, 'KL-COMM-2026-001', '10.09.002.002', '通信模块', 'COMM-MOD-REV1',
 '操作员C', '2026-03-20 09:00:00', 'PROC-ASM-02', '装配',
 '是', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

-- DG 区：通信模块 -> OLED显示屏（物料，直绑）
('物料', 'WO-2026-0402', 'KL-COMM-2026-001', '10.09.002.002', '通信模块',
 1.0000, 'BAR-DIS-200501', '99.11.200501', 'OLED显示屏', 'SSD1306-0.96-I2C',
 '操作员C', '2026-03-20 09:30:00', 'PROC-ASM-02', '装配',
 '是', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now()),

-- DG 区：工业控制器 -> 继电器（物料，直绑）
('物料', 'WO-2026-0401', 'KL-CTRL-2026-001', '10.09.002.001', '工业控制器',
 1.0000, 'BAR-RLY-200502', '99.11.200502', '继电器', 'JQC-3FF-05V',
 '操作员C', '2026-03-20 10:00:00', 'PROC-ASM-02', '装配',
 '是', 'DG', '东莞分公司', 'reseed', 0, 1, now(), now());
