-- 异常单新增客诉 / 过程异常专属字段
-- 纯结构变更（ADD COLUMN，可空），符合迁移铁律：无 DELETE/UPDATE/TRUNCATE/DROP
ALTER TABLE qms.exception_order
  ADD COLUMN customer_name   VARCHAR(100),   -- 客诉：客户名称
  ADD COLUMN complaint_no    VARCHAR(100),   -- 客诉：客诉单号
  ADD COLUMN process_step    VARCHAR(100),   -- 过程异常：工序（下拉选固化工序库，冗余存储名称）
  ADD COLUMN production_line VARCHAR(100);   -- 过程异常：产线
