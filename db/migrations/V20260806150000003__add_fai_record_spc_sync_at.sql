-- 为 fai_inspection_record 添加 spc_sync_at 字段，跟踪最近一次同步到 SPC 的时间
ALTER TABLE qms.fai_inspection_record ADD COLUMN IF NOT EXISTS spc_sync_at TIMESTAMP;

COMMENT ON COLUMN qms.fai_inspection_record.spc_sync_at IS '最近一次同步到SPC子组的时间，空值表示从未同步';
