-- 修复上一批历史回填中正则转义造成的遗漏：从关联异常单补齐仍为空的物料编码。
UPDATE qms.escalation e
SET material_code = (
    SELECT eo.material_code
    FROM unnest(string_to_array(COALESCE(e.related_exception_ids, ''), ',')) AS token(exception_id)
    JOIN qms.exception_order eo
      ON BTRIM(token.exception_id) ~ '^[0-9]+$'
     AND eo.id = BTRIM(token.exception_id)::bigint
     AND eo.is_deleted = 0
    WHERE NULLIF(BTRIM(eo.material_code), '') IS NOT NULL
    ORDER BY eo.created_at DESC
    LIMIT 1
)
WHERE NULLIF(BTRIM(e.material_code), '') IS NULL;
