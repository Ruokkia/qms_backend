-- ============================================================
-- m1_production_defect_seed.sql
-- M1 生产维修记录 种子数据（需在 09_m1_production_defect_analytics.sql
-- 与 m1_incoming_seed.sql 之后执行）
-- 版本：V1.2
-- 日期：2026-07-21（多维分析功能已删除，仅保留生产维修记录样本导入）
-- 内容：导入用户提供的 7 行「生产维修记录表」Excel 样本（自由文本录入）
-- 说明：本文件不再设置 defect_type/item/data_source/data_completeness 等
--       已删除的统计字段；repair_done 依据 repair_status 由 09 脚本推导。
-- ============================================================

-- ===== 导入用户提供的 7 行「生产维修记录表」Excel 样本 =====
INSERT INTO qms.production_repair (
    repair_no, product_no, product_name, spec_model, work_order_no, product_batch_or_sn,
    process, defect_qty, defect_phenomenon, defect_code,
    send_repair_date, repair_date, repair_judgment_result, repair_status, repair_record,
    send_repairer, repairer, auditor, audit_status, audit_date, form_no,
    repair_done, plant_code, plant_name, created_by, updated_by
) VALUES
-- A. 待维修未提交（默认不进正式报表）
('WX26071600001','80.40.000029','血气生化分析仪','Vitagas 5E','WTMO012195-1',NULL,
 '老化测试',3,NULL,NULL,
 '2026-07-16',NULL,NULL,'待维修未提交',NULL,
 '丘爱玲',NULL,NULL,'待审核',NULL,'Y/KL.QR-834-02',
 0,'SZ','深圳','丘爱玲','丘爱玲'),
-- B. 已提交待审核
('WX26071400001','80.01.024001','电解质分析仪','K-Lite8H','MO035993-1',NULL,
 '外观功能调试',1,NULL,NULL,
 '2026-07-14','2026-07-16',NULL,'已提交待审核',NULL,
 '李富琼','罗鹏飞',NULL,'待审核',NULL,'Y/KL.QR-834-02',
 1,'SZ','深圳','李富琼','李富琼'),
-- C. 已审核
('WX26071300001','80.01.024004','电解质分析仪','K-Lite8G','MO034156-1',NULL,
 '外观功能调试',1,NULL,NULL,
 '2026-07-13','2026-07-14',NULL,'已审核',NULL,
 '丘爱玲','罗鹏飞','张文峰','已审核','2026-07-14','Y/KL.QR-834-02',
 1,'SZ','深圳','丘爱玲','丘爱玲'),
-- D. 已审核
('WX26071000001','80.01.029601','Electrolyte analyzer','KLite-C','MO035227-1',NULL,
 '外观功能调试',1,NULL,NULL,
 '2026-07-10','2026-07-14',NULL,'已审核',NULL,
 '李富琼','罗鹏飞','张文峰','已审核','2026-07-14','Y/KL.QR-834-02',
 1,'SZ','深圳','李富琼','李富琼'),
-- E. 已审核
('WX26070900002','80.01.028002','Electrolyte analyzer','AFT-800D','MO035627-1',NULL,
 '外观功能调试',1,NULL,NULL,
 '2026-07-09','2026-07-09',NULL,'已审核',NULL,
 '李富琼','罗鹏飞','张文峰','已审核','2026-07-09','Y/KL.QR-834-02',
 1,'SZ','深圳','李富琼','李富琼'),
-- F. 已审核
('WX26070900001','80.01.021002','电解质分析仪','K-Lite8D','MO035784-1',NULL,
 '外观功能调试',1,NULL,NULL,
 '2026-07-09','2026-07-09',NULL,'已审核',NULL,
 '丘爱玲','罗鹏飞','张文峰','已审核','2026-07-09','Y/KL.QR-834-02',
 1,'SZ','深圳','丘爱玲','丘爱玲'),
-- G. 已审核
('WX26070800001','80.01.025004','电解质分析仪','AFT-800G','MO032836-1',NULL,
 '外观功能调试',1,NULL,NULL,
 '2026-07-08','2026-07-09',NULL,'已审核',NULL,
 '丘爱玲','罗鹏飞','张文峰','已审核','2026-07-09','Y/KL.QR-834-02',
 1,'SZ','深圳','丘爱玲','丘爱玲');
