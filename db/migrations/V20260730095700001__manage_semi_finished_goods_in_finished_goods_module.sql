-- 半成品纳入成品数据管理：与成品共用检验记录和双签审核，追溯节点仅作为主数据投影。

UPDATE qms.finished_goods_inspection
SET category = '成品'
WHERE is_deleted = 0
  AND (category IS NULL OR btrim(category) = '');

ALTER TABLE qms.trace_node
    DROP CONSTRAINT IF EXISTS chk_trace_node_master_reference;

-- 将历史追溯半成品补为成品检验主数据，保留待审核状态，交由现有双签流程完成放行。
INSERT INTO qms.finished_goods_inspection (
    report_no, product_name, material_code, model_spec, prod_batch_or_sn,
    category, qc_review, mgr_approval, is_urgent, is_valid, is_entrusted,
    plant_code, plant_name, created_by, updated_by, is_deleted, version, created_at, updated_at
)
SELECT
    'TRACE-SF-' || n.barcode,
    n.name,
    n.product_code,
    n.specification,
    n.barcode,
    '半成品', '待审核', '待审核', '否', '是', '否',
    n.plant_code,
    CASE n.plant_code WHEN 'SZ' THEN '深圳' WHEN 'MZ' THEN '梅州' ELSE n.plant_code END,
    'SYSTEM', 'SYSTEM', 0, 1, now(), now()
FROM (
    SELECT DISTINCT ON (plant_code, barcode) *
    FROM qms.trace_node
    WHERE node_type = 'SEMI_FINISHED'
    ORDER BY plant_code, barcode, id
) n
WHERE NOT EXISTS (
      SELECT 1
      FROM qms.finished_goods_inspection f
      WHERE f.is_deleted = 0
        AND f.plant_code = n.plant_code
        AND f.prod_batch_or_sn = n.barcode
  );

UPDATE qms.trace_node n
SET finished_goods_inspection_id = f.id,
    material_inspection_id = NULL
FROM qms.finished_goods_inspection f
WHERE n.node_type = 'SEMI_FINISHED'
  AND n.plant_code = f.plant_code
  AND n.barcode = f.prod_batch_or_sn
  AND f.category = '半成品'
  AND f.is_deleted = 0;

ALTER TABLE qms.trace_node
    ADD CONSTRAINT chk_trace_node_master_reference
    CHECK (
        (node_type IN ('FINISHED_GOOD', 'SEMI_FINISHED')
            AND finished_goods_inspection_id IS NOT NULL
            AND material_inspection_id IS NULL)
        OR (node_type = 'MATERIAL'
            AND material_inspection_id IS NOT NULL
            AND finished_goods_inspection_id IS NULL)
    );

-- 补齐历史成品/半成品主数据缺失的追溯节点，保证管理列表与追溯图谱一一对应。
INSERT INTO qms.trace_node (
    node_type, barcode, name, product_code, specification,
    finished_goods_inspection_id, material_inspection_id, plant_code
)
SELECT
    CASE WHEN f.category = '半成品' THEN 'SEMI_FINISHED' ELSE 'FINISHED_GOOD' END,
    f.prod_batch_or_sn,
    COALESCE(NULLIF(f.product_name, ''), f.prod_batch_or_sn),
    f.material_code,
    f.model_spec,
    f.id,
    NULL,
    f.plant_code
FROM qms.finished_goods_inspection f
WHERE f.is_deleted = 0
  AND NULLIF(btrim(f.prod_batch_or_sn), '') IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM qms.trace_node n
      WHERE n.finished_goods_inspection_id = f.id
  );
