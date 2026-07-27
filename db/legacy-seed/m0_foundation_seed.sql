-- ============================================================
-- m0_foundation_seed.sql
-- M0 全链路追溯底座测试数据
-- 版本：V1.0
-- 日期：2026-07-17
-- 数据量：SZ + MZ 双分公司，audit_log 10条/item_node 10条
-- 注：实际 audit_log 由 AOP 自动写入，此处为示例/联调数据
-- ============================================================

-- ===== M0: 审计日志种子数据 =====

INSERT INTO qms.audit_log (table_name, record_id, operation_type, before_data, after_data, operator_id, operator_name, plant_code, ip_address, operation_time, reason) VALUES
-- SZ 深圳
('material_inspection', 1,  'CREATE', NULL, '{"record_no":"R-SZ-2026-001","inspection_result":"合格"}', 1, '张三', 'SZ', '192.168.1.101', '2026-07-10 08:30:00', 'IQC检验完成'),
('material_inspection', 2,  'UPDATE', '{"review_status":"待审核"}', '{"review_status":"已审核","reviewer":"李四","review_date":"2026-07-10"}', 2, '李四', 'SZ', '192.168.1.102', '2026-07-10 10:15:00', '品管审核通过'),
('finished_goods_inspection', 1, 'CREATE', NULL, '{"report_no":"RP-SZ-001","inspection_result":"合格"}', 2, '李四', 'SZ', '192.168.1.102', '2026-07-12 14:00:00', '成品检验完成'),
('critical_material_binding', 1, 'CREATE', NULL, '{"work_order_no":"WO-SZ-001","product_barcode":"SN2026-SZ-0001"}', 1, '张三', 'SZ', '192.168.1.101', '2026-07-11 09:30:00', '关键物料绑定'),
('exception_order', 1, 'CREATE', NULL, '{"exception_no":"EX-20260713-001","severity":"严重","status":"待整改"}', 4, '赵六', 'SZ', '192.168.1.104', '2026-07-13 11:00:00', '来料不良触发异常'),
-- MZ 梅州
('material_inspection', 3,  'CREATE', NULL, '{"record_no":"R-MZ-2026-001","inspection_result":"不合格"}', 7, '陈一', 'MZ', '192.168.2.101', '2026-07-10 09:00:00', 'IQC检验完成'),
('material_inspection', 3,  'UPDATE', '{"review_status":"待审核"}', '{"review_status":"已审核"}', 8, '周二', 'MZ', '192.168.2.102', '2026-07-10 11:00:00', '品管审核'),
('production_repair', 1,     'CREATE', NULL, '{"repair_no":"PR-MZ-001","defect_phenomenon":"虚焊导致通讯中断"}', 7, '陈一', 'MZ', '192.168.2.101', '2026-07-14 09:00:00', '维修记录创建'),
('escalation', 1,            'CREATE', NULL, '{"escalation_reason":"90天内虚焊≥3次","escalation_action":"加密审核"}', 11, '冯五', 'MZ', '192.168.2.105', '2026-07-15 15:00:00', '供应商升级触发'),
('exception_order', 2,       'UPDATE', '{"status":"整改中"}', '{"status":"已闭环","closed_at":"2026-07-16"}', 11, '冯五', 'MZ', '192.168.2.105', '2026-07-16 16:30:00', '异常闭环');

-- ===== M0: 追溯引擎节点树种子数据（SZ 深圳 完整追溯链） =====
-- 追溯链：成品SN → 部件(主板) → 关键物料(电容) → 来料批次 → 供应商

INSERT INTO qms.item_node (node_type, node_code, parent_id, batch_id, qty_used, work_order_id, plant_code, plant_name, created_by, updated_by) VALUES
-- 层级 1：成品序列号（根节点）
('SN',       'SN2026-SZ-0001', NULL,  NULL, NULL,   1, 'SZ', '深圳', '张三', '张三'),
-- 层级 2：部件（主板）
('部件',     'M001-SZ-MB-01',  1,     NULL, 1,      1, 'SZ', '深圳', '张三', '张三'),
-- 层级 2：部件（外壳）
('部件',     'M002-SZ-HS-01',  1,     NULL, 1,      1, 'SZ', '深圳', '张三', '张三'),
-- 层级 3：关键物料（电容，来自来料批次 B-SZ-2026-001）
('关键物料', 'MC-001-C1-001',  2,     1,    5,      1, 'SZ', '深圳', '张三', '张三'),
-- 层级 3：关键物料（芯片，来自来料批次 B-SZ-2026-002）
('关键物料', 'MC-003-IC1-001', 2,     2,    1,      1, 'SZ', '深圳', '张三', '张三'),
-- 层级 4：来料批次（电容）
('来料批次', 'B-SZ-2026-001',  4,     1,    NULL,   NULL, 'SZ', '深圳', '张三', '张三'),
-- 层级 4：来料批次（芯片）
('来料批次', 'B-SZ-2026-002',  5,     2,    NULL,   NULL, 'SZ', '深圳', '张三', '张三'),
-- MZ 梅州：另一条独立追溯链
('SN',       'SN2026-MZ-0001', NULL,  NULL, NULL,   2, 'MZ', '梅州', '陈一', '陈一'),
('部件',     'M001-MZ-MB-01',  8,     NULL, 1,      2, 'MZ', '梅州', '陈一', '陈一'),
('关键物料', 'MC-002-R1-001',  9,     3,    10,     2, 'MZ', '梅州', '陈一', '陈一');
