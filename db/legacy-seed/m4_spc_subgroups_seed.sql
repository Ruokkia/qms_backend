-- ============================================================
-- m4_spc_subgroups_seed.sql
-- SPC 过程能力分析 演示用子组数据（每参数 25 组，覆盖 Xbar-R 与 Xbar-s）
-- 深圳 SZ + 梅州 MZ，依 spc_parameter 现存的工序/参数生成
-- 数值围绕 target_value 在规格内波动，用于演示控制图与过程能力
-- ============================================================

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
    -- 演示用产品代码：随厂区+参数生成，使控制图可按代码关联/模糊搜索命中
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
         p.plant_code, p.plant_name, 'PRODUCT', demo_code, 'seed', 'seed')
      RETURNING id INTO sid;

      FOR s IN 1..p.subgroup_size LOOP
        v := round((p.target_value + spread * (random() * 2 - 1))::numeric, 6);
        INSERT INTO qms.spc_sample
          (subgroup_id, sample_no, sample_value, plant_code, plant_name, created_by, updated_by)
        VALUES
          (sid, s, v, p.plant_code, p.plant_name, 'seed', 'seed');
      END LOOP;
    END LOOP;
  END LOOP;
END $$;

-- 回填子组统计量（均值 / 极差 / 标准差）
WITH agg AS (
  SELECT subgroup_id,
         round(avg(sample_value), 6)                                            AS m,
         round(max(sample_value) - min(sample_value), 6)                       AS rg,
         round(stddev_samp(sample_value), 6)                                   AS sd
  FROM qms.spc_sample
  WHERE is_deleted = 0
  GROUP BY subgroup_id
)
UPDATE qms.spc_subgroup sub
SET mean_value = agg.m,
    range_value = agg.rg,
    std_dev = agg.sd
FROM agg
WHERE sub.id = agg.subgroup_id;

SELECT 'spc_subgroup seed done: ' || count(*) AS result FROM qms.spc_subgroup WHERE is_deleted = 0;
