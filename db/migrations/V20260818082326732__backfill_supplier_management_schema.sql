-- 补齐因历史 Flyway 版本冲突被跳过的供应商管理表结构。
-- 所有 DDL 均可重复执行，供已有环境安全升级。

CREATE SEQUENCE IF NOT EXISTS qms.supplier_audit_plan_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_audit_plan (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_plan_id_seq'::regclass), supplier_id bigint NOT NULL,
    supplier_code varchar(64) NOT NULL, supplier_name varchar(128) NOT NULL, audit_type varchar(16) NOT NULL,
    plan_year integer, frequency varchar(16) NOT NULL, planned_date date, status varchar(16) NOT NULL DEFAULT '草稿',
    auditor varchar(64), remark text, plant_code varchar(8) NOT NULL, plant_name varchar(32) NOT NULL,
    created_by varchar(64), updated_by varchar(64), is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(), updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT supplier_audit_plan_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS qms.supplier_audit_record_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_audit_record (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_record_id_seq'::regclass), plan_id bigint, supplier_id bigint NOT NULL,
    supplier_code varchar(64) NOT NULL, supplier_name varchar(128) NOT NULL, report_no varchar(64), audit_date date NOT NULL,
    auditor varchar(64), audit_summary text, extra_requirement text, plant_code varchar(8) NOT NULL, plant_name varchar(32) NOT NULL,
    created_by varchar(64), updated_by varchar(64), is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(), updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT supplier_audit_record_pkey PRIMARY KEY (id)
);
ALTER TABLE qms.supplier_audit_record ADD COLUMN IF NOT EXISTS extra_requirement text;

CREATE SEQUENCE IF NOT EXISTS qms.supplier_audit_finding_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_audit_finding (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_finding_id_seq'::regclass), record_id bigint NOT NULL, supplier_id bigint NOT NULL,
    supplier_code varchar(64) NOT NULL, level varchar(16) NOT NULL, description text NOT NULL, photo_urls text,
    status varchar(16) NOT NULL DEFAULT '待整改', plant_code varchar(8) NOT NULL, plant_name varchar(32) NOT NULL,
    created_by varchar(64), updated_by varchar(64), is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(), updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT supplier_audit_finding_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS qms.supplier_audit_rectification_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_audit_rectification (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_rectification_id_seq'::regclass), finding_id bigint NOT NULL,
    measure text, due_date date, owner varchar(64), verify_result varchar(16), verified_by varchar(64), closed_at timestamp without time zone,
    plant_code varchar(8) NOT NULL, plant_name varchar(32) NOT NULL, created_by varchar(64), updated_by varchar(64),
    is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(), updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT supplier_audit_rectification_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS qms.supplier_qualification_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_qualification (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_qualification_id_seq'::regclass), supplier_id bigint NOT NULL,
    supplier_code varchar(64), supplier_name varchar(128), cert_type varchar(64) NOT NULL, cert_no varchar(128), issuer varchar(128),
    issue_date date, expire_date date, long_term smallint NOT NULL DEFAULT 0, file_urls text, remark text,
    plant_code varchar(16), plant_name varchar(32), created_by varchar(64), updated_by varchar(64),
    is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(), updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT supplier_qualification_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS qms.high_risk_material (
    id bigserial PRIMARY KEY, material_code varchar(64) NOT NULL, material_name varchar(128) NOT NULL,
    risk_level varchar(16) NOT NULL DEFAULT '高', extra_requirement text, remark text
);
CREATE TABLE IF NOT EXISTS qms.supplier_high_risk_material (
    id bigserial PRIMARY KEY, supplier_id bigint NOT NULL, supplier_code varchar(64), supplier_name varchar(128), material_id bigint NOT NULL,
    material_code varchar(64), material_name varchar(128), extra_requirement text, plant_code varchar(16), plant_name varchar(32),
    created_by varchar(64), is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);

CREATE SEQUENCE IF NOT EXISTS qms.supplier_material_change_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_material_change (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_material_change_id_seq'::regclass), change_no varchar(64) NOT NULL,
    applicant_id bigint, applicant varchar(64), supplier_code varchar(64) NOT NULL, supplier_name varchar(128) NOT NULL,
    material_code varchar(64) NOT NULL, material_name varchar(128) NOT NULL, change_type varchar(16) NOT NULL,
    change_desc text, validation_report text, risk_assessment text, standard_id bigint, tightened_subgroup_size integer,
    spc_enabled smallint NOT NULL DEFAULT 0, status varchar(16) NOT NULL DEFAULT 'DRAFT', reject_reason text, attachments text,
    plant_code varchar(8) NOT NULL, plant_name varchar(32) NOT NULL, created_by varchar(64), updated_by varchar(64),
    is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(), updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT supplier_material_change_pkey PRIMARY KEY (id), CONSTRAINT uq_smc_change_no UNIQUE (change_no)
);

CREATE SEQUENCE IF NOT EXISTS qms.supplier_material_change_approval_id_seq;
CREATE TABLE IF NOT EXISTS qms.supplier_material_change_approval (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_material_change_approval_id_seq'::regclass), change_id bigint NOT NULL,
    approval_role varchar(16) NOT NULL, approver varchar(64), approver_id bigint, approval_status varchar(16) NOT NULL DEFAULT 'PENDING',
    opinion text, approved_at timestamp without time zone, plant_code varchar(8) NOT NULL, plant_name varchar(32) NOT NULL,
    created_by varchar(64), updated_by varchar(64), is_deleted smallint NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(), updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT supplier_material_change_approval_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_audit_plan_supplier ON qms.supplier_audit_plan (supplier_id, plant_code);
CREATE INDEX IF NOT EXISTS idx_supplier_audit_record_supplier ON qms.supplier_audit_record (supplier_id, plant_code);
CREATE INDEX IF NOT EXISTS idx_supplier_audit_finding_record ON qms.supplier_audit_finding (record_id, plant_code);
CREATE INDEX IF NOT EXISTS idx_supplier_audit_rectification_finding ON qms.supplier_audit_rectification (finding_id, plant_code);
CREATE INDEX IF NOT EXISTS idx_supplier_qualification_supplier ON qms.supplier_qualification (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_qualification_expire ON qms.supplier_qualification (expire_date);
CREATE INDEX IF NOT EXISTS idx_high_risk_material_code ON qms.high_risk_material (material_code);
CREATE INDEX IF NOT EXISTS idx_supplier_high_risk_supplier ON qms.supplier_high_risk_material (supplier_id);
CREATE INDEX IF NOT EXISTS idx_smc_status ON qms.supplier_material_change (status, plant_code);
CREATE INDEX IF NOT EXISTS idx_smc_supplier ON qms.supplier_material_change (supplier_code, plant_code);
CREATE INDEX IF NOT EXISTS idx_smc_material ON qms.supplier_material_change (material_code, plant_code);
CREATE INDEX IF NOT EXISTS idx_smca_change ON qms.supplier_material_change_approval (change_id, plant_code);
CREATE INDEX IF NOT EXISTS idx_smca_role ON qms.supplier_material_change_approval (approval_role, approval_status);
