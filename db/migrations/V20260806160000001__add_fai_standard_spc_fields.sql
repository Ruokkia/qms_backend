-- FAI 标准模板增强：
--   1) fai_inspection_standard 加生效日期、变更备注
--   2) fai_inspection_standard_item 加目标值、子组大小n、控制图类型
--   目标值/USL/LSL/子组n/控制图类型属于物料-工序专属标准，不随 SPC 参数字典级联

ALTER TABLE qms.fai_inspection_standard
  ADD COLUMN IF NOT EXISTS effective_date DATE,
  ADD COLUMN IF NOT EXISTS change_remark TEXT;

ALTER TABLE qms.fai_inspection_standard_item
  ADD COLUMN IF NOT EXISTS target_value   DECIMAL(15,6),
  ADD COLUMN IF NOT EXISTS subgroup_size  INT,
  ADD COLUMN IF NOT EXISTS chart_type     VARCHAR(20);

COMMENT ON COLUMN qms.fai_inspection_standard_item.target_value  IS '目标值（物料-工序专属标准，非参数字典回填）';
COMMENT ON COLUMN qms.fai_inspection_standard_item.subgroup_size IS 'SPC 子组大小 n（物料-工序专属标准）';
COMMENT ON COLUMN qms.fai_inspection_standard_item.chart_type    IS 'SPC 控制图类型（物料-工序专属标准）';
COMMENT ON COLUMN qms.fai_inspection_standard.effective_date     IS '标准生效日期（ECN 变更/药监审计）';
COMMENT ON COLUMN qms.fai_inspection_standard.change_remark      IS '变更备注（ECN 变更/药监审计）';
