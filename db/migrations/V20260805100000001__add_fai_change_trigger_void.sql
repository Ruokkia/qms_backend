-- FAI 变更触发：支持作废流程
-- 新增 void_reason 列记录作废原因
-- 仅加列，不改动/删除已有数据

ALTER TABLE qms.fai_change_trigger
    ADD COLUMN IF NOT EXISTS void_reason character varying(512);

COMMENT ON COLUMN qms.fai_change_trigger.void_reason IS '作废原因（仅在 status=已作废 时填写）';
