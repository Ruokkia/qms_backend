-- M3 回填：为「有 0 条激活明细 + 存在匹配激活标准」的已有首件检验单补建参数项
-- 复制逻辑与后端 FaiInspectionServiceImpl.create 保持一致（param_name/code/category/
-- standard_value/upper_limit/lower_limit/unit/sort_order；actual_value 空、result=未检）
-- 仅插入当前缺失明细的检验单，已存在的明细不会被重复插入。

INSERT INTO qms.fai_inspection_item
  (fai_record_id, param_name, param_code, param_category,
   standard_value, upper_limit, lower_limit, unit, result, sort_order,
   plant_code, plant_name, created_by, updated_by)
SELECT
  r.id,
  si.param_name,
  si.param_code,
  si.param_category,
  si.standard_value,
  si.upper_limit,
  si.lower_limit,
  si.unit,
  '未检',
  si.sort_order,
  r.plant_code,
  r.plant_name,
  COALESCE(r.created_by, 'system'),
  COALESCE(r.updated_by, 'system')
FROM qms.fai_inspection_record r
JOIN qms.fai_inspection_standard s
  ON s.material_code = r.material_code
 AND s.process_name  = r.process_name
 AND s.plant_code    = r.plant_code
 AND s.is_active     = '是'
 AND s.is_deleted    = 0
JOIN qms.fai_inspection_standard_item si
  ON si.standard_id = s.id
 AND si.is_deleted  = 0
WHERE NOT EXISTS (
  SELECT 1 FROM qms.fai_inspection_item it
  WHERE it.fai_record_id = r.id AND it.is_deleted = 0
);

SELECT '--- 回填后各检验单明细数 ---' AS info;
SELECT r.id, r.fai_no, r.material_code, r.process_name, r.plant_code,
       (SELECT count(*) FROM qms.fai_inspection_item it
         WHERE it.fai_record_id = r.id AND it.is_deleted = 0) AS item_cnt
FROM qms.fai_inspection_record r
ORDER BY r.id DESC LIMIT 20;
