-- 异常单表增加 supplier_name 冗余列：即使 supplier_id 查找失败也能展示供应商名称
-- 对应缺陷：来料检验中 supplier_code 在 supplier 表中不存在时，异常单无法展示供应商

ALTER TABLE qms.exception_order
    ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(255);

COMMENT ON COLUMN qms.exception_order.supplier_name IS '供应商名称（冗余存储，避免 supplier_id 查找失败时无法展示）';
