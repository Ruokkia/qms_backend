-- 清理 SPC 历史残留脏数据：修复「工序删除后关键参数及子表成为孤节点」导致的数据断裂。
-- 历史版本在删除 spc_process / spc_parameter 时未级联清理子表，导致 spc_parameter、
-- spc_subgroup、spc_sample、spc_control_limit、spc_capability 出现父子断裂的孤节点。
-- 本迁移对这些孤节点做【逻辑删除】(is_deleted = 1)，与系统其余逻辑删除保持一致，可审计、可回查。
-- 迁移幂等：仅对 is_deleted = 0 的记录生效，重复执行无副作用。

-- 1) 孤参数：父工序不存在或已被逻辑删除（含 process_id 为空的脏数据）
UPDATE qms.spc_parameter p
SET is_deleted = 1,
    updated_at = NOW()
WHERE p.is_deleted = 0
  AND (p.process_id IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM qms.spc_process pr
           WHERE pr.id = p.process_id AND pr.is_deleted = 0
       ));

-- 2) 孤子组：父参数不存在或已被逻辑删除
UPDATE qms.spc_subgroup sg
SET is_deleted = 1,
    updated_at = NOW()
WHERE sg.is_deleted = 0
  AND (sg.param_id IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM qms.spc_parameter pa
           WHERE pa.id = sg.param_id AND pa.is_deleted = 0
       ));

-- 3) 孤样本：父子组不存在或已被逻辑删除
UPDATE qms.spc_sample s
SET is_deleted = 1,
    updated_at = NOW()
WHERE s.is_deleted = 0
  AND (s.subgroup_id IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM qms.spc_subgroup sg
           WHERE sg.id = s.subgroup_id AND sg.is_deleted = 0
       ));

-- 4) 孤控制限：父参数不存在或已被逻辑删除
UPDATE qms.spc_control_limit cl
SET is_deleted = 1,
    updated_at = NOW()
WHERE cl.is_deleted = 0
  AND (cl.param_id IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM qms.spc_parameter pa
           WHERE pa.id = cl.param_id AND pa.is_deleted = 0
       ));

-- 5) 孤能力指数：父参数不存在或已被逻辑删除
UPDATE qms.spc_capability c
SET is_deleted = 1,
    updated_at = NOW()
WHERE c.is_deleted = 0
  AND (c.param_id IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM qms.spc_parameter pa
           WHERE pa.id = c.param_id AND pa.is_deleted = 0
       ));
