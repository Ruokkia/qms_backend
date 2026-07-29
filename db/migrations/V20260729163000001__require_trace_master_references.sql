-- Finished-goods and material nodes must originate from their inspection master data.
ALTER TABLE qms.trace_node
    DROP CONSTRAINT chk_trace_node_master_reference;

ALTER TABLE qms.trace_node
    ADD CONSTRAINT chk_trace_node_master_reference
        CHECK (
            (node_type = 'FINISHED_GOOD'
                AND finished_goods_inspection_id IS NOT NULL
                AND material_inspection_id IS NULL)
            OR (node_type = 'MATERIAL'
                AND material_inspection_id IS NOT NULL
                AND finished_goods_inspection_id IS NULL)
            OR (node_type = 'SEMI_FINISHED'
                AND finished_goods_inspection_id IS NULL
                AND material_inspection_id IS NULL)
        );
