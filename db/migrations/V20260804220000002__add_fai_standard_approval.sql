-- ============================================================================
-- P1: FAI 检验标准轻量级审批工作流
-- 重大变更（新建/编辑关键字段/删除）需审批后执行，保留审批记录
-- ============================================================================

CREATE TABLE IF NOT EXISTS qms.fai_standard_approval (
    id               BIGSERIAL PRIMARY KEY,
    standard_id      BIGINT,                                 -- 关联标准 ID（编辑/删除时存在，新建时为 null）
    approval_type    VARCHAR(16)  NOT NULL,                  -- CREATE / UPDATE / DELETE
    request_data     JSONB        NOT NULL,                  -- 待审批的请求数据快照（完整的 FaiStandardSaveRequest）
    requester        VARCHAR(64)  NOT NULL,                  -- 提交人
    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approver         VARCHAR(64),                            -- 审批人（审批后回填）
    approved_at      TIMESTAMPTZ,                            -- 审批时间
    approval_status  VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / APPROVED / REJECTED
    reject_reason    TEXT,                                    -- 驳回原因
    applied          BOOLEAN      NOT NULL DEFAULT FALSE,    -- 审批通过后是否已执行变更
    remark           TEXT,                                    -- 提交备注
    plant_code       VARCHAR(32)  NOT NULL,
    plant_name       VARCHAR(64)
);

-- 按状态查询待审批列表
CREATE INDEX IF NOT EXISTS idx_fsa_status ON qms.fai_standard_approval (approval_status, plant_code);

-- 按提交时间排序
CREATE INDEX IF NOT EXISTS idx_fsa_requested ON qms.fai_standard_approval (requested_at DESC);

COMMENT ON TABLE  qms.fai_standard_approval IS 'FAI 检验标准审批表';
COMMENT ON COLUMN qms.fai_standard_approval.standard_id    IS '关联标准 ID，新建审批时为 null';
COMMENT ON COLUMN qms.fai_standard_approval.request_data   IS '待审批的请求数据 JSON 快照（含标准主表 + 参数项列表）';
COMMENT ON COLUMN qms.fai_standard_approval.applied        IS '审批通过后是否已执行实际变更（防重复执行）';
