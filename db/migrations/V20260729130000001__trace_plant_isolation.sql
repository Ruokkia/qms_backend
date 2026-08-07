-- L6 / D8：追溯图谱按分公司隔离
-- trace_node / trace_relation 增加 plant_code 列，并在服务层原生 SQL 中按当前厂过滤。
-- 历史数据按关联来料的 plant_code 回填，无法推断时兜底到 'SZ'，避免过滤后全部不可见。

ALTER TABLE qms.trace_node ADD COLUMN plant_code character varying(8);
ALTER TABLE qms.trace_relation ADD COLUMN plant_code character varying(8);

-- 1) MATERIAL 节点：通过 material_code + material_batch_no 关联 material_inspection 回填
UPDATE qms.trace_node n
SET plant_code = m.plant_code
FROM qms.material_inspection m
WHERE n.node_type = 'MATERIAL'
  AND n.material_code = m.material_code
  AND n.material_batch_no = m.material_batch_no
  AND n.plant_code IS NULL;

-- 2) 非 MATERIAL 节点：从下游子节点推导
UPDATE qms.trace_node n
SET plant_code = child.plant_code
FROM qms.trace_relation r
JOIN qms.trace_node child ON child.id = r.child_node_id
WHERE n.plant_code IS NULL
  AND r.parent_node_id = n.id
  AND child.plant_code IS NOT NULL;

-- 3) 非 MATERIAL 节点：从上游父节点推导
UPDATE qms.trace_node n
SET plant_code = parent.plant_code
FROM qms.trace_relation r
JOIN qms.trace_node parent ON parent.id = r.parent_node_id
WHERE n.plant_code IS NULL
  AND r.child_node_id = n.id
  AND parent.plant_code IS NOT NULL;

-- 4) 关系：取父节点所在厂
UPDATE qms.trace_relation r
SET plant_code = p.plant_code
FROM qms.trace_node p
WHERE r.parent_node_id = p.id
  AND r.plant_code IS NULL;

-- 5) 兜底：极其孤立的数据落入 'SZ'，保证可见性
UPDATE qms.trace_node SET plant_code = 'SZ' WHERE plant_code IS NULL;
UPDATE qms.trace_relation SET plant_code = 'SZ' WHERE plant_code IS NULL;

-- 索引，避免按厂过滤时全表扫描
CREATE INDEX IF NOT EXISTS idx_trace_node_plant ON qms.trace_node (plant_code);
CREATE INDEX IF NOT EXISTS idx_trace_relation_plant ON qms.trace_relation (plant_code);
