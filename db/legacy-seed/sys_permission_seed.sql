-- Run after seed/sys_auth_seed.sql and ddl/08_sys_permission.sql.
INSERT INTO qms.sys_role (role_code, role_name, description, status, plant_code, plant_name, data_scope, created_by)
SELECT 'R00', '超级管理员', '系统账号与权限管理', 1, '*', '全局', 'ALL_PLANTS', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM qms.sys_role WHERE role_code = 'R00' AND is_deleted = 0);

UPDATE qms.sys_role SET data_scope = CASE WHEN role_code IN ('R00', 'R06') THEN 'ALL_PLANTS' ELSE 'OWN_PLANT' END;

-- Development bootstrap administrator. Change this password immediately in deployed environments.
INSERT INTO qms.sys_user (account, password_hash, real_name, role_code, plant_code, plant_name, status, auth_version, created_by)
SELECT 'qms_admin', '$2b$10$XmwOkbcMKBN.BTnIl7FMKe.m7Q0dLH5/MevC7mIClbW8P7Q1A1qTO', '系统管理员', 'R00', 'SZ', '深圳', 1, 1, 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM qms.sys_user WHERE account = 'qms_admin' AND is_deleted = 0);

INSERT INTO qms.sys_module(module_code, module_name) VALUES
('systemAdmin', '系统管理'), ('trace', '来料追溯'), ('incoming', '来料管理'),
('exception', '异常管理'), ('fai', '首件检验'), ('spc', 'SPC分析'),
('productionDefect', '生产不良'), ('finishedGoods', '成品管理'), ('supplier', '供应商管理'),
('material', '物料变更'), ('notification', '通知')
ON CONFLICT DO NOTHING;

INSERT INTO qms.sys_role_permission(role_code, module_code, action_code)
SELECT r.role_code, m.module_code, a.action_code
FROM (VALUES ('R00'), ('R06')) AS r(role_code)
CROSS JOIN qms.sys_module m
CROSS JOIN (VALUES ('VIEW'), ('EDIT'), ('APPROVE'), ('EXPORT')) AS a(action_code)
WHERE NOT EXISTS (
    SELECT 1 FROM qms.sys_role_permission p
    WHERE p.role_code = r.role_code AND p.module_code = m.module_code AND p.action_code = a.action_code AND p.is_deleted = 0
);

-- Existing role/module matrix is intentionally seeded as view access. R00 and R06 already receive all actions above.
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code)
SELECT r.role_code, m.module_code, 'VIEW'
FROM qms.sys_role r JOIN qms.sys_module m ON m.module_code <> 'systemAdmin'
WHERE r.role_code BETWEEN 'R01' AND 'R05'
  AND NOT EXISTS (SELECT 1 FROM qms.sys_role_permission p WHERE p.role_code = r.role_code AND p.module_code = m.module_code AND p.action_code = 'VIEW' AND p.is_deleted = 0);
