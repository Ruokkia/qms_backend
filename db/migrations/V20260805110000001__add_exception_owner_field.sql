-- 异常单整改责任人字段（M2）
-- 自动触发（来料/首件/成品不合格）时责任人留空（无发起人）；
-- 由相关部门在「发起整改」时从已有人员中选择填写后进入 D0 指派流程。
-- 注：区别于 initiated_by（发起操作人，即点击「发起整改」的人）。

ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS owner_id bigint;
COMMENT ON COLUMN qms.exception_order.owner_id IS '整改责任人 ID（自动触发时留空，发起整改时由相关部门选择已有人员填写）';

ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS owner_name character varying(64);
COMMENT ON COLUMN qms.exception_order.owner_name IS '整改责任人姓名（冗余存储，便于列表/详情直接展示，与 initiated_by 区分）';
