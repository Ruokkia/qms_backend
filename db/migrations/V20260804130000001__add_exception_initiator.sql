-- 异常单新增「发起人」字段，用于记录点击「发起整改流程」的责任人及时间。
-- 历史数据允许为空（发起前无发起人），不回填。
ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS initiated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS initiated_at TIMESTAMP;

COMMENT ON COLUMN qms.exception_order.initiated_by IS '发起整改流程的责任人姓名（点击「发起整改」的人）';
COMMENT ON COLUMN qms.exception_order.initiated_at IS '发起整改流程的时间';
