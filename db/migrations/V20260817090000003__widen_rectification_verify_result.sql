-- 加宽 supplier_audit_rectification.verify_result 列，允许存储完整验证结论文本。
-- 仅结构性变更（ALTER COLUMN），遵守 Migration Iron Law（无 DELETE/UPDATE/DROP）。
-- 依赖 V20260817090000001__create_supplier_audit.sql 已执行。

ALTER TABLE qms.supplier_audit_rectification
    ALTER COLUMN verify_result TYPE text;

COMMENT ON COLUMN qms.supplier_audit_rectification.verify_result IS '验证结果结论（含通过/不通过与说明）';
