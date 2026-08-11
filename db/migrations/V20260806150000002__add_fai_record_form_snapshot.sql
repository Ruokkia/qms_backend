-- 为 fai_inspection_record 添加 form_snapshot 列，存储建单时的检验标准快照（JSON）
-- 用于审计追溯：即使标准后续变更，也能回溯建单时的配置全貌
ALTER TABLE qms.fai_inspection_record ADD COLUMN IF NOT EXISTS form_snapshot TEXT;

COMMENT ON COLUMN qms.fai_inspection_record.form_snapshot IS '建单时检验标准快照（JSON），含标准版本与参数项明细';
