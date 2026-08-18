-- 工装管理写权限：质量经理及 SQE 负责工装台账、保养和维修闭环；超级管理员由代码直接放行。
INSERT INTO qms.sys_role_permission (role_code, module_code, action_code, created_at, updated_at, is_deleted)
SELECT r.role_code, 'tooling', 'EDIT', NOW(), NOW(), 0
FROM qms.sys_role r
WHERE r.role_code IN ('R05', 'R06')
  AND r.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM qms.sys_role_permission p
      WHERE p.role_code = r.role_code
        AND p.module_code = 'tooling'
        AND p.action_code = 'EDIT'
        AND p.is_deleted = 0
  );
