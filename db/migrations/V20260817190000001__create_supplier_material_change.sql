-- 供应商物料变更管理模块：建表（变更单主表 / 联合审批表）
-- 仅结构变更 + 标准扩展列，遵守 Migration Iron Law（不含 DELETE/UPDATE/TRUNCATE/DROP）。

-- 1. 供应商物料变更单主表
CREATE SEQUENCE qms.supplier_material_change_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_material_change (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_material_change_id_seq'::regclass),
    change_no character varying(64) NOT NULL,
    applicant_id bigint,
    applicant character varying(64),
    supplier_code character varying(64) NOT NULL,
    supplier_name character varying(128) NOT NULL,
    material_code character varying(64) NOT NULL,
    material_name character varying(128) NOT NULL,
    change_type character varying(16) NOT NULL,
    change_desc text,
    validation_report text,
    risk_assessment text,
    standard_id bigint,
    tightened_subgroup_size integer,
    spc_enabled smallint DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    reject_reason text,
    attachments text,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY qms.supplier_material_change ADD CONSTRAINT supplier_material_change_pkey PRIMARY KEY (id);
ALTER TABLE ONLY qms.supplier_material_change ADD CONSTRAINT uq_smc_change_no UNIQUE (change_no);
COMMENT ON TABLE qms.supplier_material_change IS '供应商物料变更单主表（规格/工艺/产地变更受控管理）';
COMMENT ON COLUMN qms.supplier_material_change.change_no IS '变更单号（规则 SMC-{plant}-{yyyyMMdd}-{4位流水}）';
COMMENT ON COLUMN qms.supplier_material_change.applicant_id IS '申请人用户ID';
COMMENT ON COLUMN qms.supplier_material_change.applicant IS '申请人姓名';
COMMENT ON COLUMN qms.supplier_material_change.change_type IS '变更类型：SPEC 规格 / PROCESS 工艺 / ORIGIN 产地';
COMMENT ON COLUMN qms.supplier_material_change.change_desc IS '变更说明';
COMMENT ON COLUMN qms.supplier_material_change.validation_report IS '验证报告（文本+附件URL JSON）';
COMMENT ON COLUMN qms.supplier_material_change.risk_assessment IS '风险评估';
COMMENT ON COLUMN qms.supplier_material_change.standard_id IS '关联的 FAI 检验标准 ID（可空，人工维护后回填，仅追溯）';
COMMENT ON COLUMN qms.supplier_material_change.tightened_subgroup_size IS '加严子组样本数（SPC 子组大小）';
COMMENT ON COLUMN qms.supplier_material_change.spc_enabled IS '是否联动 SPC：0 否 / 1 是';
COMMENT ON COLUMN qms.supplier_material_change.status IS '状态：DRAFT 草稿 / PENDING 审批中 / APPROVED 已批准 / REJECTED 已驳回 / VOID 已作废';
COMMENT ON COLUMN qms.supplier_material_change.reject_reason IS '驳回原因';
COMMENT ON COLUMN qms.supplier_material_change.attachments IS '附件 URL JSON';

-- 2. 联合审批表（质量/采购/研发 并行会签）
CREATE SEQUENCE qms.supplier_material_change_approval_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_material_change_approval (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_material_change_approval_id_seq'::regclass),
    change_id bigint NOT NULL,
    approval_role character varying(16) NOT NULL,
    approver character varying(64),
    approver_id bigint,
    approval_status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    opinion text,
    approved_at timestamp without time zone,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY qms.supplier_material_change_approval ADD CONSTRAINT supplier_material_change_approval_pkey PRIMARY KEY (id);
COMMENT ON TABLE qms.supplier_material_change_approval IS '供应商物料变更联合审批表（质量/采购/研发 并行会签 + 一票否决）';
COMMENT ON COLUMN qms.supplier_material_change_approval.change_id IS '变更单ID';
COMMENT ON COLUMN qms.supplier_material_change_approval.approval_role IS '审批角色：QUALITY 质量 / PURCHASE 采购 / RD 研发';
COMMENT ON COLUMN qms.supplier_material_change_approval.approver IS '审批人姓名';
COMMENT ON COLUMN qms.supplier_material_change_approval.approver_id IS '审批人用户ID';
COMMENT ON COLUMN qms.supplier_material_change_approval.approval_status IS '审批状态：PENDING / APPROVED / REJECTED / CANCELLED';
COMMENT ON COLUMN qms.supplier_material_change_approval.opinion IS '审批意见';
COMMENT ON COLUMN qms.supplier_material_change_approval.approved_at IS '审批时间';

-- 索引（性能与隔离）
CREATE INDEX idx_smc_status ON qms.supplier_material_change (status, plant_code);
CREATE INDEX idx_smc_supplier ON qms.supplier_material_change (supplier_code, plant_code);
CREATE INDEX idx_smc_material ON qms.supplier_material_change (material_code, plant_code);
CREATE INDEX idx_smc_standard ON qms.supplier_material_change (standard_id);
CREATE INDEX idx_smca_change ON qms.supplier_material_change_approval (change_id, plant_code);
CREATE INDEX idx_smca_role ON qms.supplier_material_change_approval (approval_role, approval_status);
