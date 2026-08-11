-- 异常单整改责任人字段修复
-- 原因：V20260805110000001 虽已标记为 applied 但列实际未创建，
-- 导致 MyBatis-Plus 自动 SELECT 时因 owner_id 不存在而报 500。
ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS owner_id bigint;

ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS owner_name character varying(64);
