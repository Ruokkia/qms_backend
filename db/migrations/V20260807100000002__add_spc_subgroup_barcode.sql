-- 为 SPC 子组表新增条码字段（追溯标识，手动录入时必填）
ALTER TABLE qms.spc_subgroup ADD COLUMN IF NOT EXISTS barcode VARCHAR(100);
