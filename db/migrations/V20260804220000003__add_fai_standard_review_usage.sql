-- ============================================================================
-- P3: 定期复审提醒 + 执行情况统计
-- ============================================================================

-- 标准复审字段
ALTER TABLE qms.fai_inspection_standard
    ADD COLUMN IF NOT EXISTS last_reviewed_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS review_interval_days INT DEFAULT 90;

-- 执行统计字段
ALTER TABLE qms.fai_inspection_standard
    ADD COLUMN IF NOT EXISTS usage_count    INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_used_at   TIMESTAMPTZ;

COMMENT ON COLUMN qms.fai_inspection_standard.last_reviewed_at     IS '最近一次复审时间';
COMMENT ON COLUMN qms.fai_inspection_standard.review_interval_days IS '复审间隔天数，默认 90 天';
COMMENT ON COLUMN qms.fai_inspection_standard.usage_count          IS '被首件检验记录引用的次数（累计）';
COMMENT ON COLUMN qms.fai_inspection_standard.last_used_at         IS '最近一次被引用时间';
