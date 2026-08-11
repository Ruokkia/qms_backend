-- 为检验标准表新增条码字段（追溯标识）
ALTER TABLE qms.fai_inspection_standard ADD COLUMN IF NOT EXISTS item_barcode VARCHAR(100);
-- 条码与追溯体系对齐，经常用于精确匹配，建索引
CREATE INDEX IF NOT EXISTS idx_fis_item_barcode ON qms.fai_inspection_standard(item_barcode) WHERE item_barcode IS NOT NULL AND is_deleted = 0;
