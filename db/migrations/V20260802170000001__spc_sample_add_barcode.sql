-- ============================================================================
-- SPC 样本增加条码列（SN 级追溯标识）
-- 控制图悬停需在「样本点」级展示条码，支撑 SN 级质量追溯。
-- spc_subgroup 已含 batch_no（批次级），此处补齐样本级 barcode。
-- 版本号 20260802170000001 晚于已存在的 fix_fai_record_standard_itemtype(150000001)
-- 与 fix_spc_coefficient_and_recalc(160000001)，避免 Flyway 版本冲突。
-- ============================================================================

-- 1. 加列（幂等）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'qms' AND table_name = 'spc_sample' AND column_name = 'barcode'
  ) THEN
    ALTER TABLE qms.spc_sample ADD COLUMN barcode VARCHAR(128);
  END IF;
END $$;

-- 2. 为存量样本回填演示条码：SN-{plant}-{subgroup_no}-{sample_no}
--    仅对未绑定条码的存量行生效，不覆盖已有值。
UPDATE qms.spc_sample sm
SET barcode = 'SN-' || sg.plant_code || '-' || sg.subgroup_no || '-' || lpad(sm.sample_no::text, 2, '0')
FROM qms.spc_subgroup sg
WHERE sm.subgroup_id = sg.id
  AND sm.is_deleted = 0
  AND sm.barcode IS NULL;
