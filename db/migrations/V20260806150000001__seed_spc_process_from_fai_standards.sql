-- 将 fai_inspection_standard 中已有的工序补齐到 spc_process 工序主数据表
-- spc_process 作为全系统唯一工序字典，确保 FAI 与 SPC 工序编码/名称一致
INSERT INTO qms.spc_process (process_code, process_name, plant_code, plant_name, sort_order, created_at, updated_at)
SELECT DISTINCT ON (f.process_code, f.plant_code)
    f.process_code,
    f.process_name,
    f.plant_code,
    f.plant_name,
    0,
    NOW(),
    NOW()
FROM qms.fai_inspection_standard f
WHERE f.process_code IS NOT NULL
  AND f.process_code != ''
  AND f.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM qms.spc_process s
    WHERE s.process_code = f.process_code
      AND s.plant_code = f.plant_code
      AND s.is_deleted = 0
  )
ORDER BY f.process_code, f.plant_code;
