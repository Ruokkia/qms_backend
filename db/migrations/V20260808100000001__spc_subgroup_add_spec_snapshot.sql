-- SPC 子组标准快照：保存子组生成当时使用的公差副本（目标值/上下限），
-- 避免标准库改版后历史控制图与过程能力随之变动，满足药监核查与 ISO13485 合规。
-- 仅结构修改（ADD COLUMN），不删除/不修改任何历史数据。

ALTER TABLE qms.spc_subgroup
    ADD COLUMN IF NOT EXISTS target_value       NUMERIC(18, 6),
    ADD COLUMN IF NOT EXISTS upper_spec_limit    NUMERIC(18, 6),
    ADD COLUMN IF NOT EXISTS lower_spec_limit    NUMERIC(18, 6);

COMMENT ON COLUMN qms.spc_subgroup.target_value    IS '子组生成时所引用 SpcParameter 的目标值快照';
COMMENT ON COLUMN qms.spc_subgroup.upper_spec_limit IS '子组生成时所引用 SpcParameter 的规格上限快照';
COMMENT ON COLUMN qms.spc_subgroup.lower_spec_limit IS '子组生成时所引用 SpcParameter 的规格下限快照';
