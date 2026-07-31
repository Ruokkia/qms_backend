-- 物料检验入库表新增 material_barcode 列（SN 级追溯标识）。
-- 存量记录 material_barcode 为 NULL，不参与 SN 级追溯。
-- 新导入记录必须携带 material_barcode，全局唯一（跨厂区不冲突）。

ALTER TABLE qms.material_inspection ADD COLUMN material_barcode character varying(200);

-- 全局唯一索引（部分索引，仅约束非空非删除记录）
CREATE UNIQUE INDEX uq_mi_material_barcode ON qms.material_inspection USING btree (material_barcode) WHERE (material_barcode IS NOT NULL AND is_deleted = 0);
