ALTER TABLE qms.escalation
    ADD COLUMN IF NOT EXISTS plan_filled_by varchar(64);

COMMENT ON COLUMN qms.escalation.plan_filled_by IS '升级措施填写人，由后端根据当前登录用户自动写入';
