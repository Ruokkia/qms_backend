-- Make traceability master-data links explicit instead of relying on barcode matching.
ALTER TABLE qms.trace_node
    ADD COLUMN finished_goods_inspection_id bigint,
    ADD COLUMN material_inspection_id bigint;

-- Backfill only the retained, valid trace demo nodes.
UPDATE qms.trace_node node
SET finished_goods_inspection_id = inspection.id
FROM qms.finished_goods_inspection inspection
WHERE node.node_type = 'FINISHED_GOOD'
  AND node.barcode = inspection.prod_batch_or_sn
  AND inspection.is_deleted = 0;

UPDATE qms.trace_node node
SET material_inspection_id = inspection.id
FROM qms.material_inspection inspection
WHERE node.node_type = 'MATERIAL'
  AND node.material_code = inspection.material_code
  AND node.material_batch_no = inspection.material_batch_no
  AND inspection.is_deleted = 0;

ALTER TABLE qms.trace_node
    ADD CONSTRAINT fk_trace_node_finished_goods_inspection
        FOREIGN KEY (finished_goods_inspection_id)
        REFERENCES qms.finished_goods_inspection(id),
    ADD CONSTRAINT fk_trace_node_material_inspection
        FOREIGN KEY (material_inspection_id)
        REFERENCES qms.material_inspection(id),
    ADD CONSTRAINT chk_trace_node_master_reference
        CHECK (
            (node_type = 'FINISHED_GOOD' AND material_inspection_id IS NULL)
            OR (node_type = 'MATERIAL' AND finished_goods_inspection_id IS NULL)
            OR (node_type = 'SEMI_FINISHED'
                AND finished_goods_inspection_id IS NULL
                AND material_inspection_id IS NULL)
        );

CREATE UNIQUE INDEX uq_trace_node_finished_goods_inspection
    ON qms.trace_node (finished_goods_inspection_id)
    WHERE finished_goods_inspection_id IS NOT NULL;

CREATE UNIQUE INDEX uq_trace_node_material_inspection
    ON qms.trace_node (material_inspection_id)
    WHERE material_inspection_id IS NOT NULL;
