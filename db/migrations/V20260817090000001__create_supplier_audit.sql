-- 供应商现场审核管理模块：建表（计划 / 记录 / 不符合项 / 整改）
-- 仅结构变更 + 标准扩展列，遵守 Migration Iron Law（不含 DELETE/UPDATE/DROP）。

-- 1. 审核计划
CREATE SEQUENCE qms.supplier_audit_plan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_audit_plan (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_plan_id_seq'::regclass),
    supplier_id bigint NOT NULL,
    supplier_code character varying(64) NOT NULL,
    supplier_name character varying(128) NOT NULL,
    audit_type character varying(16) NOT NULL,
    plan_year integer,
    frequency character varying(16) NOT NULL,
    planned_date date,
    status character varying(16) DEFAULT '草稿'::character varying NOT NULL,
    auditor character varying(64),
    remark text,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY qms.supplier_audit_plan ADD CONSTRAINT supplier_audit_plan_pkey PRIMARY KEY (id);
COMMENT ON TABLE qms.supplier_audit_plan IS '供应商现场审核计划（年度/专项/临时，按风险等级分配频次）';
COMMENT ON COLUMN qms.supplier_audit_plan.audit_type IS '审核类型：年度/专项/临时';
COMMENT ON COLUMN qms.supplier_audit_plan.frequency IS '审核频次：如 一年2次/一年1次/两年1次';
COMMENT ON COLUMN qms.supplier_audit_plan.status IS '状态：草稿/已排期/已完成';

-- 2. 审核记录（一次现场审核）
CREATE SEQUENCE qms.supplier_audit_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_audit_record (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_record_id_seq'::regclass),
    plan_id bigint,
    supplier_id bigint NOT NULL,
    supplier_code character varying(64) NOT NULL,
    supplier_name character varying(128) NOT NULL,
    report_no character varying(64),
    audit_date date NOT NULL,
    auditor character varying(64),
    audit_summary text,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY qms.supplier_audit_record ADD CONSTRAINT supplier_audit_record_pkey PRIMARY KEY (id);
COMMENT ON TABLE qms.supplier_audit_record IS '供应商现场审核记录（一次现场审核的执行数据）';
COMMENT ON COLUMN qms.supplier_audit_record.report_no IS '审核报告编号（格式 SA-年月日-序号）';

-- 3. 不符合项
CREATE SEQUENCE qms.supplier_audit_finding_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_audit_finding (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_finding_id_seq'::regclass),
    record_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    supplier_code character varying(64) NOT NULL,
    level character varying(16) NOT NULL,
    description text NOT NULL,
    photo_urls text,
    status character varying(16) DEFAULT '待整改'::character varying NOT NULL,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY qms.supplier_audit_finding ADD CONSTRAINT supplier_audit_finding_pkey PRIMARY KEY (id);
COMMENT ON TABLE qms.supplier_audit_finding IS '供应商现场审核不符合项（严重/一般/观察项）';
COMMENT ON COLUMN qms.supplier_audit_finding.level IS '不符合项级别：严重/一般/观察项';
COMMENT ON COLUMN qms.supplier_audit_finding.photo_urls IS '现场照片相对 URL，逗号分隔';
COMMENT ON COLUMN qms.supplier_audit_finding.status IS '状态：待整改/整改中/待验证/已闭环';

-- 4. 整改跟踪
CREATE SEQUENCE qms.supplier_audit_rectification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_audit_rectification (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_audit_rectification_id_seq'::regclass),
    finding_id bigint NOT NULL,
    measure text,
    due_date date,
    owner character varying(64),
    verify_result character varying(16),
    verified_by character varying(64),
    closed_at timestamp without time zone,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY qms.supplier_audit_rectification ADD CONSTRAINT supplier_audit_rectification_pkey PRIMARY KEY (id);
COMMENT ON TABLE qms.supplier_audit_rectification IS '供应商现场审核不符合项整改跟踪（措施+验证+闭环）';
COMMENT ON COLUMN qms.supplier_audit_rectification.verify_result IS '验证结果：通过/不通过';
COMMENT ON COLUMN qms.supplier_audit_rectification.closed_at IS '闭环时间';

-- 索引（性能与隔离）
CREATE INDEX idx_supplier_audit_plan_supplier ON qms.supplier_audit_plan (supplier_id, plant_code);
CREATE INDEX idx_supplier_audit_plan_status ON qms.supplier_audit_plan (status, plant_code);
CREATE INDEX idx_supplier_audit_record_plan ON qms.supplier_audit_record (plan_id, plant_code);
CREATE INDEX idx_supplier_audit_record_supplier ON qms.supplier_audit_record (supplier_id, plant_code);
CREATE INDEX idx_supplier_audit_finding_record ON qms.supplier_audit_finding (record_id, plant_code);
CREATE INDEX idx_supplier_audit_finding_status ON qms.supplier_audit_finding (status, plant_code);
CREATE INDEX idx_supplier_audit_rectification_finding ON qms.supplier_audit_rectification (finding_id, plant_code);
