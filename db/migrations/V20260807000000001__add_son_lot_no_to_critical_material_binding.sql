-- 追溯改造：为关键物料绑定清单表新增 son_lot_no（子项批号）字段。
-- 该字段仅用于详情展示（子节点用批号查询成品表/物料表），不影响追溯链路
-- （链路仍通过 product_barcode / material_barcode 在绑定表中关联）。

ALTER TABLE qms.critical_material_binding ADD COLUMN son_lot_no character varying(200);

COMMENT ON COLUMN qms.critical_material_binding.son_lot_no IS '子项批号（详情展示用，不影响追溯链路）';
