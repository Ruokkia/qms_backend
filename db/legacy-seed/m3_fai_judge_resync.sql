-- M3 判定语义对齐三态：未检 -> 待判定，并重算主单判定结果
UPDATE qms.fai_inspection_item SET result = '待判定' WHERE result = '未检';

UPDATE qms.fai_inspection_record r
SET inspection_result = CASE
  WHEN EXISTS (SELECT 1 FROM qms.fai_inspection_item it
               WHERE it.fai_record_id = r.id AND it.is_deleted = 0 AND it.result = '不合格')
    THEN '不合格'
  WHEN EXISTS (SELECT 1 FROM qms.fai_inspection_item it
               WHERE it.fai_record_id = r.id AND it.is_deleted = 0 AND it.result = '待判定')
    THEN '待判定'
  ELSE '合格'
END;

SELECT '--- 主单判定分布 ---' AS info;
SELECT inspection_result, count(*) FROM qms.fai_inspection_record GROUP BY inspection_result;

SELECT '--- 明细状态分布 ---' AS info;
SELECT result, count(*) FROM qms.fai_inspection_item
WHERE is_deleted = 0 GROUP BY result;
