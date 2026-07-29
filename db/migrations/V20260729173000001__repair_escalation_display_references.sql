-- 历史升级单曾使用分公司内供应商序号，且未落库展示快照；统一迁移为供应商主数据主键。
UPDATE qms.escalation e
SET supplier_id = CASE
    WHEN e.plant_code = 'SZ' THEN (e.supplier_id::bigint + 10)::text
    WHEN e.plant_code = 'MZ' THEN (e.supplier_id::bigint + 15)::text
END
WHERE e.supplier_id ~ '^[1-5]$'
  AND NOT EXISTS (
      SELECT 1 FROM qms.supplier s
      WHERE s.id::text = e.supplier_id
        AND s.plant_code = e.plant_code
        AND s.is_deleted = 0
  );

-- 为历史记录补齐供应商显示快照；后续记录由创建服务在写入时直接带入。
UPDATE qms.escalation e
SET supplier_code = s.supplier_code,
    supplier_name = s.supplier_name
FROM qms.supplier s
WHERE s.id::text = e.supplier_id
  AND s.plant_code = e.plant_code
  AND s.is_deleted = 0
  AND (NULLIF(BTRIM(e.supplier_code), '') IS NULL OR NULLIF(BTRIM(e.supplier_name), '') IS NULL);

-- 先从关联异常单回填物料，关联缺失的历史示例再从升级原因中提取 MC 编码。
UPDATE qms.escalation e
SET material_code = (
    SELECT eo.material_code
    FROM regexp_split_to_table(COALESCE(e.related_exception_ids, ''), '\\s*,\\s*') AS token(exception_id)
    JOIN qms.exception_order eo
      ON token.exception_id ~ '^\\d+$'
     AND eo.id = token.exception_id::bigint
     AND eo.is_deleted = 0
    WHERE NULLIF(BTRIM(eo.material_code), '') IS NOT NULL
    ORDER BY eo.created_at DESC
    LIMIT 1
)
WHERE NULLIF(BTRIM(e.material_code), '') IS NULL;

UPDATE qms.escalation
SET material_code = (regexp_match(escalation_reason, '(MC-[0-9]+)'))[1]
WHERE NULLIF(BTRIM(material_code), '') IS NULL
  AND escalation_reason ~ 'MC-[0-9]+';
