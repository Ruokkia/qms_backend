-- M2 供应商升级：供应商ID 改为字符串类型，支持非数字供应商编码/编号（如 abc、S-001、-）
ALTER TABLE qms.escalation
    ALTER COLUMN supplier_id TYPE character varying(64)
    USING supplier_id::text;

COMMENT ON COLUMN qms.escalation.supplier_id IS '供应商ID（字符串，允许字母/编号等非数字标识，如 abc、S-001）';
