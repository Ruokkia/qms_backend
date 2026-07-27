-- 深圳追溯演示数据验收查询：执行种子脚本后各项均应满足 expected_min。
SELECT 'incoming_records' AS check_name, COUNT(*) AS actual, 12 AS expected_min
FROM qms.material_inspection
WHERE record_no LIKE 'IQC-SZD-202607%'
UNION ALL
SELECT 'finished_goods_records', COUNT(*), 6
FROM qms.finished_goods_inspection
WHERE report_no LIKE 'FGR-SZD-202607%'
UNION ALL
SELECT 'trace_nodes', COUNT(*), 19
FROM qms.trace_node
WHERE barcode LIKE 'SZD-%'
UNION ALL
SELECT 'trace_relations', COUNT(*), 20
FROM qms.trace_relation relation
JOIN qms.trace_node parent ON parent.id = relation.parent_node_id
JOIN qms.trace_node child ON child.id = relation.child_node_id
WHERE parent.barcode LIKE 'SZD-%' AND child.barcode LIKE 'SZD-%';

WITH RECURSIVE affected(node_id) AS (
  SELECT id FROM qms.trace_node WHERE barcode = 'SZD-MAT-RES-LOT-20260722'
  UNION
  SELECT relation.parent_node_id
  FROM qms.trace_relation relation
  JOIN affected item ON relation.child_node_id = item.node_id
)
SELECT 'shared_batch_finished_goods' AS check_name, COUNT(*) AS actual, 2 AS expected_min
FROM affected item
JOIN qms.trace_node node ON node.id = item.node_id
WHERE node.node_type = 'FINISHED_GOOD';

SELECT supplier_code, COUNT(*) AS batch_count,
       ROUND(100.0 * COUNT(*) FILTER (WHERE inspection_result = '合格') / COUNT(*), 2) AS pass_rate
FROM qms.material_inspection
WHERE record_no LIKE 'IQC-SZD-202607%'
GROUP BY supplier_code
ORDER BY batch_count DESC, supplier_code;
