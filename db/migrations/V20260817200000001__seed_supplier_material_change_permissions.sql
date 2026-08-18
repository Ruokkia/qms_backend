-- 供应商物料变更管理模块权限种子（仅 INSERT，遵守 Migration Iron Law）。
-- R00 超级管理员走 SUPER_ADMIN_ROLE 常量全通，无需在此插入。
-- 审批动作（approve/reject）在 Service 层按「指定审批人 / R06 管理员」校验，故后端仅需 VIEW/EDIT 两档 action。

-- R06 质量经理：发起变更 + 质量审批 + 全程管理
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R06','supplierMaterialChange','VIEW'),
('R06','supplierMaterialChange','EDIT')
ON CONFLICT DO NOTHING;

-- R05 SQE（供应商质量工程师）：发起/跟进供应商物料变更
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R05','supplierMaterialChange','VIEW'),
('R05','supplierMaterialChange','EDIT')
ON CONFLICT DO NOTHING;

-- R04 质量工程师：发起变更 + 维护物料检验标准
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R04','supplierMaterialChange','VIEW'),
('R04','supplierMaterialChange','EDIT')
ON CONFLICT DO NOTHING;

-- R03 班组长：仅查看
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R03','supplierMaterialChange','VIEW')
ON CONFLICT DO NOTHING;

-- R02 检验员：仅查看（执行加严检验）
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R02','supplierMaterialChange','VIEW')
ON CONFLICT DO NOTHING;

-- R01 操作工：仅查看
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R01','supplierMaterialChange','VIEW')
ON CONFLICT DO NOTHING;
