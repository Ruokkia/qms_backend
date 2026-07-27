-- ============================================================
-- fai_seed.sql
-- M3 首件检验（FAI）模块种子数据
-- 版本：V1.0
-- 日期：2026-07-19
-- 数据量：fai_inspection_record 首件检验记录 10 条
--         SZ 深圳 5 条 + MZ 梅州 5 条（按 plant_code 做分公司数据隔离）
-- 说明：
--   - id 由序列 qms.fai_inspection_record_id_seq 自动生成，不显式指定
--   - change_trigger_id 关联 qms.fai_change_trigger（SZ=1, MZ=2）
--   - 系统列 is_deleted(0)/version(1)/created_at/updated_at 使用表默认值
--   - process_name 仅限 装配/焊接/检测（项目红线）
--   - inspection_result 仅限 合格/不合格；signature_status 仅限 已签/未签
-- ============================================================

-- ===== M3: 首件检验记录 种子数据（SZ 深圳 5条 + MZ 梅州 5条） =====

INSERT INTO qms.fai_inspection_record (
    fai_no, change_trigger_id, material_code, material_name, batch_no,
    process_name, work_order_no, inspection_result, signature_status,
    plant_code, plant_name, created_by, updated_by
) VALUES
-- -------- SZ 深圳 5 条（关联变更触发 id=1） --------
('FAI-SZ-20260719-0003', 1, 'M001', '测试主轴', 'B-SZ-001', '装配', 'WO-SZ-001', '合格',   '已签', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
('FAI-SZ-20260719-0004', 1, 'M002', '轴承座',   'B-SZ-002', '焊接', 'WO-SZ-002', '合格',   '已签', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
('FAI-SZ-20260719-0005', 1, 'M003', '法兰盘',   'B-SZ-003', '检测', 'WO-SZ-003', '不合格', '未签', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
('FAI-SZ-20260719-0006', 1, 'M004', '齿轮箱',   'B-SZ-004', '装配', 'WO-SZ-004', '合格',   '已签', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
('FAI-SZ-20260719-0007', 1, 'M005', '传动轴',   'B-SZ-005', '焊接', 'WO-SZ-005', '合格',   '已签', 'SZ', '深圳', 'sz_insp01', 'sz_insp01'),
-- -------- MZ 梅州 5 条（关联变更触发 id=2） --------
('FAI-MZ-20260719-0001', 2, 'M101', '梅州主轴', 'B-MZ-001', '装配', 'WO-MZ-001', '合格',   '已签', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
('FAI-MZ-20260719-0002', 2, 'M102', '梅州轴承', 'B-MZ-002', '焊接', 'WO-MZ-002', '不合格', '未签', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
('FAI-MZ-20260719-0003', 2, 'M103', '梅州法兰', 'B-MZ-003', '检测', 'WO-MZ-003', '合格',   '已签', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
('FAI-MZ-20260719-0004', 2, 'M104', '梅州齿轮', 'B-MZ-004', '装配', 'WO-MZ-004', '合格',   '已签', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'),
('FAI-MZ-20260719-0005', 2, 'M105', '梅州转轴', 'B-MZ-005', '焊接', 'WO-MZ-005', '不合格', '未签', 'MZ', '梅州', 'mz_insp01', 'mz_insp01');
