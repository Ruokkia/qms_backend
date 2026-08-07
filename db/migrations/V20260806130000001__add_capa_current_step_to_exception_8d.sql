-- 添加 capa_current_step 列到 exception_8d 表，与实体类 Exception8d.capaCurrentStep 对应
ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS capa_current_step character varying(16);
COMMENT ON COLUMN qms.exception_8d.capa_current_step IS 'CAPA 流程当前阶段：C1-C4（选 CAPA 或 BOTH 时维护）';
