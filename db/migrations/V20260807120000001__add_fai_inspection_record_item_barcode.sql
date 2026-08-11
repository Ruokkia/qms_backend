-- 为检验记录表新增条码字段（从变更触发复制，支撑 FAI→SPC 去采集条码自动填充）
ALTER TABLE qms.fai_inspection_record ADD COLUMN IF NOT EXISTS item_barcode VARCHAR(100);
