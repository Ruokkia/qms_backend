-- 为 fai_standard_approval 增加 version 列，支撑审批并发乐观锁（防重复审批/竞态）
-- 仅做结构修改 + 初始化种子值，不含任何删改生产数据的操作

ALTER TABLE qms.fai_standard_approval
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN qms.fai_standard_approval.version IS '乐观锁版本号：每次更新自增，用于并发审批防重入';

-- 历史表同样需要 version 以便后续统一乐观锁（如未来启用行级并发编辑）
-- 当前仅补充列，不影响既有数据
ALTER TABLE qms.fai_inspection_standard_history
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
