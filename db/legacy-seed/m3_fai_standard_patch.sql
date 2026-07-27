-- ============================================================
-- m3_fai_standard_patch.sql
-- 补齐首件检验标准维护中「实际检验单已使用、但缺标准」的物料/工序组合
-- 缺失组合（来自 fai_inspection_record 实际数据）：
--   SZ: M004/装配, M005/焊接
--   MZ: M104/装配, M105/焊接, 123/装配, 123/焊接
-- 每套含 3 个参数项：AQL / 关键尺寸 / 性能参数
-- id 用 OVERRIDING SYSTEM VALUE 显式指定（避开现有 1-18，使用 101 起），
-- 避免与 GENERATED ALWAYS 序列冲突。
-- ============================================================

-- ===== 标准模板主表（6 条，id 101-106） =====
INSERT INTO qms.fai_inspection_standard (id, material_code, material_name, process_name, std_version, is_active, remark, plant_code, plant_name, created_by, updated_by)
OVERRIDING SYSTEM VALUE VALUES
(101, 'M004', '测试物料M004', '装配', 1, '是', '深圳装配首件标准(补)', 'SZ', '深圳', 'system', 'system'),
(102, 'M005', '测试物料M005', '焊接', 1, '是', '深圳焊接首件标准(补)', 'SZ', '深圳', 'system', 'system'),
(103, 'M104', '梅州物料M104', '装配', 1, '是', '梅州装配首件标准(补)', 'MZ', '梅州', 'system', 'system'),
(104, 'M105', '梅州物料M105', '焊接', 1, '是', '梅州焊接首件标准(补)', 'MZ', '梅州', 'system', 'system'),
(105, '123',  '测试物料123',  '装配', 1, '是', '梅州装配首件标准(补)', 'MZ', '梅州', 'system', 'system'),
(106, '123',  '测试物料123',  '焊接', 1, '是', '梅州焊接首件标准(补)', 'MZ', '梅州', 'system', 'system');

-- ===== 标准模板参数项（18 条，id 101-118） =====
INSERT INTO qms.fai_inspection_standard_item (id, standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit, is_required, sort_order, param_category, plant_code, plant_name, created_by, updated_by)
OVERRIDING SYSTEM VALUE VALUES
-- M004 装配 (101)
(101, 101, 'AQL 接收水准', 'AQL', 'AQL 1.0', 2,    NULL, '个',   '否', 1, 'AQL',       'SZ', '深圳', 'system', 'system'),
(102, 101, '装配间隙',     'DIM', '10.00',    10.05, 9.95, 'mm',  '是', 2, '关键尺寸', 'SZ', '深圳', 'system', 'system'),
(103, 101, '装配扭矩',     'PERF','≥50',      NULL,  50,   'N',   '否', 3, '性能参数', 'SZ', '深圳', 'system', 'system'),
-- M005 焊接 (102)
(104, 102, 'AQL 接收水准', 'AQL', 'AQL 1.0', 2,    NULL, '个',   '否', 1, 'AQL',       'SZ', '深圳', 'system', 'system'),
(105, 102, '焊接尺寸',     'DIM', '3.0',      3.2,  2.8,  'mm',  '是', 2, '关键尺寸', 'SZ', '深圳', 'system', 'system'),
(106, 102, '焊接强度',     'PERF','≥400',     NULL,  400,  'MPa', '否', 3, '性能参数', 'SZ', '深圳', 'system', 'system'),
-- M104 装配 (103)
(107, 103, 'AQL 接收水准', 'AQL', 'AQL 1.0', 2,    NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'system', 'system'),
(108, 103, '装配间隙',     'DIM', '10.00',    10.03, 9.97, 'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'system', 'system'),
(109, 103, '装配扭矩',     'PERF','≥50',      NULL,  50,   'N',   '否', 3, '性能参数', 'MZ', '梅州', 'system', 'system'),
-- M105 焊接 (104)
(110, 104, 'AQL 接收水准', 'AQL', 'AQL 1.0', 2,    NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'system', 'system'),
(111, 104, '焊接尺寸',     'DIM', '3.0',      3.3,  2.7,  'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'system', 'system'),
(112, 104, '焊接强度',     'PERF','≥400',     NULL,  400,  'MPa', '否', 3, '性能参数', 'MZ', '梅州', 'system', 'system'),
-- 123 装配 (105)
(113, 105, 'AQL 接收水准', 'AQL', 'AQL 1.0', 2,    NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'system', 'system'),
(114, 105, '装配间隙',     'DIM', '10.00',    10.05, 9.95, 'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'system', 'system'),
(115, 105, '装配扭矩',     'PERF','≥50',      NULL,  50,   'N',   '否', 3, '性能参数', 'MZ', '梅州', 'system', 'system'),
-- 123 焊接 (106)
(116, 106, 'AQL 接收水准', 'AQL', 'AQL 1.0', 2,    NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'system', 'system'),
(117, 106, '焊接尺寸',     'DIM', '3.0',      3.2,  2.8,  'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'system', 'system'),
(118, 106, '焊接强度',     'PERF','≥400',     NULL,  400,  'MPa', '否', 3, '性能参数', 'MZ', '梅州', 'system', 'system');

-- ===== 验证：缺失组合现已覆盖 =====
SELECT s.material_code, s.process_name, s.plant_code, s.is_active,
       (SELECT count(*) FROM qms.fai_inspection_standard_item i WHERE i.standard_id = s.id AND i.is_deleted = 0) AS item_cnt
FROM qms.fai_inspection_standard s
WHERE s.material_code IN ('M004','M005','M104','M105','123') AND s.is_deleted = 0
ORDER BY s.plant_code, s.material_code, s.process_name;
