-- ============================================================
-- m4_spc_seed.sql
-- M4 SPC 过程能力分析 基础种子数据
-- 工序（装配/焊接/检测）× 参数，覆盖 Xbar-R 与 Xbar-s 两种控制图
-- 深圳 SZ + 梅州 MZ 各一套（分公司数据隔离）
-- ============================================================

-- ===== SZ 深圳 工序 =====
INSERT INTO qms.spc_process (id, process_code, process_name, description, sort_order, plant_code, plant_name, created_by, updated_by) VALUES
(1, 'ASM', '装配', '关键装配工序', 1, 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(2, 'WDG', '焊接', '关键焊接工序', 2, 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(3, 'INS', '检测', '关键检测工序', 3, 'SZ', '深圳', 'sz_insp01', 'sz_insp01');

-- ===== MZ 梅州 工序 =====
INSERT INTO qms.spc_process (id, process_code, process_name, description, sort_order, plant_code, plant_name, created_by, updated_by) VALUES
(11, 'ASM', '装配', '关键装配工序', 1, 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(12, 'WDG', '焊接', '关键焊接工序', 2, 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(13, 'INS', '检测', '关键检测工序', 3, 'MZ', '梅州', 'mz_insp01', 'mz_insp01');

-- ===== SZ 参数 =====
INSERT INTO qms.spc_parameter (id, process_id, param_code, param_name, param_type, unit, upper_spec_limit, lower_spec_limit, target_value, subgroup_size, chart_type, is_active, plant_code, plant_name, created_by, updated_by) VALUES
(1, 1, 'AX-DIA', '轴径',   '尺寸', 'mm', 10.05, 9.95, 10.00, 5,  'Xbar-R', '是', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(2, 1, 'AX-PRES','装配压力', '压力', 'MPa', 5.5,  4.5,  5.00, 5,  'Xbar-R', '是', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(3, 2, 'WDG-TEMP','焊接温度','温度', '°C', 260,  240,  250,  5,  'Xbar-R', '是', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(4, 2, 'WDG-TRQ', '焊接扭矩','扭矩', 'N·m', 5.5,  4.5,  5.0,  4,  'Xbar-R', '是', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(5, 3, 'INS-DIM', '尺寸偏差','尺寸', 'mm', 0.05, -0.05, 0.00, 5,  'Xbar-R', '是', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
(6, 3, 'INS-FLAT','平面度', '尺寸', 'mm', 0.08, 0.00, 0.02, 12, 'Xbar-s', '是', 'SZ', '深圳', 'sz_insp01', 'sz_insp01');

-- ===== MZ 参数 =====
INSERT INTO qms.spc_parameter (id, process_id, param_code, param_name, param_type, unit, upper_spec_limit, lower_spec_limit, target_value, subgroup_size, chart_type, is_active, plant_code, plant_name, created_by, updated_by) VALUES
(11, 11, 'AX-DIA', '轴径',   '尺寸', 'mm', 10.05, 9.95, 10.00, 5,  'Xbar-R', '是', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(12, 11, 'AX-PRES','装配压力', '压力', 'MPa', 5.5,  4.5,  5.00, 5,  'Xbar-R', '是', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(13, 12, 'WDG-TEMP','焊接温度','温度', '°C', 260,  240,  250,  5,  'Xbar-R', '是', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(14, 12, 'WDG-TRQ', '焊接扭矩','扭矩', 'N·m', 5.5,  4.5,  5.0,  4,  'Xbar-R', '是', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(15, 13, 'INS-DIM', '尺寸偏差','尺寸', 'mm', 0.05, -0.05, 0.00, 5,  'Xbar-R', '是', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
(16, 13, 'INS-FLAT','平面度', '尺寸', 'mm', 0.08, 0.00, 0.02, 12, 'Xbar-s', '是', 'MZ', '梅州', 'mz_insp01', 'mz_insp01');

-- 序列对齐（避免后续自增主键冲突）
SELECT setval('qms.spc_process_id_seq',     (SELECT MAX(id) FROM qms.spc_process));
SELECT setval('qms.spc_parameter_id_seq',  (SELECT MAX(id) FROM qms.spc_parameter));
