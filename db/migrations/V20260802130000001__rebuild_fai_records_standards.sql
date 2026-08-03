-- ============================================================================
-- 重建 FAI 检验执行 / 标准维护 / 签名数据
-- 基于已重建的 fai_change_trigger（真实 ERP 号）展开：
--   - 每条 change_trigger 建 1 条 fai_inspection_record（检验执行 tab）
--   - 部分 record 置“合格/已签”，填充历史报告 tab
--   - 5 个物料各建 1 条激活标准模板 + 3 个参数项（标准维护 tab）
--   - 每条 record 复制其物料标准参数项为检验明细
--   - 已签 record 建 1 条电子签名
-- 旧 FAI 数据（M001~M105 / FG- / SFG- / DIS- 友好码）逻辑删除。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 逻辑删除旧 FAI 行
-- ---------------------------------------------------------------------------
UPDATE qms.fai_inspection_record       SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.fai_inspection_item         SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.fai_inspection_standard     SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.fai_inspection_standard_item SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.fai_signature               SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;

-- ---------------------------------------------------------------------------
-- 1. 重建 fai_inspection_standard（5 物料，各 1 标准 + 3 参数项）
--    standard 与 change_trigger 同厂区；standard_item 参数与 spc 无关，纯 FAI 检验项
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
    v_std_id BIGINT;
BEGIN
    FOR r IN
        SELECT item_code, item_name, plant_code, plant_name
        FROM qms.fai_change_trigger
        WHERE is_deleted=0 AND item_type='MATERIAL'
        ORDER BY id
    LOOP
        INSERT INTO qms.fai_inspection_standard
            (material_code, material_name, process_name, std_version, is_active, plant_code, plant_name,
             created_by, is_deleted, version, created_at, updated_at)
        VALUES
            (r.item_code, r.item_name, '装配', 1, '是', r.plant_code, r.plant_name,
             'rebuild', 0, 1, now(), now())
        RETURNING id INTO v_std_id;

        INSERT INTO qms.fai_inspection_standard_item
            (standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit,
             is_required, sort_order, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at,
             param_category, spc_enabled)
        VALUES
            (v_std_id, '外观检查', 'AQL', '无划伤/无毛刺', NULL, NULL, '—', '是', 1,
             r.plant_code, r.plant_name, 'rebuild', 0, 1, now(), now(), '外观', '否'),
            (v_std_id, '关键尺寸', 'DIM', '50.00', 50.20, 49.80, 'mm', '是', 2,
             r.plant_code, r.plant_name, 'rebuild', 0, 1, now(), now(), '关键尺寸', '否'),
            (v_std_id, '性能参数', 'PERF', '合格', NULL, NULL, '—', '是', 3,
             r.plant_code, r.plant_name, 'rebuild', 0, 1, now(), now(), '性能', '否');
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 2. 重建 fai_inspection_record（10 条 trigger 各 1 条）+ 明细 + 签名
--    前 6 条（含 5 产品 + 第1物料）置“合格/已签”填充历史报告；后 4 物料“待判定/未签”
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
    v_rec_id BIGINT;
    v_cnt INT := 0;
    v_std_id BIGINT;
    v_plant_code VARCHAR(8);
    v_plant_name VARCHAR(32);
    si RECORD;
BEGIN
    FOR r IN
        SELECT id AS trigger_id, item_code, item_name, item_barcode, batch_no,
               process_name, process_code, plant_code, plant_name
        FROM qms.fai_change_trigger
        WHERE is_deleted=0
        ORDER BY id
    LOOP
        v_cnt := v_cnt + 1;
        v_plant_code := r.plant_code;
        v_plant_name := r.plant_name;

        INSERT INTO qms.fai_inspection_record
            (fai_no, change_trigger_id, material_code, material_name, batch_no,
             process_name, process_code, work_order_no,
             inspection_result, signature_status, plant_code, plant_name,
             created_by, is_deleted, version, created_at, updated_at)
        VALUES
            ('FAI-' || to_char(now(),'YYYYMMDD') || '-' || lpad(v_cnt::text, 3, '0'),
             r.trigger_id, r.item_code, r.item_name, r.batch_no,
             r.process_name, r.process_code, 'WO-REBUILD-' || lpad(v_cnt::text,3,'0'),
             CASE WHEN v_cnt <= 6 THEN '合格' ELSE '待判定' END,
             CASE WHEN v_cnt <= 6 THEN '已签' ELSE '未签' END,
             v_plant_code, v_plant_name,
             'rebuild', 0, 1, now(), now())
        RETURNING id INTO v_rec_id;

        -- 明细：复制该物料（或同厂区任一物料）标准的参数项；物料直接取自身标准，产品取同厂区首个物料标准
        IF r.item_code LIKE '99.11.%' THEN
            v_std_id := (SELECT id FROM qms.fai_inspection_standard
                         WHERE is_deleted=0 AND material_code = r.item_code LIMIT 1);
        ELSE
            v_std_id := (SELECT id FROM qms.fai_inspection_standard
                         WHERE is_deleted=0 AND plant_code = v_plant_code
                         ORDER BY id LIMIT 1);
        END IF;

        IF v_std_id IS NOT NULL THEN
            FOR si IN
                SELECT param_name, param_code, standard_value, upper_limit, lower_limit, unit, param_category
                FROM qms.fai_inspection_standard_item
                WHERE is_deleted=0 AND standard_id = v_std_id
                ORDER BY sort_order
            LOOP
                INSERT INTO qms.fai_inspection_item
                    (fai_record_id, param_name, param_code, standard_value, upper_limit, lower_limit,
                     actual_value, unit, result, sort_order, plant_code, plant_name,
                     created_by, is_deleted, version, created_at, updated_at,
                     param_category, standard_item_id, spc_enabled)
                VALUES
                    (v_rec_id, si.param_name, si.param_code, si.standard_value, si.upper_limit, si.lower_limit,
                     NULL, si.unit, CASE WHEN v_cnt <= 6 THEN '合格' ELSE '未检' END,
                     (SELECT COALESCE(MAX(sort_order),0)+1 FROM qms.fai_inspection_item WHERE fai_record_id=v_rec_id),
                     v_plant_code, v_plant_name, 'rebuild', 0, 1, now(), now(),
                     si.param_category, v_std_id, '否');
            END LOOP;
        END IF;

        -- 已签记录建电子签名
        IF v_cnt <= 6 THEN
            INSERT INTO qms.fai_signature
                (fai_record_id, signer_id, signer_name, sign_type, signature_hash, signed_at,
                 sign_reason, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at)
            VALUES
                (v_rec_id, 'SZ-QC-001', '质检员张工', '检验签名',
                 md5('FAI-'||v_rec_id||'-rebuild'),
                 now(), '首件检验合格放行',
                 v_plant_code, v_plant_name, 'rebuild', 0, 1, now(), now());
        END IF;
    END LOOP;
END $$;
