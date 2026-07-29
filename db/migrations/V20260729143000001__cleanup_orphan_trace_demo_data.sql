-- Remove legacy, smoke-test and completeness-test trace graph data that has no
-- corresponding material inspection or finished-goods inspection master record.
-- The SZD trace dataset (nodes 22-40) remains as the consistent demo dataset.

WITH obsolete_nodes AS (
    SELECT id
    FROM qms.trace_node
    WHERE barcode IN (
        'FG-A100', 'SF-A110', 'SF-A120',
        'MAT-A01-LOT-20260721', 'MAT-A02-LOT-20260721', 'MAT-A03-LOT-20260718',
        'FG-B200', 'SF-B210', 'MAT-B01-LOT-20260721', 'MAT-B02-LOT-20260719',
        'SF-C310', 'FG-C300', 'MAT-TEST-LOT-20260722',
        'MAT-SMOKE-1784654618871', 'MAT-SMOKE-1784656116699',
        'FG-COMPLETE-1784682386370', 'SF-COMPLETE-A-1784682386370',
        'SF-COMPLETE-B-1784682386370', 'MAT-COMPLETE-1784682386370',
        '1', '1.1'
    )
)
DELETE FROM qms.trace_relation
WHERE parent_node_id IN (SELECT id FROM obsolete_nodes)
   OR child_node_id IN (SELECT id FROM obsolete_nodes);

DELETE FROM qms.trace_node
WHERE barcode IN (
    'FG-A100', 'SF-A110', 'SF-A120',
    'MAT-A01-LOT-20260721', 'MAT-A02-LOT-20260721', 'MAT-A03-LOT-20260718',
    'FG-B200', 'SF-B210', 'MAT-B01-LOT-20260721', 'MAT-B02-LOT-20260719',
    'SF-C310', 'FG-C300', 'MAT-TEST-LOT-20260722',
    'MAT-SMOKE-1784654618871', 'MAT-SMOKE-1784656116699',
    'FG-COMPLETE-1784682386370', 'SF-COMPLETE-A-1784682386370',
    'SF-COMPLETE-B-1784682386370', 'MAT-COMPLETE-1784682386370',
    '1', '1.1'
);
