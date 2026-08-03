-- ============================================================================
-- 修复 SPC 过程能力/控制限：spc_coefficient 缺失 n=5 导致 capability/control_limit
-- 对 subgroup_size=5 的参数（含 SZ 全部参数）计算时 cf 为 NULL -> DO 块异常 -> 漏算。
-- 处理：
--   1) 补 spc_coefficient n=5 标准 AIAG-VDA 系数行（若不存在）
--   2) 逻辑删除 capability/control_limit 旧行，按所有参数（SZ+MZ）重算覆盖
-- ============================================================================

-- 1. 补系数（n=5）
INSERT INTO qms.spc_coefficient (n, a2, a3, d2, d3, d4, c4, b3, b4)
SELECT 5, 0.577, 1.427, 2.326, 0.864, 2.114, 0.940, 0.000, 2.089
WHERE NOT EXISTS (SELECT 1 FROM qms.spc_coefficient WHERE n = 5);

-- 2. 逻辑删除旧派生数据
UPDATE qms.spc_capability    SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='fix' WHERE is_deleted=0;
UPDATE qms.spc_control_limit SET is_deleted=1, version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='fix' WHERE is_deleted=0;

-- 3. 重算 capability + control_limit（覆盖所有参数）
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
  ss NUMERIC(18,6);
BEGIN
  FOR p IN
    SELECT id, param_code, chart_type, subgroup_size,
           upper_spec_limit AS usl, lower_spec_limit AS lsl,
           plant_code, plant_name
    FROM qms.spc_parameter WHERE is_deleted = 0
    ORDER BY id
  LOOP
    sub_n := p.subgroup_size;
    SELECT c.* INTO cf FROM qms.spc_coefficient c WHERE c.n = sub_n LIMIT 1;
    IF cf IS NULL THEN
      RAISE NOTICE 'skip param % (size %): no coefficient', p.id, sub_n;
      CONTINUE;
    END IF;

    SELECT avg(mean_value), avg(range_value), avg(std_dev), count(*)
      INTO xbar_bar, rbar, sbar, cnt
    FROM qms.spc_subgroup
    WHERE is_deleted = 0 AND param_id = p.id;

    IF cnt IS NULL OR cnt = 0 THEN
      RAISE NOTICE 'skip param %: no subgroups', p.id;
      CONTINUE;
    END IF;

    ss := (SELECT stddev_samp(sample_value)
           FROM qms.spc_sample sm JOIN qms.spc_subgroup sg ON sm.subgroup_id = sg.id AND sg.is_deleted=0
           WHERE sg.param_id = p.id AND sm.is_deleted=0);

    IF p.chart_type = 'Xbar-s' THEN
      sigma_within := sbar / cf.c4;
    ELSE
      sigma_within := rbar / cf.d2;
    END IF;

    cpu := (p.usl - xbar_bar) / (3 * sigma_within);
    cpl := (xbar_bar - p.lsl) / (3 * sigma_within);
    cp  := (p.usl - p.lsl) / (6 * sigma_within);
    cpk := LEAST(cpu, cpl);
    pp  := (p.usl - p.lsl) / (6 * ss);
    ppk := LEAST((p.usl - xbar_bar)/(3*ss), (xbar_bar - p.lsl)/(3*ss));

    INSERT INTO qms.spc_capability
      (param_id, cp, cpk, pp, ppk, cpu, cpl, sample_count, subgroup_count, judgment,
       start_time, end_time, plant_code, plant_name, created_by, updated_by,
       is_deleted, version, created_at, updated_at)
    VALUES
      (p.id, round(cp,6), round(cpk,6), round(pp,6), round(ppk,6), round(cpu,6), round(cpl,6),
       cnt * sub_n, cnt, '充足',
       now() - interval '1 day', now(), p.plant_code, p.plant_name, 'fix', 'fix',
       0, 1, now(), now());

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
       cnt, now(), p.plant_code, p.plant_name, 'fix', 'fix',
       0, 1, now(), now());
  END LOOP;
END $$;
