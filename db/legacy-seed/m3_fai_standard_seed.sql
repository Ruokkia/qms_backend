-- ============================================================
-- m3_fai_standard_seed.sql
-- M3 首件检验标准模板 种子数据
-- 版本：V1.0
-- 说明：为「物料+工序」手动维护检验标准（AQL / 关键尺寸 / 性能参数）
--       - 覆盖 SZ 深圳(M001/M002/M003) 与 MZ 梅州(M101/M102/M103)
--       - 每个标准含 3 个参数项：AQL / 关键尺寸 / 性能参数
--       - id 由序列自动生成，这里用 OVERRIDING SYSTEM VALUE 显式指定以保证主从关联
--       - plant_code 隔离；标准与主记录为最新激活版本(is_active=是)
-- ============================================================

-- ===== 标准模板主表（6 条） =====
INSERT INTO qms.fai_inspection_standard (id, material_code, material_name, process_name, std_version, is_active, remark, plant_code, plant_name, created_by, updated_by)
OVERRIDING SYSTEM VALUE VALUES
(1, 'M001', '测试主轴',   '装配', 1, '是', '深圳装配首件标准', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
(2, 'M002', '轴承座',     '焊接', 1, '是', '深圳焊接首件标准', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
(3, 'M003', '法兰盘',     '检测', 1, '是', '深圳检测首件标准', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
(4, 'M101', '梅州主轴',   '装配', 1, '是', '梅州装配首件标准', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
(5, 'M102', '梅州轴承',   '焊接', 1, '是', '梅州焊接首件标准', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
(6, 'M103', '梅州法兰',   '检测', 1, '是', '梅州检测首件标准', 'MZ', '梅州', 'mz_admin', 'mz_admin');

-- ===== 标准模板参数项（18 条） =====
INSERT INTO qms.fai_inspection_standard_item (id, standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit, is_required, sort_order, param_category, plant_code, plant_name, created_by, updated_by)
OVERRIDING SYSTEM VALUE VALUES
-- SZ M001 装配
(1, 1, 'AQL 接收水准',        'AQL',   'AQL 1.0',  2,     NULL, '个',   '否', 1, 'AQL',       'SZ', '深圳', 'sz_admin', 'sz_admin'),
(2, 1, '主轴外径',            'OD',    '50',        50.02, 49.98, 'mm',  '是', 2, '关键尺寸', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
(3, 1, '额定转速',            'RPM',   '3000',      NULL,  3000,   'r/min','是', 3, '性能参数', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
-- SZ M002 焊接
(4, 2, 'AQL 接收水准',        'AQL',   'AQL 1.0',  2,     NULL, '个',   '否', 1, 'AQL',       'SZ', '深圳', 'sz_admin', 'sz_admin'),
(5, 2, '焊接熔深',            'WD',    '3.0',       3.2,   2.8,   'mm',  '是', 2, '关键尺寸', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
(6, 2, '焊缝抗拉强度',        'TS',    '400',       NULL,  400,   'MPa', '是', 3, '性能参数', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
-- SZ M003 检测
(7, 3, 'AQL 接收水准',        'AQL',   'AQL 1.0',  1,     NULL, '个',   '否', 1, 'AQL',       'SZ', '深圳', 'sz_admin', 'sz_admin'),
(8, 3, '法兰厚度',            'THK',   '20',        20.1,  19.9,  'mm',  '是', 2, '关键尺寸', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
(9, 3, '平面度',              'FLAT',  '0.05',      0.05,  NULL,  'mm',  '是', 3, '性能参数', 'SZ', '深圳', 'sz_admin', 'sz_admin'),
-- MZ M101 装配
(10, 4, 'AQL 接收水准',       'AQL',   'AQL 1.0',  2,     NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'mz_admin', 'mz_admin'),
(11, 4, '主轴外径',           'OD',    '50',        50.03, 49.97, 'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
(12, 4, '额定转速',           'RPM',   '3000',      NULL,  3000,   'r/min','是', 3, '性能参数', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
-- MZ M102 焊接
(13, 5, 'AQL 接收水准',       'AQL',   'AQL 1.0',  2,     NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'mz_admin', 'mz_admin'),
(14, 5, '焊接熔深',           'WD',    '3.0',       3.3,   2.7,   'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
(15, 5, '同轴度',             'CONC',  '0.02',      0.02,  NULL,  'mm',  '是', 3, '性能参数', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
-- MZ M103 检测
(16, 6, 'AQL 接收水准',       'AQL',   'AQL 1.0',  1,     NULL, '个',   '否', 1, 'AQL',       'MZ', '梅州', 'mz_admin', 'mz_admin'),
(17, 6, '法兰厚度',           'THK',   '20',        20.15, 19.85, 'mm',  '是', 2, '关键尺寸', 'MZ', '梅州', 'mz_admin', 'mz_admin'),
(18, 6, '圆度',               'ROUND', '0.03',      0.03,  NULL,  'mm',  '是', 3, '性能参数', 'MZ', '梅州', 'mz_admin', 'mz_admin');
