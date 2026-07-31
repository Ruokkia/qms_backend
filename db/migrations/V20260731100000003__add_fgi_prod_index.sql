-- 成品入库检验表新增按 (plant_code, prod_batch_or_sn) 的查询索引。
-- 用于追溯展开时按产品条码快速定位成品/半成品记录。
-- 非唯一索引：历史数据可能存在同一 prod_batch_or_sn 多条记录的情况。

CREATE INDEX idx_fgi_prod ON qms.finished_goods_inspection USING btree (plant_code, prod_batch_or_sn) WHERE (is_deleted = 0);
