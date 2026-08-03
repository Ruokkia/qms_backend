-- ============================================================================
-- 端到端测试数据准备
-- 依赖：V20260802120000001（FAI 变更触发重建）、V20260802130000001（检验记录/标准重建）、
--       V20260802140000001（SPC 子组重建）、V20260802150000001（补 item_type 列）
-- 改动范围：
--   1. 检验标准维护：新增 5 条 PRODUCT 标准 + 15 条标准项（产品代码配置工序和参数项）
--   2. 变更触发管理：10 条 trigger status '待检验' → '已建单'（确保已建单才在首件检验执行显示）
--   3. 数据采集：20 条子组 + 样本（4 条 SZ 已签合格记录各 5 子组，方便控制图展示）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 检验标准维护：新增 PRODUCT 标准（5 条产品 × 3 参数项 = 15 条标准项）
--    让「标准维护」tab 能按产品代码配置工序和参数项
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_std_id BIGINT;
BEGIN
    -- 10.09.001.001 康立数字示波器 → 装配
    INSERT INTO qms.fai_inspection_standard
        (item_type, item_code, item_name, material_code, material_name,
         process_name, process_code, std_version, is_active, plant_code, plant_name,
         created_by, is_deleted, version, created_at, updated_at)
    VALUES
        ('PRODUCT', '10.09.001.001', '康立数字示波器', '10.09.001.001', '康立数字示波器',
         '装配', 'PROC-ASM-01', 1, '是', 'SZ', '深圳分公司',
         'e2e-prep', 0, 1, now(), now())
    RETURNING id INTO v_std_id;
    INSERT INTO qms.fai_inspection_standard_item
        (standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit,
         is_required, sort_order, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at,
         param_category, spc_enabled)
    VALUES
        (v_std_id, '外观检查', 'AQL', '无划伤/无毛刺/无氧化', NULL, NULL, '—', '是', 1, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '外观', '否'),
        (v_std_id, '关键尺寸', 'DIM', '50.00', 50.20, 49.80, 'mm', '是', 2, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '关键尺寸', '否'),
        (v_std_id, '性能参数', 'PERF', '合格', NULL, NULL, '—', '是', 3, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '性能', '否');

    -- 10.09.001.002 主控板组件 → 焊接
    INSERT INTO qms.fai_inspection_standard
        (item_type, item_code, item_name, material_code, material_name,
         process_name, process_code, std_version, is_active, plant_code, plant_name,
         created_by, is_deleted, version, created_at, updated_at)
    VALUES
        ('PRODUCT', '10.09.001.002', '主控板组件', '10.09.001.002', '主控板组件',
         '焊接', 'PROC-SMT-01', 1, '是', 'SZ', '深圳分公司',
         'e2e-prep', 0, 1, now(), now())
    RETURNING id INTO v_std_id;
    INSERT INTO qms.fai_inspection_standard_item
        (standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit,
         is_required, sort_order, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at,
         param_category, spc_enabled)
    VALUES
        (v_std_id, '外观检查', 'AQL', '无虚焊/无短路/无锡珠', NULL, NULL, '—', '是', 1, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '外观', '否'),
        (v_std_id, '焊点强度', 'SMT-STR', '≥50', NULL, 50.00, 'N', '是', 2, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '关键尺寸', '否'),
        (v_std_id, '焊接温度', 'WDG-TEMP', '250.00', 260.00, 240.00, '°C', '是', 3, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '性能', '是');

    -- 10.09.001.003 电源模块 → 装配
    INSERT INTO qms.fai_inspection_standard
        (item_type, item_code, item_name, material_code, material_name,
         process_name, process_code, std_version, is_active, plant_code, plant_name,
         created_by, is_deleted, version, created_at, updated_at)
    VALUES
        ('PRODUCT', '10.09.001.003', '电源模块', '10.09.001.003', '电源模块',
         '装配', 'PROC-ASM-01', 1, '是', 'SZ', '深圳分公司',
         'e2e-prep', 0, 1, now(), now())
    RETURNING id INTO v_std_id;
    INSERT INTO qms.fai_inspection_standard_item
        (standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit,
         is_required, sort_order, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at,
         param_category, spc_enabled)
    VALUES
        (v_std_id, '外观检查', 'AQL', '无破损/无变形/标识清晰', NULL, NULL, '—', '是', 1, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '外观', '否'),
        (v_std_id, '输出电压', 'V-OUT', '5.00', 5.25, 4.75, 'V', '是', 2, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '关键尺寸', '否'),
        (v_std_id, '装配压力', 'AX-PRES', '5.00', 5.50, 4.50, 'MPa', '是', 3, 'SZ', '深圳分公司', 'e2e-prep', 0, 1, now(), now(), '性能', '是');

    -- 10.09.002.001 工业控制器 → 装配（DG）
    INSERT INTO qms.fai_inspection_standard
        (item_type, item_code, item_name, material_code, material_name,
         process_name, process_code, std_version, is_active, plant_code, plant_name,
         created_by, is_deleted, version, created_at, updated_at)
    VALUES
        ('PRODUCT', '10.09.002.001', '工业控制器', '10.09.002.001', '工业控制器',
         '装配', 'PROC-ASM-02', 1, '是', 'DG', '东莞分公司',
         'e2e-prep', 0, 1, now(), now())
    RETURNING id INTO v_std_id;
    INSERT INTO qms.fai_inspection_standard_item
        (standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit,
         is_required, sort_order, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at,
         param_category, spc_enabled)
    VALUES
        (v_std_id, '外观检查', 'AQL', '无划伤/面板平整/接口完好', NULL, NULL, '—', '是', 1, 'DG', '东莞分公司', 'e2e-prep', 0, 1, now(), now(), '外观', '否'),
        (v_std_id, '关键尺寸', 'DIM', '100.00', 100.50, 99.50, 'mm', '是', 2, 'DG', '东莞分公司', 'e2e-prep', 0, 1, now(), now(), '关键尺寸', '否'),
        (v_std_id, '性能参数', 'PERF', '合格', NULL, NULL, '—', '是', 3, 'DG', '东莞分公司', 'e2e-prep', 0, 1, now(), now(), '性能', '否');

    -- 10.09.002.002 通信模块 → 焊接（DG）
    INSERT INTO qms.fai_inspection_standard
        (item_type, item_code, item_name, material_code, material_name,
         process_name, process_code, std_version, is_active, plant_code, plant_name,
         created_by, is_deleted, version, created_at, updated_at)
    VALUES
        ('PRODUCT', '10.09.002.002', '通信模块', '10.09.002.002', '通信模块',
         '焊接', 'PROC-SMT-02', 1, '是', 'DG', '东莞分公司',
         'e2e-prep', 0, 1, now(), now())
    RETURNING id INTO v_std_id;
    INSERT INTO qms.fai_inspection_standard_item
        (standard_id, param_name, param_code, standard_value, upper_limit, lower_limit, unit,
         is_required, sort_order, plant_code, plant_name, created_by, is_deleted, version, created_at, updated_at,
         param_category, spc_enabled)
    VALUES
        (v_std_id, '外观检查', 'AQL', '无虚焊/天线完整/屏蔽罩完好', NULL, NULL, '—', '是', 1, 'DG', '东莞分公司', 'e2e-prep', 0, 1, now(), now(), '外观', '否'),
        (v_std_id, '焊点强度', 'SMT-STR', '≥50', NULL, 50.00, 'N', '是', 2, 'DG', '东莞分公司', 'e2e-prep', 0, 1, now(), now(), '关键尺寸', '否'),
        (v_std_id, '信号强度', 'SIG-STR', '-20.00', -18.00, -22.00, 'dBm', '是', 3, 'DG', '东莞分公司', 'e2e-prep', 0, 1, now(), now(), '性能', '否');
END $$;

-- ---------------------------------------------------------------------------
-- 2. 变更触发管理：10 条 trigger status '待检验' → '已建单'
--    确保已建单才在首件检验执行 tab 显示
-- ---------------------------------------------------------------------------
UPDATE qms.fai_change_trigger
SET status     = '已建单',
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'e2e-prep'
WHERE is_deleted = 0
  AND status = '待检验';

-- ---------------------------------------------------------------------------
-- 3. 数据采集：20 条子组 + 样本（4 条 SZ 已签合格 FAI 记录各 5 子组）
--    Record #1: 10.09.001.001 示波器 → 装配/轴径(AX-DIA)  5子组×5样本
--    Record #2: 10.09.001.002 主控板 → 焊接/焊接温度(WDG-TEMP) 5子组×5样本
--    Record #3: 10.09.001.003 电源模块 → 装配/装配压力(AX-PRES) 5子组×5样本
--    Record #6: 99.11.100501 ARM处理器 → 焊接/焊接扭矩(WDG-TRQ) 5子组×4样本
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    rec1 RECORD;
    rec2 RECORD;
    rec3 RECORD;
    rec4 RECORD;
    v_param_id BIGINT;
    v_subgroup_size INT;
    v_target NUMERIC(18,6);
    v_usl NUMERIC(18,6);
    v_lsl NUMERIC(18,6);
    v_spread NUMERIC(18,6);
    g INT;
    s INT;
    sid BIGINT;
    v_val NUMERIC(18,6);
    sub_no TEXT;
    v_date_str TEXT;
BEGIN
    v_date_str := to_char(now(), 'YYYYMMDD');

    -- 定位 4 条 SZ 已签合格 FAI 检验记录（按 item_code + plant_code 精准匹配）
    SELECT r.id, r.item_code, r.item_name, r.process_name
      INTO rec1
      FROM qms.fai_inspection_record r
      WHERE r.is_deleted = 0
        AND r.plant_code = 'SZ'
        AND r.item_code = '10.09.001.001'
        AND r.inspection_result = '合格'
        AND r.signature_status = '已签'
      LIMIT 1;

    SELECT r.id, r.item_code, r.item_name, r.process_name
      INTO rec2
      FROM qms.fai_inspection_record r
      WHERE r.is_deleted = 0
        AND r.plant_code = 'SZ'
        AND r.item_code = '10.09.001.002'
        AND r.inspection_result = '合格'
        AND r.signature_status = '已签'
      LIMIT 1;

    SELECT r.id, r.item_code, r.item_name, r.process_name
      INTO rec3
      FROM qms.fai_inspection_record r
      WHERE r.is_deleted = 0
        AND r.plant_code = 'SZ'
        AND r.item_code = '10.09.001.003'
        AND r.inspection_result = '合格'
        AND r.signature_status = '已签'
      LIMIT 1;

    SELECT r.id, r.item_code, r.item_name, r.process_name
      INTO rec4
      FROM qms.fai_inspection_record r
      WHERE r.is_deleted = 0
        AND r.plant_code = 'SZ'
        AND r.item_code = '99.11.100501'
        AND r.inspection_result = '合格'
        AND r.signature_status = '已签'
      LIMIT 1;

    -- ======================================================================
    -- Record #1: 示波器 → 装配 / AX-DIA 轴径 (10.00±0.05, subgroup_size=5)
    -- ======================================================================
    IF rec1.id IS NOT NULL THEN
        SELECT id, subgroup_size, target_value, upper_spec_limit, lower_spec_limit
          INTO v_param_id, v_subgroup_size, v_target, v_usl, v_lsl
          FROM qms.spc_parameter
          WHERE is_deleted = 0 AND plant_code = 'SZ' AND param_code = 'AX-DIA';
        v_spread := (v_usl - v_lsl) / 6.0;

        FOR g IN 1..5 LOOP
            sub_no := 'SG-FAI-SZ-AX-DIA-' || v_date_str || '-' || lpad(g::text, 4, '0');
            INSERT INTO qms.spc_subgroup
                (param_id, subgroup_no, sample_count, mean_value, range_value, std_dev,
                 sample_time, source_type, fai_record_id, plant_code, plant_name,
                 item_type, item_code, created_by, updated_by, is_deleted, version, created_at, updated_at)
            VALUES
                (v_param_id, sub_no, v_subgroup_size, 0, 0, 0,
                 now() - (5 - g) * interval '2 hours', 'FAI采集', rec1.id, 'SZ', '深圳分公司',
                 'PRODUCT', rec1.item_code, 'e2e-prep', 'e2e-prep', 0, 1, now(), now())
            RETURNING id INTO sid;

            FOR s IN 1..v_subgroup_size LOOP
                v_val := round((v_target + v_spread * (random() * 2.5 - 1.25))::numeric, 6);
                INSERT INTO qms.spc_sample
                    (subgroup_id, sample_no, sample_value, plant_code, plant_name, created_by, updated_by, is_deleted, version, created_at, updated_at)
                VALUES
                    (sid, s, v_val, 'SZ', '深圳分公司', 'e2e-prep', 'e2e-prep', 0, 1, now(), now());
            END LOOP;
        END LOOP;
    END IF;

    -- ======================================================================
    -- Record #2: 主控板 → 焊接 / WDG-TEMP 焊接温度 (250±10, subgroup_size=5)
    -- ======================================================================
    IF rec2.id IS NOT NULL THEN
        SELECT id, subgroup_size, target_value, upper_spec_limit, lower_spec_limit
          INTO v_param_id, v_subgroup_size, v_target, v_usl, v_lsl
          FROM qms.spc_parameter
          WHERE is_deleted = 0 AND plant_code = 'SZ' AND param_code = 'WDG-TEMP';
        v_spread := (v_usl - v_lsl) / 6.0;

        FOR g IN 1..5 LOOP
            sub_no := 'SG-FAI-SZ-WDG-TEMP-' || v_date_str || '-' || lpad(g::text, 4, '0');
            INSERT INTO qms.spc_subgroup
                (param_id, subgroup_no, sample_count, mean_value, range_value, std_dev,
                 sample_time, source_type, fai_record_id, plant_code, plant_name,
                 item_type, item_code, created_by, updated_by, is_deleted, version, created_at, updated_at)
            VALUES
                (v_param_id, sub_no, v_subgroup_size, 0, 0, 0,
                 now() - (5 - g) * interval '2 hours', 'FAI采集', rec2.id, 'SZ', '深圳分公司',
                 'PRODUCT', rec2.item_code, 'e2e-prep', 'e2e-prep', 0, 1, now(), now())
            RETURNING id INTO sid;

            FOR s IN 1..v_subgroup_size LOOP
                v_val := round((v_target + v_spread * (random() * 2.5 - 1.25))::numeric, 6);
                INSERT INTO qms.spc_sample
                    (subgroup_id, sample_no, sample_value, plant_code, plant_name, created_by, updated_by, is_deleted, version, created_at, updated_at)
                VALUES
                    (sid, s, v_val, 'SZ', '深圳分公司', 'e2e-prep', 'e2e-prep', 0, 1, now(), now());
            END LOOP;
        END LOOP;
    END IF;

    -- ======================================================================
    -- Record #3: 电源模块 → 装配 / AX-PRES 装配压力 (5.0±0.5, subgroup_size=5)
    -- ======================================================================
    IF rec3.id IS NOT NULL THEN
        SELECT id, subgroup_size, target_value, upper_spec_limit, lower_spec_limit
          INTO v_param_id, v_subgroup_size, v_target, v_usl, v_lsl
          FROM qms.spc_parameter
          WHERE is_deleted = 0 AND plant_code = 'SZ' AND param_code = 'AX-PRES';
        v_spread := (v_usl - v_lsl) / 6.0;

        FOR g IN 1..5 LOOP
            sub_no := 'SG-FAI-SZ-AX-PRES-' || v_date_str || '-' || lpad(g::text, 4, '0');
            INSERT INTO qms.spc_subgroup
                (param_id, subgroup_no, sample_count, mean_value, range_value, std_dev,
                 sample_time, source_type, fai_record_id, plant_code, plant_name,
                 item_type, item_code, created_by, updated_by, is_deleted, version, created_at, updated_at)
            VALUES
                (v_param_id, sub_no, v_subgroup_size, 0, 0, 0,
                 now() - (5 - g) * interval '2 hours', 'FAI采集', rec3.id, 'SZ', '深圳分公司',
                 'PRODUCT', rec3.item_code, 'e2e-prep', 'e2e-prep', 0, 1, now(), now())
            RETURNING id INTO sid;

            FOR s IN 1..v_subgroup_size LOOP
                v_val := round((v_target + v_spread * (random() * 2.5 - 1.25))::numeric, 6);
                INSERT INTO qms.spc_sample
                    (subgroup_id, sample_no, sample_value, plant_code, plant_name, created_by, updated_by, is_deleted, version, created_at, updated_at)
                VALUES
                    (sid, s, v_val, 'SZ', '深圳分公司', 'e2e-prep', 'e2e-prep', 0, 1, now(), now());
            END LOOP;
        END LOOP;
    END IF;

    -- ======================================================================
    -- Record #6: ARM处理器 → 焊接 / WDG-TRQ 焊接扭矩 (5.0±0.5, subgroup_size=4)
    -- ======================================================================
    IF rec4.id IS NOT NULL THEN
        SELECT id, subgroup_size, target_value, upper_spec_limit, lower_spec_limit
          INTO v_param_id, v_subgroup_size, v_target, v_usl, v_lsl
          FROM qms.spc_parameter
          WHERE is_deleted = 0 AND plant_code = 'SZ' AND param_code = 'WDG-TRQ';
        v_spread := (v_usl - v_lsl) / 6.0;

        FOR g IN 1..5 LOOP
            sub_no := 'SG-FAI-SZ-WDG-TRQ-' || v_date_str || '-' || lpad(g::text, 4, '0');
            INSERT INTO qms.spc_subgroup
                (param_id, subgroup_no, sample_count, mean_value, range_value, std_dev,
                 sample_time, source_type, fai_record_id, plant_code, plant_name,
                 item_type, item_code, created_by, updated_by, is_deleted, version, created_at, updated_at)
            VALUES
                (v_param_id, sub_no, v_subgroup_size, 0, 0, 0,
                 now() - (5 - g) * interval '2 hours', 'FAI采集', rec4.id, 'SZ', '深圳分公司',
                 'MATERIAL', rec4.item_code, 'e2e-prep', 'e2e-prep', 0, 1, now(), now())
            RETURNING id INTO sid;

            FOR s IN 1..v_subgroup_size LOOP
                v_val := round((v_target + v_spread * (random() * 2.5 - 1.25))::numeric, 6);
                INSERT INTO qms.spc_sample
                    (subgroup_id, sample_no, sample_value, plant_code, plant_name, created_by, updated_by, is_deleted, version, created_at, updated_at)
                VALUES
                    (sid, s, v_val, 'SZ', '深圳分公司', 'e2e-prep', 'e2e-prep', 0, 1, now(), now());
            END LOOP;
        END LOOP;
    END IF;
END $$;

-- 回填新子组统计量（均值 / 极差 / 标准差）
WITH agg AS (
    SELECT subgroup_id,
           round(avg(sample_value), 6) AS m,
           round(max(sample_value) - min(sample_value), 6) AS rg,
           round(stddev_samp(sample_value), 6) AS sd
    FROM qms.spc_sample
    WHERE is_deleted = 0
      AND updated_by = 'e2e-prep'
    GROUP BY subgroup_id
)
UPDATE qms.spc_subgroup sub
SET mean_value = agg.m,
    range_value = agg.rg,
    std_dev    = agg.sd,
    version    = sub.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM agg
WHERE sub.id = agg.subgroup_id
  AND sub.is_deleted = 0;

-- ---------------------------------------------------------------------------
-- 4. 重建受影响参数的过程能力 capability 与控制限 control_limit
--    仅针对 SZ 厂区 4 个有新增子组的参数重算
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    rec_p RECORD;
    cf RECORD;
    sub_n INT;
    xbar_bar NUMERIC(18,6);
    rbar NUMERIC(18,6);
    sbar NUMERIC(18,6);
    sigma_within NUMERIC(18,6);
    cpu NUMERIC(18,6);
    cpl NUMERIC(18,6);
    cp NUMERIC(18,6);
    cpk NUMERIC(18,6);
    pp NUMERIC(18,6);
    ppk NUMERIC(18,6);
    xbar_ucl NUMERIC(18,6);
    xbar_lcl NUMERIC(18,6);
    xbar_cl NUMERIC(18,6);
    r_ucl NUMERIC(18,6);
    r_lcl NUMERIC(18,6);
    s_ucl NUMERIC(18,6);
    s_lcl NUMERIC(18,6);
    cnt INT;
BEGIN
    -- 删除受影响参数旧 capability 和 control_limit
    DELETE FROM qms.spc_capability
    WHERE plant_code = 'SZ'
      AND param_id IN (SELECT id FROM qms.spc_parameter WHERE is_deleted=0 AND plant_code='SZ'
                       AND param_code IN ('AX-DIA','AX-PRES','WDG-TEMP','WDG-TRQ'));

    DELETE FROM qms.spc_control_limit
    WHERE plant_code = 'SZ'
      AND param_id IN (SELECT id FROM qms.spc_parameter WHERE is_deleted=0 AND plant_code='SZ'
                       AND param_code IN ('AX-DIA','AX-PRES','WDG-TEMP','WDG-TRQ'));

    FOR rec_p IN
        SELECT id, param_code, chart_type, subgroup_size,
               upper_spec_limit AS usl, lower_spec_limit AS lsl,
               plant_code, plant_name
        FROM qms.spc_parameter
        WHERE is_deleted = 0
          AND plant_code = 'SZ'
          AND param_code IN ('AX-DIA','AX-PRES','WDG-TEMP','WDG-TRQ')
    LOOP
        sub_n := rec_p.subgroup_size;
        SELECT c.* INTO cf FROM qms.spc_coefficient c WHERE c.n = sub_n LIMIT 1;

        -- 总体均值与极差/标准差均值（基于当前所有子组）
        SELECT avg(mean_value), avg(range_value), avg(std_dev), count(*)
          INTO xbar_bar, rbar, sbar, cnt
        FROM qms.spc_subgroup
        WHERE is_deleted = 0 AND param_id = rec_p.id;

        -- 组内标准差估计
        IF rec_p.chart_type = 'Xbar-s' THEN
            sigma_within := sbar / cf.c4;
        ELSE
            sigma_within := rbar / cf.d2;
        END IF;

        -- 过程能力
        cpu := (rec_p.usl - xbar_bar) / (3 * sigma_within);
        cpl := (xbar_bar - rec_p.lsl) / (3 * sigma_within);
        cp  := (rec_p.usl - rec_p.lsl) / (6 * sigma_within);
        cpk := LEAST(cpu, cpl);
        pp  := (rec_p.usl - rec_p.lsl) / (6 * (SELECT stddev_samp(sample_value)
                                         FROM qms.spc_sample sm JOIN qms.spc_subgroup sg
                                           ON sm.subgroup_id = sg.id AND sg.is_deleted=0
                                         WHERE sg.param_id = rec_p.id AND sm.is_deleted=0));
        ppk := LEAST((rec_p.usl - xbar_bar)/(3*(SELECT stddev_samp(sample_value)
                                         FROM qms.spc_sample sm JOIN qms.spc_subgroup sg
                                           ON sm.subgroup_id = sg.id AND sg.is_deleted=0
                                         WHERE sg.param_id = rec_p.id AND sm.is_deleted=0)),
                     (xbar_bar - rec_p.lsl)/(3*(SELECT stddev_samp(sample_value)
                                         FROM qms.spc_sample sm JOIN qms.spc_subgroup sg
                                           ON sm.subgroup_id = sg.id AND sg.is_deleted=0
                                         WHERE sg.param_id = rec_p.id AND sm.is_deleted=0)));

        INSERT INTO qms.spc_capability
            (param_id, cp, cpk, pp, ppk, cpu, cpl, sample_count, subgroup_count, judgment,
             start_time, end_time, plant_code, plant_name, created_by, updated_by,
             is_deleted, version, created_at, updated_at)
        VALUES
            (rec_p.id, round(cp,6), round(cpk,6), round(pp,6), round(ppk,6), round(cpu,6), round(cpl,6),
             cnt * sub_n, cnt, '充足',
             now() - interval '1 day', now(), rec_p.plant_code, rec_p.plant_name, 'e2e-prep', 'e2e-prep',
             0, 1, now(), now());

        -- 控制限
        xbar_cl := xbar_bar;
        IF rec_p.chart_type = 'Xbar-s' THEN
            xbar_ucl := xbar_bar + cf.a3 * sbar;
            xbar_lcl := xbar_bar - cf.a3 * sbar;
            s_ucl := cf.b4 * sbar;
            s_lcl := cf.b3 * sbar;
            r_ucl := NULL; r_lcl := NULL;
        ELSE
            xbar_ucl := xbar_bar + cf.a2 * rbar;
            xbar_lcl := xbar_bar - cf.a2 * rbar;
            r_ucl := cf.d4 * rbar;
            r_lcl := cf.d3 * rbar;
            s_ucl := NULL; s_lcl := NULL;
        END IF;

        INSERT INTO qms.spc_control_limit
            (param_id, chart_type, xbar_ucl, xbar_cl, xbar_lcl, r_ucl, r_cl, r_lcl,
             s_ucl, s_cl, s_lcl, subgroup_count, calc_date, plant_code, plant_name,
             created_by, updated_by, is_deleted, version, created_at, updated_at)
        VALUES
            (rec_p.id, rec_p.chart_type, round(xbar_ucl,6), round(xbar_cl,6), round(xbar_lcl,6),
             round(r_ucl,6), round(rbar,6), round(r_lcl,6),
             round(s_ucl,6), round(sbar,6), round(s_lcl,6),
             cnt, now(), rec_p.plant_code, rec_p.plant_name, 'e2e-prep', 'e2e-prep',
             0, 1, now(), now());
    END LOOP;
END $$;