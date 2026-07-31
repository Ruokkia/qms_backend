-- 清除 trace_node / trace_relation 脏数据并删除表。
-- 此前 /bind 接口因 critical_material_binding 表不存在一直报 500，trace_relation 大概率无有效数据。
-- 追溯架构已切换到三张业务表（critical_material_binding + material_inspection + finished_goods_inspection），
-- 这两张中间表及其关联索引不再需要。

-- 1. 清除所有脏数据
TRUNCATE TABLE qms.trace_relation CASCADE;
TRUNCATE TABLE qms.trace_node CASCADE;

-- 2. 删除表（CASCADE 防御性清理可能存在的依赖对象）
DROP TABLE IF EXISTS qms.trace_relation CASCADE;
DROP TABLE IF EXISTS qms.trace_node CASCADE;

-- 3. 清理关联的历史迁移索引（如有残留，DROP TABLE CASCADE 通常已处理）
DROP INDEX IF EXISTS qms.idx_trace_node_batch;
DROP INDEX IF EXISTS qms.idx_trace_relation_parent;
DROP INDEX IF EXISTS qms.idx_trace_relation_child;
