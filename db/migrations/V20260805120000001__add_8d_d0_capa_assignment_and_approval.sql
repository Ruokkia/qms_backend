-- 8D/CAPA 流程改造（M2）
-- 1) Exception8d 增加 D0 发起环节、D1 结构化成员指派、CAPA 负责人、阶段审批状态
-- 2) exception_8d_step_log 增加审批状态字段
-- 3) 新建 exception_approval_config：阶段级可配置审批（8D + CAPA 通用）

-- ====== 1. exception_8d 扩展 ======

ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS d0_symptom text;
COMMENT ON COLUMN qms.exception_8d.d0_symptom IS 'D0 质量部发起说明（立案情由 / 不良现象概述）';

ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS d0_initiator character varying(64);
COMMENT ON COLUMN qms.exception_8d.d0_initiator IS 'D0 发起责任人（质量部发起者姓名）';

ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS d0_initiate_time timestamp without time zone;
COMMENT ON COLUMN qms.exception_8d.d0_initiate_time IS 'D0 发起时间';

-- D1 由纯文本改造为结构化：保留 d1_team（JSON 数组：成员姓名列表）并新增 capa_owner（JSON 数组：CAPA 负责人姓名列表）
ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS capa_owner text;
COMMENT ON COLUMN qms.exception_8d.capa_owner IS 'CAPA 负责人姓名列表（JSON 数组，与 8D 团队对称指派）';

ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS step_status character varying(16) DEFAULT 'DRAFT' NOT NULL;
COMMENT ON COLUMN qms.exception_8d.step_status IS '当前阶段审批状态：DRAFT(草稿)/SUBMITTED(已提交)/PENDING_APPROVAL(待审)/APPROVED(已通过)/REJECTED(已驳回)';

-- ====== 2. exception_8d_step_log 扩展审批状态 ======

ALTER TABLE qms.exception_8d_step_log
    ADD COLUMN IF NOT EXISTS approval_status character varying(16);
COMMENT ON COLUMN qms.exception_8d_step_log.approval_status IS '该步骤提交后的审批状态：PENDING_APPROVAL/APPROVED/REJECTED（未配置审批则为空）';

ALTER TABLE qms.exception_8d_step_log
    ADD COLUMN IF NOT EXISTS approver character varying(64);
COMMENT ON COLUMN qms.exception_8d_step_log.approver IS '审批人姓名';

ALTER TABLE qms.exception_8d_step_log
    ADD COLUMN IF NOT EXISTS approval_comment text;
COMMENT ON COLUMN qms.exception_8d_step_log.approval_comment IS '审批意见（通过/驳回理由）';

ALTER TABLE qms.exception_8d_step_log
    ADD COLUMN IF NOT EXISTS approval_time timestamp without time zone;
COMMENT ON COLUMN qms.exception_8d_step_log.approval_time IS '审批时间';

-- ====== 3. 新建 exception_approval_config（阶段级可配置审批，8D + CAPA 通用） ======

CREATE TABLE IF NOT EXISTS qms.exception_approval_config (
    id              bigint NOT NULL,
    process_flow    character varying(8) NOT NULL,   -- 8D / CAPA
    stage           character varying(8) NOT NULL,   -- 8D: D0-D8；CAPA: C1-C4
    stage_name      character varying(64),           -- 阶段中文名（展示用）
    need_approval   smallint DEFAULT 0 NOT NULL,     -- 0=不需要 1=需要
    approver_role   character varying(8),            -- 审批角色：R04(质量工程师) / R06(质量经理)
    is_default      smallint DEFAULT 1 NOT NULL,     -- 是否默认配置（全局默认一套）
    plant_code      character varying(8) NOT NULL DEFAULT 'SZ', -- 数据隔离维度（当前仅全局默认，固定 SZ）
    plant_name      character varying(32) DEFAULT '深圳',
    is_deleted      smallint DEFAULT 0 NOT NULL,
    version         integer DEFAULT 1 NOT NULL,
    created_at      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at      timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_exception_approval_config PRIMARY KEY (id)
);

COMMENT ON TABLE qms.exception_approval_config IS '异常整改阶段级审批配置（M2：8D 与 CAPA 共用，按阶段配置是否需审批及审批角色）';
COMMENT ON COLUMN qms.exception_approval_config.process_flow IS '流程维度：8D / CAPA';
COMMENT ON COLUMN qms.exception_approval_config.stage IS '阶段码：8D 为 D0-D8，CAPA 为 C1-C4';
COMMENT ON COLUMN qms.exception_approval_config.stage_name IS '阶段中文名';
COMMENT ON COLUMN qms.exception_approval_config.need_approval IS '是否需审批：0=否 1=是';
COMMENT ON COLUMN qms.exception_approval_config.approver_role IS '审批角色：R04=质量工程师 R06=质量经理';
COMMENT ON COLUMN qms.exception_approval_config.is_default IS '是否默认配置（全局默认一套）';

CREATE SEQUENCE IF NOT EXISTS qms.exception_approval_config_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER TABLE qms.exception_approval_config ALTER COLUMN id
    SET DEFAULT nextval('qms.exception_approval_config_id_seq');

-- 唯一约束：同一流程+阶段+分公司仅一条默认配置
CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_config_flow_stage
    ON qms.exception_approval_config (process_flow, stage, plant_code)
    WHERE is_deleted = 0;

CREATE INDEX IF NOT EXISTS idx_approval_config_flow
    ON qms.exception_approval_config (process_flow, plant_code, is_deleted);

-- ====== 4. 默认审批配置种子数据 ======
-- 8D：D3(R04)、D4(R04)、D8(R06) 需审批；CAPA：C2(R04) 措施审批、C4(R06) 效果验证审批
INSERT INTO qms.exception_approval_config (process_flow, stage, stage_name, need_approval, approver_role, is_default, plant_code, plant_name)
VALUES
    ('8D', 'D0', 'D0 发起立案', 0, NULL, 1, 'SZ', '深圳'),
    ('8D', 'D1', 'D1 团队成立', 0, NULL, 1, 'SZ', '深圳'),
    ('8D', 'D2', 'D2 问题描述', 0, NULL, 1, 'SZ', '深圳'),
    ('8D', 'D3', 'D3 临时遏制', 1, 'R04', 1, 'SZ', '深圳'),
    ('8D', 'D4', 'D4 根本原因', 1, 'R04', 1, 'SZ', '深圳'),
    ('8D', 'D5', 'D5 纠正措施', 0, NULL, 1, 'SZ', '深圳'),
    ('8D', 'D6', 'D6 实施验证', 0, NULL, 1, 'SZ', '深圳'),
    ('8D', 'D7', 'D7 预防措施', 0, NULL, 1, 'SZ', '深圳'),
    ('8D', 'D8', 'D8 团队表彰', 1, 'R06', 1, 'SZ', '深圳'),
    ('CAPA', 'C1', 'C1 措施制定', 0, NULL, 1, 'SZ', '深圳'),
    ('CAPA', 'C2', 'C2 措施审批', 1, 'R04', 1, 'SZ', '深圳'),
    ('CAPA', 'C3', 'C3 措施实施', 0, NULL, 1, 'SZ', '深圳'),
    ('CAPA', 'C4', 'C4 效果验证', 1, 'R06', 1, 'SZ', '深圳')
ON CONFLICT DO NOTHING;
