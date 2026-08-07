-- 角色权限细粒度操作扩展：
-- 1. action_code 列扩长（NEXT_STEP_8D=12 字符，ESCALATION_REVIEW=18 字符，超过 varchar(16)）
-- 2. 去掉 CHECK 约束（原只允许 VIEW/EDIT/APPROVE/EXPORT）
-- 3. 种子数据：为异常模块新增 CLOSE/RESET/EDIT_8D/NEXT_STEP_8D/ESCALATION_REVIEW/ESCALATION_CLOSE 操作

-- 1. 扩列 + 去 CHECK
ALTER TABLE qms.sys_role_permission
    ALTER COLUMN action_code TYPE varchar(32);

ALTER TABLE qms.sys_role_permission
    DROP CONSTRAINT IF EXISTS sys_role_permission_action_code_check;

-- 2. R06 质量经理：闭环 + 重置 + 升级审核 + 升级关闭 + 8D
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R06','exception','CLOSE'),
('R06','exception','RESET'),
('R06','exception','ESCALATION_REVIEW'),
('R06','exception','ESCALATION_CLOSE'),
('R06','exception','EDIT_8D'),
('R06','exception','NEXT_STEP_8D')
ON CONFLICT DO NOTHING;

-- 3. R03 班组长：8D 保存 + 推进
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R03','exception','EDIT_8D'),
('R03','exception','NEXT_STEP_8D')
ON CONFLICT DO NOTHING;

-- 4. R04 质量工程师：8D 保存 + 推进
INSERT INTO qms.sys_role_permission(role_code, module_code, action_code) VALUES
('R04','exception','EDIT_8D'),
('R04','exception','NEXT_STEP_8D')
ON CONFLICT DO NOTHING;
