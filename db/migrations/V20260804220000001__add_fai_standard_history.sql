-- ============================================================================
-- P0: FAI 检验标准变更历史追溯
-- 记录标准的 创建/编辑/删除 操作，存储前后快照便于差异对比
-- ============================================================================

CREATE TABLE IF NOT EXISTS qms.fai_inspection_standard_history (
    id               BIGSERIAL PRIMARY KEY,
    standard_id      BIGINT       NOT NULL,                -- 关联 fai_inspection_standard.id
    change_type      VARCHAR(16)  NOT NULL,                -- CREATE / UPDATE / DELETE
    change_reason    TEXT,                                  -- 操作人填写的变更原因
    before_snapshot  JSONB,                                 -- 变更前快照（标准 + 参数项列表）
    after_snapshot   JSONB,                                 -- 变更后快照（标准 + 参数项列表）
    diff_summary     TEXT,                                  -- 自动生成的差异摘要文本
    changed_by       VARCHAR(64)  NOT NULL,                -- 操作人账号
    changed_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    plant_code       VARCHAR(32)  NOT NULL,
    plant_name       VARCHAR(64)
);

-- 按标准查询历史
CREATE INDEX IF NOT EXISTS idx_fish_standard_id
    ON qms.fai_inspection_standard_history (standard_id);

-- 按时间排序
CREATE INDEX IF NOT EXISTS idx_fish_changed_at
    ON qms.fai_inspection_standard_history (changed_at DESC);

COMMENT ON TABLE  qms.fai_inspection_standard_history IS 'FAI 检验标准变更历史表';
COMMENT ON COLUMN qms.fai_inspection_standard_history.before_snapshot IS '变更前快照（JSON: {standard:{...}, items:[...]}），CREATE 时为 null';
COMMENT ON COLUMN qms.fai_inspection_standard_history.after_snapshot  IS '变更后快照（JSON: {standard:{...}, items:[...]}），DELETE 时为 null';
COMMENT ON COLUMN qms.fai_inspection_standard_history.diff_summary   IS '自动生成的差异摘要，如"修改了物料编码从 A→B，新增参数项 X…"';
