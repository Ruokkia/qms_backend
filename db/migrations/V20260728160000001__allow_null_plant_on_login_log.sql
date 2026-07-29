-- 修复登录失败日志落库报错 500 的问题：
-- 当账号不存在时 user 为 null，login_log 无子公司信息，
-- 但 plant_code / plant_name 为 NOT NULL，插入 NULL 触发约束异常，
-- 导致 BusinessException(账号或密码错误) 未抛出，被兜底成 500。
-- 账号不存在本就无子公司信息，将这两列改为可空，与可空的 user_id 保持一致。

ALTER TABLE qms.sys_login_log ALTER COLUMN plant_code DROP NOT NULL;
ALTER TABLE qms.sys_login_log ALTER COLUMN plant_name DROP NOT NULL;
