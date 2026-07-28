-- 系统管理菜单是高风险入口：仅 R00（超级管理员）与 R06（质量经理）拥有完整权限。
-- 对既有数据库补齐权限；新建数据库在执行种子数据后同样会应用本迁移。

-- 先撤销所有旧的系统管理授权（包含误配角色及历史重复的逻辑删除记录），再写入唯一的标准授权。
UPDATE qms.sys_role_permission
SET is_deleted = 1,
    updated_at = NOW()
WHERE module_code = 'systemAdmin'
  AND is_deleted = 0;

INSERT INTO qms.sys_role_permission (role_code, module_code, action_code)
VALUES
    ('R00', 'systemAdmin', 'VIEW'),
    ('R00', 'systemAdmin', 'EDIT'),
    ('R00', 'systemAdmin', 'APPROVE'),
    ('R00', 'systemAdmin', 'EXPORT'),
    ('R06', 'systemAdmin', 'VIEW'),
    ('R06', 'systemAdmin', 'EDIT'),
    ('R06', 'systemAdmin', 'APPROVE'),
    ('R06', 'systemAdmin', 'EXPORT');
