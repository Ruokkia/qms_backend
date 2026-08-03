-- ============================================================
-- V20260801100000006 回填 SPC 子组存量 NULL 的 item_code
-- 背景：item_type/item_code 列在加列迁移(V20260801100000002)中新增，
--       该迁移仅回填 material_code 非空行；而 legacy-seed 的演示子组
--       (m4_spc_subgroups_seed.sql) 在加列前导入且 material_code 为空，
--       导致存量 spc_subgroup.item_code 全为 NULL。这造成控制图按代码
--       关联/模糊搜索失效，前端误显示默认 NULL 数据的"图表不符"假象。
-- 处理：对 item_code IS NULL 的存量行，按 plant_code + param_code 生成
--       演示 PRODUCT 代码，使模糊搜索可命中、图表归属明确。
-- 隔离：仅更新 is_deleted=0 的行，plant_code 取自子组自身，避免跨厂区。
-- ============================================================

UPDATE qms.spc_subgroup sub
SET item_type = 'PRODUCT',
    item_code = 'DEMO-' || sub.plant_code || '-' || COALESCE(p.param_code, 'UNK')
FROM qms.spc_parameter p
WHERE sub.item_code IS NULL
  AND sub.is_deleted = 0
  AND sub.param_id = p.id
  AND p.is_deleted = 0;

-- 兜底：param_id 无法关联参数表的孤儿行，按 plant_code 给出占位 PRODUCT 代码
UPDATE qms.spc_subgroup sub
SET item_type = 'PRODUCT',
    item_code = 'DEMO-' || sub.plant_code || '-ORPHAN'
WHERE sub.item_code IS NULL
  AND sub.is_deleted = 0;

SELECT 'spc_subgroup item_code backfill done: '
       || count(*) AS result
FROM qms.spc_subgroup
WHERE item_code IS NOT NULL AND is_deleted = 0;
