-- 供应商现场审核模块权限种子（仅 INSERT，遵守 Migration Iron Law）。
-- R00 超级管理员走 SUPER_ADMIN_MODULES 常量全通，无需在此插入。
-- action_code 列已扩为 varchar(32)，可容纳 VIEW/EDIT/APPROVE/EXPORT。

-- R06 质量经理：供应商审核全部权限（查看/编辑/审核/导出）
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R06','supplierAudit','VIEW'),
('R06','supplierAudit','EDIT'),
('R06','supplierAudit','APPROVE'),
('R06','supplierAudit','EXPORT')
ON CONFLICT DO NOTHING;

-- R04 质量工程师：制定/填写审核与整改（编辑 + 查看）
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R04','supplierAudit','VIEW'),
('R04','supplierAudit','EDIT')
ON CONFLICT DO NOTHING;

-- R03 班组长：参与整改填写（编辑 + 查看）
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R03','supplierAudit','VIEW'),
('R03','supplierAudit','EDIT')
ON CONFLICT DO NOTHING;

-- R05 SQE：供应商质量工程师，审核相关（查看 + 审核 + 导出）
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R05','supplierAudit','VIEW'),
('R05','supplierAudit','APPROVE'),
('R05','supplierAudit','EXPORT')
ON CONFLICT DO NOTHING;

-- R01 操作工 / R02 检验员：仅查看
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R01','supplierAudit','VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R02','supplierAudit','VIEW')
ON CONFLICT DO NOTHING;
