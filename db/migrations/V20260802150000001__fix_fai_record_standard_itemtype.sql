-- ============================================================================
-- 修正 FAI 检验执行 / 标准维护 数据：补齐 item_type / item_code / item_name 列
-- 原因：V20260802130000001 插入时遗漏实体已存在的 item_type/item_code/item_name 三列，
--       导致后端 list 按 item_type 过滤时匹配不到 NULL，前端检验执行/标准维护 tab 显示 No Data。
-- 处理：对 V20260802130000001 已生成的 record / standard 直接 UPDATE 补列（不重插，避开序列/唯一约束冲突）。
-- ============================================================================

-- 1. 检验记录：通过 change_trigger_id 关联 fai_change_trigger 取 item_type/item_code/item_name
UPDATE qms.fai_inspection_record r
SET item_type = ct.item_type,
    item_code = ct.item_code,
    item_name = ct.item_name,
    version   = r.version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'fix'
FROM qms.fai_change_trigger ct
WHERE r.change_trigger_id = ct.id
  AND ct.is_deleted = 0
  AND r.is_deleted = 0
  AND r.item_type IS NULL;

-- 2. 标准模板：物料类，item_type='MATERIAL'，item_code/item_name 同步 material_code/material_name
UPDATE qms.fai_inspection_standard s
SET item_type  = 'MATERIAL',
    item_code  = s.material_code,
    item_name  = s.material_name,
    version    = s.version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'fix'
WHERE s.is_deleted = 0
  AND s.item_type IS NULL;
