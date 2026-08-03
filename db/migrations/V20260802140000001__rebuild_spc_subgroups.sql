-- ============================================================================
-- 重建 SPC 子组 / 样本 / 过程能力 / 控制限
-- 保留工序演示码风格（DEMO-{plant}-{param}）与 SZ+MZ 厂区。
-- 清除旧脏数据（含 FG-DSO-2024 / DEMO-MZ 残留 / 201~205 等），重造干净一套：
--   - 按 spc_parameter（SZ+MZ 各工序参数）循环生成 25 子组/参数，子组下按 subgroup_size 生成样本
--   - 回填子组统计量（均值/极差/标准差）
--   - 依 spc_coefficient 系数表计算 capability（Cp/Cpk/Pp/Ppk）与 control_limit（Xbar-R / Xbar-s）
-- 不改动 spc_process / spc_parameter（用户要求保留 SZ+MZ 现状）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 逻辑删除 SPC 派生数据旧行
-- ---------------------------------------------------------------------------
UPDATE qms.spc_subgroup      SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.spc_sample        SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.spc_capability    SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;
UPDATE qms.spc_control_limit SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='rebuild' WHERE is_deleted=0;

-- ---------------------------------------------------------------------------
-- 1. 重造子组 + 样本（沿用 m4_spc_subgroups_seed.sql 风格）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  p RECORD;
  g INT;
  s INT;
  sid BIGINT;
  v NUMERIC(18,6);
  sub_no TEXT;
  spread NUMERIC(18,6);
  demo_code TEXT;
BEGIN
  FOR p IN
    SELECT id, param_code, plant_code, plant_name, target_value,
           subgroup_size, upper_spec_limit, lower_spec_limit
    FROM qms.spc_parameter WHERE is_deleted = 0
  LOOP
    spread := (p.upper_spec_limit - p.lower_spec_limit) / 6.0;
    demo_code := 'DEMO-' || p.plant_code || '-' || p.param_code;
    FOR g IN 1..25 LOOP
      sub_no := 'SG-' || p.plant_code || '-' || p.param_code || '-'
                || to_char(now(), 'yyyyMMdd') || '-' || lpad(g::text, 4, '0');
      INSERT INTO qms.spc_subgroup
        (param_id, subgroup_no, sample_count, mean_value, range_value, std_dev,
         sample_time, source_type, plant_code, plant_name, item_type, item_code,
         created_by, updated_by)
      VALUES
        (p.id, sub_no, p.subgroup_size, 0, 0, 0,
         now() - (25 - g) * interval '1 hour', '手动录入',
         p.plant_code, p.plant_name, 'PRODUCT', demo_code, 'rebuild', 'rebuild')
      RETURNING id INTO sid;

      FOR s IN 1..p.subgroup_size LOOP
        v := round((p.target_value + spread * (random() * 2 - 1))::numeric, 6);
        INSERT INTO qms.spc_sample
          (subgroup_id, sample_no, sample_value, plant_code, plant_name, created_by, updated_by)
        VALUES
          (sid, s, v, p.plant_code, p.plant_name, 'rebuild', 'rebuild');
      END LOOP;
    END LOOP;
  END LOOP;
END $$;

-- 回填子组统计量（均值 / 极差 / 标准差）
WITH agg AS (
  SELECT subgroup_id,
         round(avg(sample_value), 6)  AS m,
         round(max(sample_value) - min(sample_value), 6) AS rg,
         round(stddev_samp(sample_value), 6) AS sd
  FROM qms.spc_sample
  WHERE is_deleted = 0
  GROUP BY subgroup_id
)
UPDATE qms.spc_subgroup sub
SET mean_value = agg.m,
    range_value = agg.rg,
    std_dev = agg.sd
FROM agg
WHERE sub.id = agg.subgroup_id AND sub.is_deleted = 0;

-- ---------------------------------------------------------------------------
-- 2. 重建过程能力 capability 与 控制限 control_limit（按系数表计算）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  p RECORD;
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
  FOR p IN
    SELECT id, param_code, chart_type, subgroup_size,
           upper_spec_limit AS usl, lower_spec_limit AS lsl,
           plant_code, plant_name
    FROM qms.spc_parameter WHERE is_deleted = 0
  LOOP
    sub_n := p.subgroup_size;
    SELECT c.* INTO cf FROM qms.spc_coefficient c WHERE c.n = sub_n LIMIT 1;

    -- 总体均值与极差/标准差均值（基于新建子组）
    SELECT avg(mean_value), avg(range_value), avg(std_dev), count(*)
      INTO xbar_bar, rbar, sbar, cnt
    FROM qms.spc_subgroup
    WHERE is_deleted = 0 AND param_id = p.id;

    -- 组内标准差估计
    IF p.chart_type = 'Xbar-s' THEN
      sigma_within := sbar / cf.c4;
    ELSE
      sigma_within := rbar / cf.d2;
    END IF;

    -- 过程能力
    cpu := (p.usl - xbar_bar) / (3 * sigma_within);
    cpl := (xbar_bar - p.lsl) / (3 * sigma_within);
    cp  := (p.usl - p.lsl) / (6 * sigma_within);
    cpk := LEAST(cpu, cpl);
    pp  := (p.usl - p.lsl) / (6 * (SELECT stddev_samp(sample_value)
                                     FROM qms.spc_sample sm JOIN qms.spc_subgroup sg
                                       ON sm.subgroup_id = sg.id AND sg.is_deleted=0
                                     WHERE sg.param_id = p.id AND sm.is_deleted=0));
    ppk := LEAST((p.usl - xbar_bar)/(3*(SELECT stddev_samp(sample_value)
                                     FROM qms.spc_sample sm JOIN qms.spc_subgroup sg
                                       ON sm.subgroup_id = sg.id AND sg.is_deleted=0
                                     WHERE sg.param_id = p.id AND sm.is_deleted=0)),
                 (xbar_bar - p.lsl)/(3*(SELECT stddev_samp(sample_value)
                                     FROM qms.spc_sample sm JOIN qms.spc_subgroup sg
                                       ON sm.subgroup_id = sg.id AND sg.is_deleted=0
                                     WHERE sg.param_id = p.id AND sm.is_deleted=0)));

    INSERT INTO qms.spc_capability
      (param_id, cp, cpk, pp, ppk, cpu, cpl, sample_count, subgroup_count, judgment,
       start_time, end_time, plant_code, plant_name, created_by, updated_by,
       is_deleted, version, created_at, updated_at)
    VALUES
      (p.id, round(cp,6), round(cpk,6), round(pp,6), round(ppk,6), round(cpu,6), round(cpl,6),
       cnt * sub_n, cnt, '充足',
       now() - interval '1 day', now(), p.plant_code, p.plant_name, 'rebuild', 'rebuild',
       0, 1, now(), now());

    -- 控制限
    xbar_cl := xbar_bar;
    IF p.chart_type = 'Xbar-s' THEN
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
      (p.id, p.chart_type, round(xbar_ucl,6), round(xbar_cl,6), round(xbar_lcl,6),
       round(r_ucl,6), round(rbar,6), round(r_lcl,6),
       round(s_ucl,6), round(sbar,6), round(s_lcl,6),
       cnt, now(), p.plant_code, p.plant_name, 'rebuild', 'rebuild',
       0, 1, now(), now());
  END LOOP;
END $$;
