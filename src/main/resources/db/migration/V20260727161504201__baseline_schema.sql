-- Flyway baseline: complete QMS schema.
-- Generated from db/ddl/00-qms-schema.sql.

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: qms; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS qms;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_log; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.audit_log (
    id bigint NOT NULL,
    table_name character varying(64) NOT NULL,
    record_id bigint NOT NULL,
    operation_type character varying(64) NOT NULL,
    before_data text,
    after_data text,
    operator_id bigint,
    operator_name character varying(64),
    plant_code character varying(8) NOT NULL,
    ip_address character varying(64),
    operation_time timestamp without time zone DEFAULT now() NOT NULL,
    reason character varying(256),
    created_by character varying(64) DEFAULT 'AUDIT_SYSTEM'::character varying,
    updated_by character varying(64) DEFAULT NULL::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE audit_log; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.audit_log IS '独立审计日志表（M0：记录所有业务表 CUD 操作，满足 GMP 审计追溯要求）';


--
-- Name: COLUMN audit_log.table_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.table_name IS '被操作的表名，如 material_inspection';


--
-- Name: COLUMN audit_log.record_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.record_id IS '被操作的行记录主键ID';


--
-- Name: COLUMN audit_log.operation_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.operation_type IS '操作类型：CREATE=新增 UPDATE=修改 DELETE=逻辑删除';


--
-- Name: COLUMN audit_log.before_data; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.before_data IS '变更前数据快照（JSONB），UPDATE/DELETE 时有值，包含完整行数据';


--
-- Name: COLUMN audit_log.after_data; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.after_data IS '变更后数据快照（JSONB），CREATE/UPDATE 时有值，包含完整行数据';


--
-- Name: COLUMN audit_log.operator_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.operator_id IS '操作人ID（关联 sys_user.id）';


--
-- Name: COLUMN audit_log.operator_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.operator_name IS '操作人姓名（冗余字段，避免联表查询）';


--
-- Name: COLUMN audit_log.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.plant_code IS '分公司编码 SZ=深圳 MZ=梅州';


--
-- Name: COLUMN audit_log.ip_address; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.ip_address IS '操作发起IP地址';


--
-- Name: COLUMN audit_log.operation_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.operation_time IS '操作执行时间';


--
-- Name: COLUMN audit_log.reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.reason IS '操作原因或备注说明';


--
-- Name: COLUMN audit_log.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.audit_log.created_by IS '创建来源（审计系统自动记录 AUDIT_SYSTEM）';


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.audit_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: escalation; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.escalation (
    id bigint NOT NULL,
    supplier_id bigint,
    escalation_reason character varying(256) NOT NULL,
    related_exception_ids character varying(512),
    escalation_action character varying(64) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    closed_at timestamp without time zone,
    remark text,
    signature_user character varying(64),
    signature_time timestamp without time zone,
    signature_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    supplier_code character varying(64),
    supplier_name character varying(128),
    material_code character varying(64),
    review_opinion text,
    reviewed_by character varying(64),
    reviewed_at timestamp without time zone,
    process_stage character varying(32) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    action_plan text,
    owner_name character varying(64),
    due_date date,
    execution_record text,
    executed_by character varying(64),
    executed_at timestamp without time zone,
    verification_result character varying(16),
    verification_evidence text,
    verified_by character varying(64),
    verified_at timestamp without time zone,
    close_reason text,
    closed_by character varying(64)
);


--
-- Name: TABLE escalation; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.escalation IS '供应商升级记录表（M2：高频问题供应商自动升级，触发加密审核/暂停供货/专项CAPA）';


--
-- Name: COLUMN escalation.supplier_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.supplier_id IS '供应商ID（外键 supplier.id）';


--
-- Name: COLUMN escalation.escalation_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.escalation_reason IS '升级原因（如：90天内同类不良≥3次、严重不良重复发生等）';


--
-- Name: COLUMN escalation.related_exception_ids; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.related_exception_ids IS '关联异常单ID列表（逗号分隔，用于追溯升级触发依据）';


--
-- Name: COLUMN escalation.escalation_action; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.escalation_action IS '升级动作：加密审核=增加审核频次 暂停供货=暂停供应商供货资质 专项CAPA=发起专项CAPA整改';


--
-- Name: COLUMN escalation.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.status IS '状态：ACTIVE=生效中 CLOSED=已关闭（保留英文代码）';


--
-- Name: COLUMN escalation.closed_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.closed_at IS '关闭时间（status=CLOSED时写入）';


--
-- Name: COLUMN escalation.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.remark IS '备注';


--
-- Name: COLUMN escalation.signature_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.signature_user IS '电子签名人';


--
-- Name: COLUMN escalation.signature_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.signature_time IS '电子签名时间';


--
-- Name: COLUMN escalation.signature_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.signature_reason IS '电子签名原因';


--
-- Name: COLUMN escalation.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN escalation.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.plant_name IS '分公司名称';


--
-- Name: COLUMN escalation.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.created_by IS '创建人';


--
-- Name: COLUMN escalation.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.updated_by IS '更新人';


--
-- Name: COLUMN escalation.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN escalation.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.version IS '乐观锁版本号';


--
-- Name: COLUMN escalation.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.created_at IS '创建时间';


--
-- Name: COLUMN escalation.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.updated_at IS '更新时间';


--
-- Name: COLUMN escalation.supplier_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.supplier_code IS '触发升级的供应商编号，来自来料检验入库审核表';


--
-- Name: COLUMN escalation.supplier_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.supplier_name IS '触发升级的供应商名称，来自来料检验入库审核表';


--
-- Name: COLUMN escalation.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.material_code IS '重复不合格物料代码';


--
-- Name: COLUMN escalation.review_opinion; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.review_opinion IS '升级审核意见';


--
-- Name: COLUMN escalation.reviewed_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.reviewed_by IS '升级审核人';


--
-- Name: COLUMN escalation.reviewed_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.escalation.reviewed_at IS '升级审核时间';


--
-- Name: escalation_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.escalation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.escalation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: exception_8d; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.exception_8d (
    id bigint NOT NULL,
    exception_id bigint NOT NULL,
    current_step character varying(4) DEFAULT 'D1'::character varying NOT NULL,
    d1_team text,
    d2_problem_desc text,
    d3_containment text,
    d4_root_cause text,
    d5_corrective text,
    d6_implementation text,
    d7_preventive text,
    d8_closure text,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE exception_8d; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.exception_8d IS '8D/CAPA 报告（M2：D1-D8 步骤，1 个异常单对应 1 份 8D 报告）';


--
-- Name: COLUMN exception_8d.exception_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.exception_id IS '关联异常单ID（外键 exception_order.id）';


--
-- Name: COLUMN exception_8d.current_step; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.current_step IS '当前步骤：D1-D8';


--
-- Name: COLUMN exception_8d.d1_team; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d1_team IS 'D1 团队成立（成员/负责人）';


--
-- Name: COLUMN exception_8d.d2_problem_desc; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d2_problem_desc IS 'D2 问题描述（5W2H）';


--
-- Name: COLUMN exception_8d.d3_containment; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d3_containment IS 'D3 临时遏制措施';


--
-- Name: COLUMN exception_8d.d4_root_cause; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d4_root_cause IS 'D4 根本原因分析';


--
-- Name: COLUMN exception_8d.d5_corrective; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d5_corrective IS 'D5 纠正措施';


--
-- Name: COLUMN exception_8d.d6_implementation; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d6_implementation IS 'D6 实施与验证';


--
-- Name: COLUMN exception_8d.d7_preventive; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d7_preventive IS 'D7 预防措施';


--
-- Name: COLUMN exception_8d.d8_closure; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.d8_closure IS 'D8 团队表彰/闭环总结';


--
-- Name: COLUMN exception_8d.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN exception_8d.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.plant_name IS '分公司名称';


--
-- Name: COLUMN exception_8d.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.created_by IS '创建人';


--
-- Name: COLUMN exception_8d.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.updated_by IS '更新人';


--
-- Name: COLUMN exception_8d.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN exception_8d.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.version IS '乐观锁版本号';


--
-- Name: COLUMN exception_8d.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.created_at IS '创建时间';


--
-- Name: COLUMN exception_8d.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_8d.updated_at IS '更新时间';


--
-- Name: exception_8d_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.exception_8d ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.exception_8d_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: exception_order; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.exception_order (
    id bigint NOT NULL,
    exception_no character varying(64) NOT NULL,
    source_type character varying(32) NOT NULL,
    source_id bigint,
    severity character varying(8) NOT NULL,
    status character varying(16) DEFAULT '待整改'::character varying NOT NULL,
    supplier_id bigint,
    work_order_id bigint,
    material_code character varying(64),
    defect_desc text NOT NULL,
    defect_qty numeric(18,3),
    total_qty numeric(18,3),
    handler_id bigint,
    deadline date,
    closed_at timestamp without time zone,
    remark text,
    signature_user character varying(64),
    signature_time timestamp without time zone,
    signature_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    reviewer_id bigint,
    capa_status character varying(20) DEFAULT '待发起'::character varying,
    process_type character varying(8),
    rule_reason text,
    notification_level character varying(16),
    response_deadline timestamp without time zone,
    repeat_count_30_days integer DEFAULT 1 NOT NULL,
    repeat_count_90_days integer DEFAULT 1 NOT NULL,
    problem_fingerprint character varying(256),
    handling_method character varying(32)
);


--
-- Name: TABLE exception_order; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.exception_order IS '异常单（M2：来料/制程/审核等来源异常的整改工单，驱动8D/CAPA流程）';


--
-- Name: COLUMN exception_order.exception_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.exception_no IS '异常单号（唯一，格式 EX-年月日-序号）';


--
-- Name: COLUMN exception_order.source_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.source_type IS '来源类型：来料不良/制程不良/审核问题/客户投诉/重复问题';


--
-- Name: COLUMN exception_order.source_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.source_id IS '来源记录ID（关联对应业务表的主键ID）';


--
-- Name: COLUMN exception_order.severity; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.severity IS '严重等级：严重/一般';


--
-- Name: COLUMN exception_order.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.status IS '状态：待整改/整改中/待验证/已闭环';


--
-- Name: COLUMN exception_order.supplier_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.supplier_id IS '供应商ID（关联 supplier.id）';


--
-- Name: COLUMN exception_order.work_order_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.work_order_id IS '关联工单ID';


--
-- Name: COLUMN exception_order.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.material_code IS '物料代码';


--
-- Name: COLUMN exception_order.defect_desc; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.defect_desc IS '不良描述（自由文本，前端M2多维分析按此字段 GROUP BY 聚合展示）';


--
-- Name: COLUMN exception_order.defect_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.defect_qty IS '不良数量';


--
-- Name: COLUMN exception_order.total_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.total_qty IS '总数量';


--
-- Name: COLUMN exception_order.handler_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.handler_id IS '处理人ID（关联 sys_user.id）';


--
-- Name: COLUMN exception_order.deadline; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.deadline IS '整改截止日期';


--
-- Name: COLUMN exception_order.closed_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.closed_at IS '闭环时间（status=已闭环时写入）';


--
-- Name: COLUMN exception_order.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.remark IS '备注';


--
-- Name: COLUMN exception_order.signature_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.signature_user IS '电子签名人';


--
-- Name: COLUMN exception_order.signature_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.signature_time IS '电子签名时间';


--
-- Name: COLUMN exception_order.signature_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.signature_reason IS '电子签名原因';


--
-- Name: COLUMN exception_order.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN exception_order.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.plant_name IS '分公司名称';


--
-- Name: COLUMN exception_order.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.created_by IS '创建人';


--
-- Name: COLUMN exception_order.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.updated_by IS '更新人';


--
-- Name: COLUMN exception_order.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN exception_order.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.version IS '乐观锁版本号';


--
-- Name: COLUMN exception_order.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.created_at IS '创建时间';


--
-- Name: COLUMN exception_order.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.updated_at IS '更新时间';


--
-- Name: COLUMN exception_order.reviewer_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.reviewer_id IS '审核人/复核人ID（关联 sys_user.id）';


--
-- Name: COLUMN exception_order.capa_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.capa_status IS '8D/CAPA状态：待发起/进行中/已完成';


--
-- Name: COLUMN exception_order.process_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.process_type IS '整改流程类型：CAPA(改善措施+验证) / 8D(八步报告) / BOTH(两者) / NULL(待选择)';


--
-- Name: COLUMN exception_order.rule_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.rule_reason IS '自动分级命中原因，仅根据来料检验入库审核表计算';


--
-- Name: COLUMN exception_order.notification_level; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.notification_level IS '通知等级：严重/提醒';


--
-- Name: COLUMN exception_order.response_deadline; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.response_deadline IS '首次响应截止时间';


--
-- Name: COLUMN exception_order.repeat_count_30_days; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.repeat_count_30_days IS '同分公司供应商+物料近30天不合格批次';


--
-- Name: COLUMN exception_order.repeat_count_90_days; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.repeat_count_90_days IS '同分公司供应商+物料近90天不合格批次';


--
-- Name: COLUMN exception_order.problem_fingerprint; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.problem_fingerprint IS '重复问题键：plantCode|supplierCode|materialCode';


--
-- Name: COLUMN exception_order.handling_method; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.exception_order.handling_method IS '来源来料检验记录的处理方式';


--
-- Name: exception_order_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.exception_order ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.exception_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fai_change_trigger; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.fai_change_trigger (
    id bigint NOT NULL,
    trigger_type character varying(16) NOT NULL,
    work_order_no character varying(64),
    material_code character varying(64),
    material_name character varying(128),
    batch_no character varying(64),
    process_name character varying(64),
    trigger_reason character varying(512),
    status character varying(16) DEFAULT '待检验'::character varying NOT NULL,
    remark character varying(512),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    process_code character varying(32)
);


--
-- Name: TABLE fai_change_trigger; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.fai_change_trigger IS '首件检验变更触发记录（M3：换模具/升级系统/换批次/换设备/材料批次等触发首件检验）';


--
-- Name: COLUMN fai_change_trigger.trigger_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.trigger_type IS '变更类型：换模具/升级系统/换批次/换设备/材料批次';


--
-- Name: COLUMN fai_change_trigger.work_order_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.work_order_no IS '工单号';


--
-- Name: COLUMN fai_change_trigger.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.material_code IS '物料代码';


--
-- Name: COLUMN fai_change_trigger.material_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.material_name IS '物料名称';


--
-- Name: COLUMN fai_change_trigger.batch_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.batch_no IS '批次号';


--
-- Name: COLUMN fai_change_trigger.process_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.process_name IS '工序：装配/焊接/检测（红线固定，禁止新增）';


--
-- Name: COLUMN fai_change_trigger.trigger_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.trigger_reason IS '触发原因';


--
-- Name: COLUMN fai_change_trigger.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.status IS '状态：待检验/已检验/关闭';


--
-- Name: COLUMN fai_change_trigger.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.remark IS '备注';


--
-- Name: COLUMN fai_change_trigger.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN fai_change_trigger.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.plant_name IS '分公司名称';


--
-- Name: COLUMN fai_change_trigger.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.created_by IS '创建人';


--
-- Name: COLUMN fai_change_trigger.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.updated_by IS '更新人';


--
-- Name: COLUMN fai_change_trigger.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN fai_change_trigger.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.version IS '乐观锁版本号';


--
-- Name: COLUMN fai_change_trigger.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.created_at IS '创建时间';


--
-- Name: COLUMN fai_change_trigger.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_change_trigger.updated_at IS '更新时间';


--
-- Name: fai_change_trigger_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.fai_change_trigger ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.fai_change_trigger_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fai_inspection_item; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.fai_inspection_item (
    id bigint NOT NULL,
    fai_record_id bigint NOT NULL,
    param_name character varying(64),
    param_code character varying(32),
    standard_value character varying(32),
    upper_limit numeric(18,6),
    lower_limit numeric(18,6),
    actual_value numeric(18,6),
    unit character varying(16),
    result character varying(16) DEFAULT '未检'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    param_category character varying(32),
    standard_item_id bigint,
    spc_enabled character varying(8) DEFAULT '否'::character varying NOT NULL,
    spc_parameter_id bigint
);


--
-- Name: TABLE fai_inspection_item; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.fai_inspection_item IS '首件检验参数明细（M3：从标准模板复制，actual_value 录入后自动判定）';


--
-- Name: COLUMN fai_inspection_item.fai_record_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.fai_record_id IS '关联首件检验主记录 fai_inspection_record.id';


--
-- Name: COLUMN fai_inspection_item.param_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.param_name IS '参数名称';


--
-- Name: COLUMN fai_inspection_item.param_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.param_code IS '参数编码（SPC 联动分组依据）';


--
-- Name: COLUMN fai_inspection_item.standard_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.standard_value IS '标准值（上下限均为空时做精确匹配，容差 ±0.0001）';


--
-- Name: COLUMN fai_inspection_item.upper_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.upper_limit IS '上限';


--
-- Name: COLUMN fai_inspection_item.lower_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.lower_limit IS '下限';


--
-- Name: COLUMN fai_inspection_item.actual_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.actual_value IS '实际值（录入后用于判定）';


--
-- Name: COLUMN fai_inspection_item.unit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.unit IS '单位';


--
-- Name: COLUMN fai_inspection_item.result; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.result IS '判定：未检/合格/不合格';


--
-- Name: COLUMN fai_inspection_item.sort_order; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.sort_order IS '排序';


--
-- Name: COLUMN fai_inspection_item.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN fai_inspection_item.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.plant_name IS '分公司名称';


--
-- Name: COLUMN fai_inspection_item.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.created_by IS '创建人';


--
-- Name: COLUMN fai_inspection_item.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.updated_by IS '更新人';


--
-- Name: COLUMN fai_inspection_item.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN fai_inspection_item.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.version IS '乐观锁版本号';


--
-- Name: COLUMN fai_inspection_item.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.created_at IS '创建时间';


--
-- Name: COLUMN fai_inspection_item.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.updated_at IS '更新时间';


--
-- Name: COLUMN fai_inspection_item.param_category; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.param_category IS '参数类别：AQL/关键尺寸/性能参数（复制自标准模板）';


--
-- Name: COLUMN fai_inspection_item.standard_item_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_item.standard_item_id IS 'FK to fai_inspection_standard_item.id for standard sync';


--
-- Name: fai_inspection_item_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.fai_inspection_item ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.fai_inspection_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fai_inspection_record; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.fai_inspection_record (
    id bigint NOT NULL,
    fai_no character varying(64) NOT NULL,
    change_trigger_id bigint,
    material_code character varying(64),
    material_name character varying(128),
    batch_no character varying(64),
    process_name character varying(64),
    work_order_no character varying(64),
    inspection_result character varying(16) DEFAULT '待判定'::character varying NOT NULL,
    signature_status character varying(16) DEFAULT '未签'::character varying NOT NULL,
    remark character varying(512),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    process_code character varying(32)
);


--
-- Name: TABLE fai_inspection_record; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.fai_inspection_record IS '首件检验主记录（M3：从变更触发创建，含判定结果与电子签名状态）';


--
-- Name: COLUMN fai_inspection_record.fai_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.fai_no IS '首件编号（业务唯一，规则 FAI-{plant_code}-{yyyyMMdd}-{4位流水}）';


--
-- Name: COLUMN fai_inspection_record.change_trigger_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.change_trigger_id IS '关联变更触发 fai_change_trigger.id';


--
-- Name: COLUMN fai_inspection_record.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.material_code IS '物料代码（从变更触发复制）';


--
-- Name: COLUMN fai_inspection_record.material_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.material_name IS '物料名称（从变更触发复制）';


--
-- Name: COLUMN fai_inspection_record.batch_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.batch_no IS '批次号（从变更触发复制）';


--
-- Name: COLUMN fai_inspection_record.process_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.process_name IS '工序：装配/焊接/检测';


--
-- Name: COLUMN fai_inspection_record.work_order_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.work_order_no IS '工单号';


--
-- Name: COLUMN fai_inspection_record.inspection_result; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.inspection_result IS '判定结果：待判定/合格/不合格';


--
-- Name: COLUMN fai_inspection_record.signature_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.signature_status IS '电子签名状态：未签/已签';


--
-- Name: COLUMN fai_inspection_record.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.remark IS '备注';


--
-- Name: COLUMN fai_inspection_record.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN fai_inspection_record.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.plant_name IS '分公司名称';


--
-- Name: COLUMN fai_inspection_record.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.created_by IS '创建人';


--
-- Name: COLUMN fai_inspection_record.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.updated_by IS '更新人';


--
-- Name: COLUMN fai_inspection_record.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN fai_inspection_record.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.version IS '乐观锁版本号';


--
-- Name: COLUMN fai_inspection_record.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.created_at IS '创建时间';


--
-- Name: COLUMN fai_inspection_record.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_record.updated_at IS '更新时间';


--
-- Name: fai_inspection_record_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.fai_inspection_record ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.fai_inspection_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fai_inspection_standard; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.fai_inspection_standard (
    id bigint NOT NULL,
    material_code character varying(64) NOT NULL,
    process_name character varying(64) NOT NULL,
    std_version integer DEFAULT 1 NOT NULL,
    is_active character varying(8) DEFAULT '否'::character varying NOT NULL,
    remark character varying(512),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    material_name character varying(128),
    process_code character varying(32)
);


--
-- Name: TABLE fai_inspection_standard; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.fai_inspection_standard IS '首件检验标准模板主表（M3：按 物料+工序 维护，最新激活版本用于自动建单）';


--
-- Name: COLUMN fai_inspection_standard.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.material_code IS '物料代码';


--
-- Name: COLUMN fai_inspection_standard.process_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.process_name IS '工序：装配/焊接/检测';


--
-- Name: COLUMN fai_inspection_standard.std_version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.std_version IS '模板版本号（同一 物料+工序 可有多个版本）';


--
-- Name: COLUMN fai_inspection_standard.is_active; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.is_active IS '是否激活：是/否（仅一个激活版本用于建单）';


--
-- Name: COLUMN fai_inspection_standard.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.remark IS '备注';


--
-- Name: COLUMN fai_inspection_standard.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN fai_inspection_standard.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.plant_name IS '分公司名称';


--
-- Name: COLUMN fai_inspection_standard.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.created_by IS '创建人';


--
-- Name: COLUMN fai_inspection_standard.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.updated_by IS '更新人';


--
-- Name: COLUMN fai_inspection_standard.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN fai_inspection_standard.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.version IS '乐观锁版本号';


--
-- Name: COLUMN fai_inspection_standard.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.created_at IS '创建时间';


--
-- Name: COLUMN fai_inspection_standard.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.updated_at IS '更新时间';


--
-- Name: COLUMN fai_inspection_standard.material_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard.material_name IS '物料名称（冗余，便于展示）';


--
-- Name: fai_inspection_standard_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.fai_inspection_standard ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.fai_inspection_standard_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fai_inspection_standard_item; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.fai_inspection_standard_item (
    id bigint NOT NULL,
    standard_id bigint NOT NULL,
    param_name character varying(64),
    param_code character varying(32),
    standard_value character varying(32),
    upper_limit numeric(18,6),
    lower_limit numeric(18,6),
    unit character varying(16),
    is_required character varying(8) DEFAULT '是'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    param_category character varying(32) DEFAULT '关键尺寸'::character varying NOT NULL,
    spc_enabled character varying(8) DEFAULT '否'::character varying NOT NULL,
    spc_parameter_id bigint
);


--
-- Name: TABLE fai_inspection_standard_item; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.fai_inspection_standard_item IS '首件检验标准模板参数项（M3：标准模板的参数清单，建单时复制到明细表）';


--
-- Name: COLUMN fai_inspection_standard_item.standard_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.standard_id IS '关联标准模板主表 fai_inspection_standard.id';


--
-- Name: COLUMN fai_inspection_standard_item.param_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.param_name IS '参数名称';


--
-- Name: COLUMN fai_inspection_standard_item.param_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.param_code IS '参数编码';


--
-- Name: COLUMN fai_inspection_standard_item.standard_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.standard_value IS '标准值';


--
-- Name: COLUMN fai_inspection_standard_item.upper_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.upper_limit IS '上限';


--
-- Name: COLUMN fai_inspection_standard_item.lower_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.lower_limit IS '下限';


--
-- Name: COLUMN fai_inspection_standard_item.unit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.unit IS '单位';


--
-- Name: COLUMN fai_inspection_standard_item.is_required; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.is_required IS '是否必检：是/否（必检项未填或不合格则整单不合格）';


--
-- Name: COLUMN fai_inspection_standard_item.sort_order; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.sort_order IS '排序';


--
-- Name: COLUMN fai_inspection_standard_item.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN fai_inspection_standard_item.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.plant_name IS '分公司名称';


--
-- Name: COLUMN fai_inspection_standard_item.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.created_by IS '创建人';


--
-- Name: COLUMN fai_inspection_standard_item.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.updated_by IS '更新人';


--
-- Name: COLUMN fai_inspection_standard_item.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN fai_inspection_standard_item.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.version IS '乐观锁版本号';


--
-- Name: COLUMN fai_inspection_standard_item.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.created_at IS '创建时间';


--
-- Name: COLUMN fai_inspection_standard_item.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.updated_at IS '更新时间';


--
-- Name: COLUMN fai_inspection_standard_item.param_category; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_inspection_standard_item.param_category IS '参数类别：AQL/关键尺寸/性能参数';


--
-- Name: fai_inspection_standard_item_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.fai_inspection_standard_item ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.fai_inspection_standard_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fai_signature; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.fai_signature (
    id bigint NOT NULL,
    fai_record_id bigint NOT NULL,
    signer_id character varying(32) NOT NULL,
    signer_name character varying(64),
    sign_type character varying(16) NOT NULL,
    signature_hash character varying(128),
    signed_at timestamp without time zone DEFAULT now() NOT NULL,
    sign_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE fai_signature; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.fai_signature IS '首件检验电子签名（M3：独立合规表，存 SHA-256 摘要，符合电子签名规范）';


--
-- Name: COLUMN fai_signature.fai_record_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.fai_record_id IS '关联首件检验主记录 fai_inspection_record.id';


--
-- Name: COLUMN fai_signature.signer_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.signer_id IS '签名人ID（登录用户）';


--
-- Name: COLUMN fai_signature.signer_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.signer_name IS '签名人姓名';


--
-- Name: COLUMN fai_signature.sign_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.sign_type IS '签名类型：检验签/审核签';


--
-- Name: COLUMN fai_signature.signature_hash; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.signature_hash IS 'SHA-256 哈希（=SHA256(fai_no|signer_id|signed_at|sign_reason)），仅存摘要';


--
-- Name: COLUMN fai_signature.signed_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.signed_at IS '签名时间';


--
-- Name: COLUMN fai_signature.sign_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.sign_reason IS '签名原因';


--
-- Name: COLUMN fai_signature.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN fai_signature.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.plant_name IS '分公司名称';


--
-- Name: COLUMN fai_signature.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.created_by IS '创建人';


--
-- Name: COLUMN fai_signature.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.updated_by IS '更新人';


--
-- Name: COLUMN fai_signature.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN fai_signature.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.version IS '乐观锁版本号';


--
-- Name: COLUMN fai_signature.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.created_at IS '创建时间';


--
-- Name: COLUMN fai_signature.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.fai_signature.updated_at IS '更新时间';


--
-- Name: fai_signature_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.fai_signature ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.fai_signature_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: finished_goods_inspection; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.finished_goods_inspection (
    id bigint NOT NULL,
    is_urgent character varying(8) DEFAULT '否'::character varying NOT NULL,
    qc_review character varying(16) DEFAULT '待审核'::character varying NOT NULL,
    mgr_approval character varying(16) DEFAULT '待审核'::character varying NOT NULL,
    is_valid character varying(8) DEFAULT '是'::character varying NOT NULL,
    inspection_result character varying(16),
    report_no character varying(64) NOT NULL,
    inspection_request_no character varying(64),
    production_order_no character varying(64),
    material_code character varying(64),
    product_name character varying(128),
    model_spec character varying(128),
    prod_batch_or_sn character varying(128),
    production_date date,
    expiry_date date,
    submitted_qty numeric(18,3),
    inspected_qty numeric(18,3),
    qualified_qty numeric(18,3),
    unqualified_qty numeric(18,3),
    unit character varying(16),
    inspector_name character varying(64),
    category character varying(64),
    qc_reviewer character varying(64),
    qc_review_time timestamp without time zone,
    mgr_representative character varying(64),
    mgr_approval_time timestamp without time zone,
    is_entrusted character varying(8) DEFAULT '否'::character varying NOT NULL,
    drug_reg_no character varying(64),
    perf_test_method character varying(128),
    perf_sample_batch_no character varying(128),
    signature_user character varying(64),
    signature_time timestamp without time zone,
    signature_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE finished_goods_inspection; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.finished_goods_inspection IS '成品入库检验审核表（M1：成品最终检验，含品管审核+管代批准双签流程，production_order_no/prod_batch_or_sn 为追溯终点）';


--
-- Name: COLUMN finished_goods_inspection.is_urgent; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.is_urgent IS '是否加急：是/否';


--
-- Name: COLUMN finished_goods_inspection.qc_review; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.qc_review IS '品管审核：待审核/已审核/驳回';


--
-- Name: COLUMN finished_goods_inspection.mgr_approval; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.mgr_approval IS '管代批准：待审核/已审核/驳回';


--
-- Name: COLUMN finished_goods_inspection.is_valid; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.is_valid IS '是否有效：是/否';


--
-- Name: COLUMN finished_goods_inspection.inspection_result; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.inspection_result IS '检验结果：合格/不合格';


--
-- Name: COLUMN finished_goods_inspection.report_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.report_no IS '报告编号（唯一）';


--
-- Name: COLUMN finished_goods_inspection.inspection_request_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.inspection_request_no IS '送检单号';


--
-- Name: COLUMN finished_goods_inspection.production_order_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.production_order_no IS '生产订单号';


--
-- Name: COLUMN finished_goods_inspection.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.material_code IS '物料编码';


--
-- Name: COLUMN finished_goods_inspection.product_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.product_name IS '产品名称';


--
-- Name: COLUMN finished_goods_inspection.model_spec; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.model_spec IS '型号规格';


--
-- Name: COLUMN finished_goods_inspection.prod_batch_or_sn; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.prod_batch_or_sn IS '生产批号或产品编号';


--
-- Name: COLUMN finished_goods_inspection.production_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.production_date IS '生产日期';


--
-- Name: COLUMN finished_goods_inspection.expiry_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.expiry_date IS '有效期至';


--
-- Name: COLUMN finished_goods_inspection.submitted_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.submitted_qty IS '送检数量';


--
-- Name: COLUMN finished_goods_inspection.inspected_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.inspected_qty IS '检验数量';


--
-- Name: COLUMN finished_goods_inspection.qualified_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.qualified_qty IS '合格数量';


--
-- Name: COLUMN finished_goods_inspection.unqualified_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.unqualified_qty IS '不合格数量';


--
-- Name: COLUMN finished_goods_inspection.unit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.unit IS '单位';


--
-- Name: COLUMN finished_goods_inspection.inspector_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.inspector_name IS '检验名字';


--
-- Name: COLUMN finished_goods_inspection.category; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.category IS '分类';


--
-- Name: COLUMN finished_goods_inspection.qc_reviewer; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.qc_reviewer IS '品管复核人';


--
-- Name: COLUMN finished_goods_inspection.qc_review_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.qc_review_time IS '品管复核时间';


--
-- Name: COLUMN finished_goods_inspection.mgr_representative; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.mgr_representative IS '管代';


--
-- Name: COLUMN finished_goods_inspection.mgr_approval_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.mgr_approval_time IS '管代批准时间';


--
-- Name: COLUMN finished_goods_inspection.is_entrusted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.is_entrusted IS '是否委托：是/否';


--
-- Name: COLUMN finished_goods_inspection.drug_reg_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.drug_reg_no IS '药监批号';


--
-- Name: COLUMN finished_goods_inspection.perf_test_method; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.perf_test_method IS '性能检验方式';


--
-- Name: COLUMN finished_goods_inspection.perf_sample_batch_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.perf_sample_batch_no IS '性能抽检批次编号';


--
-- Name: COLUMN finished_goods_inspection.signature_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.signature_user IS '电子签名人';


--
-- Name: COLUMN finished_goods_inspection.signature_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.signature_time IS '电子签名时间';


--
-- Name: COLUMN finished_goods_inspection.signature_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.signature_reason IS '电子签名原因';


--
-- Name: COLUMN finished_goods_inspection.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN finished_goods_inspection.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.plant_name IS '分公司名称';


--
-- Name: COLUMN finished_goods_inspection.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.created_by IS '创建人';


--
-- Name: COLUMN finished_goods_inspection.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.updated_by IS '更新人';


--
-- Name: COLUMN finished_goods_inspection.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN finished_goods_inspection.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.version IS '乐观锁版本号';


--
-- Name: COLUMN finished_goods_inspection.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.created_at IS '创建时间';


--
-- Name: COLUMN finished_goods_inspection.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.finished_goods_inspection.updated_at IS '更新时间';


--
-- Name: finished_goods_inspection_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.finished_goods_inspection ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.finished_goods_inspection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: improvement_action; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.improvement_action (
    id bigint NOT NULL,
    exception_id bigint NOT NULL,
    action_type character varying(16) NOT NULL,
    content text NOT NULL,
    owner_id bigint NOT NULL,
    owner_name character varying(64),
    due_date date,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    completed_at timestamp without time zone,
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


--
-- Name: TABLE improvement_action; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.improvement_action IS '改善措施表（M2：关联异常单的临时/纠正/预防措施，对应8D D3/D4/D5步骤）';


--
-- Name: COLUMN improvement_action.exception_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.exception_id IS '关联异常单ID（外键 exception_order.id）';


--
-- Name: COLUMN improvement_action.action_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.action_type IS '措施类型：临时措施(8D D3)/纠正措施(8D D4)/预防措施(8D D5)';


--
-- Name: COLUMN improvement_action.content; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.content IS '措施内容（详细描述）';


--
-- Name: COLUMN improvement_action.owner_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.owner_id IS '责任人ID（关联 sys_user.id）';


--
-- Name: COLUMN improvement_action.owner_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.owner_name IS '责任人姓名（冗余字段，避免联表查询）';


--
-- Name: COLUMN improvement_action.due_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.due_date IS '截止日期';


--
-- Name: COLUMN improvement_action.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.status IS '状态：PENDING=待完成 DONE=已完成（保留英文代码）';


--
-- Name: COLUMN improvement_action.completed_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.completed_at IS '完成时间（status=DONE时写入）';


--
-- Name: COLUMN improvement_action.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.remark IS '备注';


--
-- Name: COLUMN improvement_action.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（继承自异常单）';


--
-- Name: COLUMN improvement_action.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.plant_name IS '分公司名称';


--
-- Name: COLUMN improvement_action.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.created_by IS '创建人';


--
-- Name: COLUMN improvement_action.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.updated_by IS '更新人';


--
-- Name: COLUMN improvement_action.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN improvement_action.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.version IS '乐观锁版本号';


--
-- Name: COLUMN improvement_action.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.created_at IS '创建时间';


--
-- Name: COLUMN improvement_action.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.improvement_action.updated_at IS '更新时间';


--
-- Name: improvement_action_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.improvement_action ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.improvement_action_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: material_inspection; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.material_inspection (
    id bigint NOT NULL,
    process_no character varying(64),
    form_version character varying(32),
    is_customer_supplied character varying(8) DEFAULT '否'::character varying NOT NULL,
    memo text,
    material_category character varying(64),
    is_valid character varying(8) DEFAULT '是'::character varying NOT NULL,
    review_status character varying(16) DEFAULT '待审核'::character varying NOT NULL,
    signature_status character varying(16) DEFAULT '未签'::character varying NOT NULL,
    is_urgent character varying(8) DEFAULT '否'::character varying NOT NULL,
    data_record_flag character varying(8) DEFAULT '否'::character varying NOT NULL,
    is_invalid character varying(8) DEFAULT '否'::character varying NOT NULL,
    report_generated character varying(8) DEFAULT '否'::character varying NOT NULL,
    record_no character varying(64) NOT NULL,
    purchase_order character varying(64),
    inbound_no character varying(64),
    inspection_request_no character varying(64),
    mes_inspection_no character varying(64),
    inspection_date date,
    judgement_date date,
    inspector character varying(64),
    inspection_result character varying(16),
    supplier_name character varying(128),
    material_code character varying(64),
    material_name character varying(128),
    spec_model character varying(128),
    material_batch_no character varying(128),
    qualified_qty numeric(18,3),
    unqualified_qty numeric(18,3),
    submitted_qty numeric(18,3),
    loss_qty numeric(18,3),
    unit character varying(16),
    defect_desc text,
    handling_method character varying(32),
    unqualified_final_status character varying(32),
    unqualified_review character varying(32),
    unqualified_review_no character varying(64),
    inspection_category character varying(64),
    arrival_date date,
    receiving_no character varying(64),
    po_line_no character varying(32),
    receiving_line_no character varying(32),
    shelf_life_days integer,
    reinspect_remark text,
    judge character varying(64),
    inspection_end_date date,
    reviewer character varying(64),
    review_date date,
    submitter character varying(64),
    submit_date date,
    supplier_code character varying(64),
    remark text,
    ext_id character varying(64),
    last_modified_by character varying(64),
    signature_user character varying(64),
    signature_time timestamp without time zone,
    signature_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE material_inspection; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.material_inspection IS '物料检验入库审核表（M1：来料批次质量数据，含审核签名流程，material_batch_no 为全链路追溯核心键）';


--
-- Name: COLUMN material_inspection.process_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.process_no IS '处理单号';


--
-- Name: COLUMN material_inspection.form_version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.form_version IS '表单版本';


--
-- Name: COLUMN material_inspection.is_customer_supplied; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.is_customer_supplied IS '是否客供料：是/否';


--
-- Name: COLUMN material_inspection.memo; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.memo IS '备注';


--
-- Name: COLUMN material_inspection.material_category; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.material_category IS '物料类别';


--
-- Name: COLUMN material_inspection.is_valid; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.is_valid IS '是否有效：是/否';


--
-- Name: COLUMN material_inspection.review_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.review_status IS '审核状态：待审核/已审核/驳回';


--
-- Name: COLUMN material_inspection.signature_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.signature_status IS '签名状态：已签/未签';


--
-- Name: COLUMN material_inspection.is_urgent; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.is_urgent IS '是否急料：是/否';


--
-- Name: COLUMN material_inspection.data_record_flag; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.data_record_flag IS '数据记录标识：是/否';


--
-- Name: COLUMN material_inspection.is_invalid; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.is_invalid IS '是否失效：是/否';


--
-- Name: COLUMN material_inspection.report_generated; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.report_generated IS '报表已生成：是/否';


--
-- Name: COLUMN material_inspection.record_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.record_no IS '记录编号（唯一）';


--
-- Name: COLUMN material_inspection.purchase_order; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.purchase_order IS '采购订单';


--
-- Name: COLUMN material_inspection.inbound_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inbound_no IS '入库单号';


--
-- Name: COLUMN material_inspection.inspection_request_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inspection_request_no IS '送检单号';


--
-- Name: COLUMN material_inspection.mes_inspection_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.mes_inspection_no IS 'MES检验单号';


--
-- Name: COLUMN material_inspection.inspection_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inspection_date IS '检验日期';


--
-- Name: COLUMN material_inspection.judgement_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.judgement_date IS '判定日期';


--
-- Name: COLUMN material_inspection.inspector; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inspector IS '检验人员';


--
-- Name: COLUMN material_inspection.inspection_result; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inspection_result IS '检验结果：合格/不合格';


--
-- Name: COLUMN material_inspection.supplier_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.supplier_name IS '供应商名称';


--
-- Name: COLUMN material_inspection.material_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.material_code IS '物料代码';


--
-- Name: COLUMN material_inspection.material_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.material_name IS '物料名称';


--
-- Name: COLUMN material_inspection.spec_model; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.spec_model IS '规格型号';


--
-- Name: COLUMN material_inspection.material_batch_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.material_batch_no IS '物料批号（全链路追溯核心键，建立唯一索引）';


--
-- Name: COLUMN material_inspection.qualified_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.qualified_qty IS '合格数量';


--
-- Name: COLUMN material_inspection.unqualified_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.unqualified_qty IS '不合格数量';


--
-- Name: COLUMN material_inspection.submitted_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.submitted_qty IS '送检数量';


--
-- Name: COLUMN material_inspection.loss_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.loss_qty IS '损耗数量';


--
-- Name: COLUMN material_inspection.unit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.unit IS '单位';


--
-- Name: COLUMN material_inspection.defect_desc; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.defect_desc IS '不合格描述（自由文本，不做枚举下拉框；M2多维分析按此字段GROUP BY聚合）';


--
-- Name: COLUMN material_inspection.handling_method; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.handling_method IS '处理方式：退货/挑选/特采/报废';


--
-- Name: COLUMN material_inspection.unqualified_final_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.unqualified_final_status IS '不合格最终状态';


--
-- Name: COLUMN material_inspection.unqualified_review; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.unqualified_review IS '不合格评审';


--
-- Name: COLUMN material_inspection.unqualified_review_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.unqualified_review_no IS '不合格评审单号';


--
-- Name: COLUMN material_inspection.inspection_category; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inspection_category IS '检验类别';


--
-- Name: COLUMN material_inspection.arrival_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.arrival_date IS '来料日期';


--
-- Name: COLUMN material_inspection.receiving_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.receiving_no IS '收料单号';


--
-- Name: COLUMN material_inspection.po_line_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.po_line_no IS '采购订单行号';


--
-- Name: COLUMN material_inspection.receiving_line_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.receiving_line_no IS '收料单行号';


--
-- Name: COLUMN material_inspection.shelf_life_days; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.shelf_life_days IS '保质期天';


--
-- Name: COLUMN material_inspection.reinspect_remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.reinspect_remark IS '复检备注';


--
-- Name: COLUMN material_inspection.judge; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.judge IS '判定人';


--
-- Name: COLUMN material_inspection.inspection_end_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.inspection_end_date IS '检验结束日期';


--
-- Name: COLUMN material_inspection.reviewer; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.reviewer IS '审核人';


--
-- Name: COLUMN material_inspection.review_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.review_date IS '审核日期';


--
-- Name: COLUMN material_inspection.submitter; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.submitter IS '送检人';


--
-- Name: COLUMN material_inspection.submit_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.submit_date IS '送检日期';


--
-- Name: COLUMN material_inspection.supplier_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.supplier_code IS '供应商编号';


--
-- Name: COLUMN material_inspection.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.remark IS '备注(扩展)';


--
-- Name: COLUMN material_inspection.ext_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.ext_id IS '原ID（数据迁移用）';


--
-- Name: COLUMN material_inspection.last_modified_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.last_modified_by IS '最后修改人';


--
-- Name: COLUMN material_inspection.signature_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.signature_user IS '电子签名人';


--
-- Name: COLUMN material_inspection.signature_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.signature_time IS '电子签名时间';


--
-- Name: COLUMN material_inspection.signature_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.signature_reason IS '电子签名原因';


--
-- Name: COLUMN material_inspection.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN material_inspection.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.plant_name IS '分公司名称';


--
-- Name: COLUMN material_inspection.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.created_by IS '创建人';


--
-- Name: COLUMN material_inspection.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.updated_by IS '更新人';


--
-- Name: COLUMN material_inspection.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN material_inspection.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.version IS '乐观锁版本号';


--
-- Name: COLUMN material_inspection.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.created_at IS '创建时间';


--
-- Name: COLUMN material_inspection.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.material_inspection.updated_at IS '更新时间';


--
-- Name: material_inspection_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.material_inspection ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.material_inspection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notification; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.notification (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type character varying(64) NOT NULL,
    title character varying(256) NOT NULL,
    content text,
    business_type character varying(64),
    business_id bigint,
    is_read smallint DEFAULT 0 NOT NULL,
    read_at timestamp without time zone,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    level character varying(16) DEFAULT '提醒'::character varying NOT NULL
);


--
-- Name: TABLE notification; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.notification IS '站内通知/消息中心（M2：异常单创建、状态变更、升级触发时自动写入）';


--
-- Name: COLUMN notification.user_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.user_id IS '接收人ID（关联 sys_user.id）';


--
-- Name: COLUMN notification.type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.type IS '通知类型：EXCEPTION_CREATED/EXCEPTION_STATUS_CHANGED/ESCALATION_TRIGGERED';


--
-- Name: COLUMN notification.title; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.title IS '标题';


--
-- Name: COLUMN notification.content; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.content IS '内容';


--
-- Name: COLUMN notification.business_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.business_type IS '业务类型：EXCEPTION_ORDER / ESCALATION';


--
-- Name: COLUMN notification.business_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.business_id IS '业务ID';


--
-- Name: COLUMN notification.is_read; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.is_read IS '是否已读：0=未读 1=已读';


--
-- Name: COLUMN notification.read_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.read_at IS '读取时间';


--
-- Name: COLUMN notification.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN notification.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.plant_name IS '分公司名称';


--
-- Name: COLUMN notification.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.created_by IS '创建人';


--
-- Name: COLUMN notification.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.updated_by IS '更新人';


--
-- Name: COLUMN notification.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN notification.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.version IS '乐观锁版本号';


--
-- Name: COLUMN notification.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.created_at IS '创建时间';


--
-- Name: COLUMN notification.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.updated_at IS '更新时间';


--
-- Name: COLUMN notification.level; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.notification.level IS '通知等级：严重/警告/提醒';


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.notification ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: production_repair; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.production_repair (
    id bigint NOT NULL,
    audit_status character varying(16) DEFAULT '待审核'::character varying NOT NULL,
    form_name character varying(128),
    repair_no character varying(64) NOT NULL,
    product_no character varying(64),
    product_name character varying(128),
    spec_model character varying(128),
    work_order_no character varying(64),
    product_batch_or_sn character varying(128),
    process character varying(64),
    defect_qty numeric(18,3),
    defect_phenomenon text,
    defect_code character varying(64),
    send_repair_date date,
    repair_date date,
    repair_judgment_result character varying(64),
    repair_status character varying(16),
    repair_record text,
    send_repairer character varying(64),
    repairer character varying(64),
    auditor character varying(64),
    audit_date date,
    remark text,
    signature_user character varying(64),
    signature_time timestamp without time zone,
    signature_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    repair_done smallint DEFAULT 0 NOT NULL,
    form_no character varying(64)
);


--
-- Name: TABLE production_repair; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.production_repair IS '生产维修记录表（M1独立表：全品类生产不良数据归集与分析，不参与来料→成品主追溯链路）';


--
-- Name: COLUMN production_repair.audit_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.audit_status IS '审核状态：待审核/已审核/驳回';


--
-- Name: COLUMN production_repair.form_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.form_name IS '表格名称';


--
-- Name: COLUMN production_repair.repair_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repair_no IS '维修编号（唯一）';


--
-- Name: COLUMN production_repair.product_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.product_no IS '产品编号';


--
-- Name: COLUMN production_repair.product_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.product_name IS '产品名称';


--
-- Name: COLUMN production_repair.spec_model; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.spec_model IS '规格型号';


--
-- Name: COLUMN production_repair.work_order_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.work_order_no IS '生产工单号';


--
-- Name: COLUMN production_repair.product_batch_or_sn; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.product_batch_or_sn IS '产品批号/序列号';


--
-- Name: COLUMN production_repair.process; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.process IS '生产工序';


--
-- Name: COLUMN production_repair.defect_qty; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.defect_qty IS '不良数量';


--
-- Name: COLUMN production_repair.defect_phenomenon; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.defect_phenomenon IS '不良现象（自由文本描述）';


--
-- Name: COLUMN production_repair.defect_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.defect_code IS '不良代码';


--
-- Name: COLUMN production_repair.send_repair_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.send_repair_date IS '送修日期';


--
-- Name: COLUMN production_repair.repair_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repair_date IS '维修日期';


--
-- Name: COLUMN production_repair.repair_judgment_result; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repair_judgment_result IS '维修判定结果';


--
-- Name: COLUMN production_repair.repair_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repair_status IS '维修状态';


--
-- Name: COLUMN production_repair.repair_record; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repair_record IS '维修记录（维修人填写）';


--
-- Name: COLUMN production_repair.send_repairer; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.send_repairer IS '送修人';


--
-- Name: COLUMN production_repair.repairer; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repairer IS '维修人';


--
-- Name: COLUMN production_repair.auditor; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.auditor IS '审核人';


--
-- Name: COLUMN production_repair.audit_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.audit_date IS '审核日期';


--
-- Name: COLUMN production_repair.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.remark IS '备注';


--
-- Name: COLUMN production_repair.signature_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.signature_user IS '电子签名人';


--
-- Name: COLUMN production_repair.signature_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.signature_time IS '电子签名时间';


--
-- Name: COLUMN production_repair.signature_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.signature_reason IS '电子签名原因';


--
-- Name: COLUMN production_repair.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN production_repair.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.plant_name IS '分公司名称';


--
-- Name: COLUMN production_repair.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.created_by IS '创建人';


--
-- Name: COLUMN production_repair.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.updated_by IS '更新人';


--
-- Name: COLUMN production_repair.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN production_repair.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.version IS '乐观锁版本号';


--
-- Name: COLUMN production_repair.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.created_at IS '创建时间';


--
-- Name: COLUMN production_repair.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.updated_at IS '更新时间';


--
-- Name: COLUMN production_repair.repair_done; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.repair_done IS '维修状态（数值）：0=未维修 1=已维修；对应Excel「维修状态」列，非冗余字段';


--
-- Name: COLUMN production_repair.form_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.production_repair.form_no IS '表格编号（Excel「表格编号」列），如 Y/KL.QR-834-02';


--
-- Name: production_repair_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.production_repair ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.production_repair_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: qms_migration_log; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.qms_migration_log (
    id bigint NOT NULL,
    version character varying(32) NOT NULL,
    description character varying(255) NOT NULL,
    applied_at timestamp without time zone DEFAULT now() NOT NULL,
    applied_by character varying(64) NOT NULL
);


--
-- Name: TABLE qms_migration_log; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.qms_migration_log IS 'QMS DDL 迁移记录';


--
-- Name: COLUMN qms_migration_log.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.qms_migration_log.version IS '迁移版本号，如 V20260717_01';


--
-- Name: COLUMN qms_migration_log.applied_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.qms_migration_log.applied_by IS '执行人（工号/系统）';


--
-- Name: rectification_plan; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.rectification_plan (
    id bigint NOT NULL,
    exception_id bigint NOT NULL,
    plan_no character varying(64),
    plan_name character varying(128) NOT NULL,
    objective text,
    owner_id bigint,
    owner_name character varying(64),
    plan_start_date date,
    plan_end_date date,
    status character varying(16) DEFAULT '待执行'::character varying NOT NULL,
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


--
-- Name: TABLE rectification_plan; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.rectification_plan IS '整改计划表（M2：与改善措施区分的独立对象，描述异常整改的整体计划/目标/周期，下挂改善措施）';


--
-- Name: COLUMN rectification_plan.exception_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.exception_id IS '关联异常单ID（外键 exception_order.id）';


--
-- Name: COLUMN rectification_plan.plan_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.plan_no IS '计划编号（后端自动生成 RP+6位序号，允许覆盖）';


--
-- Name: COLUMN rectification_plan.plan_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.plan_name IS '计划名称';


--
-- Name: COLUMN rectification_plan.objective; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.objective IS '整改目标';


--
-- Name: COLUMN rectification_plan.owner_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.owner_id IS '负责人ID（关联 sys_user.id）';


--
-- Name: COLUMN rectification_plan.owner_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.owner_name IS '负责人姓名（冗余，便于展示）';


--
-- Name: COLUMN rectification_plan.plan_start_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.plan_start_date IS '计划开始日期';


--
-- Name: COLUMN rectification_plan.plan_end_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.plan_end_date IS '计划结束日期';


--
-- Name: COLUMN rectification_plan.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.status IS '状态：待执行=未开始 执行中=进行中 已完成=已闭环（保留中文枚举）';


--
-- Name: COLUMN rectification_plan.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.remark IS '备注';


--
-- Name: COLUMN rectification_plan.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN rectification_plan.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.plant_name IS '分公司名称';


--
-- Name: COLUMN rectification_plan.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.created_by IS '创建人';


--
-- Name: COLUMN rectification_plan.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.updated_by IS '更新人';


--
-- Name: COLUMN rectification_plan.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN rectification_plan.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.version IS '乐观锁版本号';


--
-- Name: COLUMN rectification_plan.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.created_at IS '创建时间';


--
-- Name: COLUMN rectification_plan.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.rectification_plan.updated_at IS '更新时间';


--
-- Name: rectification_plan_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.rectification_plan ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.rectification_plan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: spc_capability; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_capability (
    id bigint NOT NULL,
    param_id bigint NOT NULL,
    cp numeric(18,6),
    cpk numeric(18,6),
    pp numeric(18,6),
    ppk numeric(18,6),
    cpu numeric(18,6),
    cpl numeric(18,6),
    sample_count integer,
    subgroup_count integer,
    judgment character varying(16),
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL
);


--
-- Name: TABLE spc_capability; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_capability IS 'SPC 过程能力指数表';


--
-- Name: COLUMN spc_capability.param_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.param_id IS '关联 spc_parameter.id';


--
-- Name: COLUMN spc_capability.cp; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.cp IS '过程能力指数 CP';


--
-- Name: COLUMN spc_capability.cpk; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.cpk IS '过程能力指数 CPK';


--
-- Name: COLUMN spc_capability.pp; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.pp IS '过程性能指数 PP';


--
-- Name: COLUMN spc_capability.ppk; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.ppk IS '过程性能指数 PPK';


--
-- Name: COLUMN spc_capability.cpu; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.cpu IS '上能力指数 CPU';


--
-- Name: COLUMN spc_capability.cpl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.cpl IS '下能力指数 CPL';


--
-- Name: COLUMN spc_capability.sample_count; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.sample_count IS '总样本数';


--
-- Name: COLUMN spc_capability.subgroup_count; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.subgroup_count IS '子组数';


--
-- Name: COLUMN spc_capability.judgment; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.judgment IS '判定：充足/需改进/不足';


--
-- Name: COLUMN spc_capability.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_capability.plant_code IS '分公司编码 SZ=深圳 / MZ=梅州（数据隔离）';


--
-- Name: spc_capability_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

CREATE SEQUENCE qms.spc_capability_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spc_capability_id_seq; Type: SEQUENCE OWNED BY; Schema: qms; Owner: -
--

ALTER SEQUENCE qms.spc_capability_id_seq OWNED BY qms.spc_capability.id;


--
-- Name: spc_coefficient; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_coefficient (
    n integer NOT NULL,
    a2 numeric(8,4) NOT NULL,
    a3 numeric(8,4) NOT NULL,
    d2 numeric(8,4) NOT NULL,
    d3 numeric(8,4) NOT NULL,
    d3_l numeric(8,4) NOT NULL,
    d4 numeric(8,4) NOT NULL,
    b3 numeric(8,4) NOT NULL,
    b4 numeric(8,4) NOT NULL,
    c4 numeric(8,4) DEFAULT 0 NOT NULL
);


--
-- Name: TABLE spc_coefficient; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_coefficient IS 'SPC 控制图系数表（AIAG-VDA 标准，n=2~12，固化基线）';


--
-- Name: COLUMN spc_coefficient.n; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.n IS '子组容量 2~12';


--
-- Name: COLUMN spc_coefficient.a2; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.a2 IS 'Xbar-R 图系数 A2';


--
-- Name: COLUMN spc_coefficient.a3; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.a3 IS 'Xbar-s 图系数 A3';


--
-- Name: COLUMN spc_coefficient.d2; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.d2 IS '估计系数 d2';


--
-- Name: COLUMN spc_coefficient.d3; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.d3 IS 'd2 标准差系数 d3';


--
-- Name: COLUMN spc_coefficient.d3_l; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.d3_l IS '极差图下控制限系数 D3';


--
-- Name: COLUMN spc_coefficient.d4; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.d4 IS '极差图上控制限系数 D4';


--
-- Name: COLUMN spc_coefficient.b3; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.b3 IS 's 图下控制限系数 B3';


--
-- Name: COLUMN spc_coefficient.b4; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_coefficient.b4 IS 's 图上控制限系数 B4';


--
-- Name: spc_control_limit; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_control_limit (
    id bigint NOT NULL,
    param_id bigint NOT NULL,
    chart_type character varying(16) NOT NULL,
    xbar_ucl numeric(18,6),
    xbar_cl numeric(18,6),
    xbar_lcl numeric(18,6),
    r_ucl numeric(18,6),
    r_cl numeric(18,6),
    r_lcl numeric(18,6),
    s_ucl numeric(18,6),
    s_cl numeric(18,6),
    s_lcl numeric(18,6),
    subgroup_count integer,
    calc_date timestamp without time zone,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL
);


--
-- Name: TABLE spc_control_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_control_limit IS 'SPC 控制限计算结果表';


--
-- Name: COLUMN spc_control_limit.param_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.param_id IS '关联 spc_parameter.id';


--
-- Name: COLUMN spc_control_limit.chart_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.chart_type IS 'Xbar-R / Xbar-s';


--
-- Name: COLUMN spc_control_limit.xbar_ucl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.xbar_ucl IS 'X̄ 图上控制限';


--
-- Name: COLUMN spc_control_limit.xbar_cl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.xbar_cl IS 'X̄ 图中心线';


--
-- Name: COLUMN spc_control_limit.xbar_lcl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.xbar_lcl IS 'X̄ 图下控制限';


--
-- Name: COLUMN spc_control_limit.r_ucl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.r_ucl IS 'R 图上控制限';


--
-- Name: COLUMN spc_control_limit.r_cl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.r_cl IS 'R 图中心线';


--
-- Name: COLUMN spc_control_limit.r_lcl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.r_lcl IS 'R 图下控制限';


--
-- Name: COLUMN spc_control_limit.s_ucl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.s_ucl IS 's 图上控制限';


--
-- Name: COLUMN spc_control_limit.s_cl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.s_cl IS 's 图中心线';


--
-- Name: COLUMN spc_control_limit.s_lcl; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.s_lcl IS 's 图下控制限';


--
-- Name: COLUMN spc_control_limit.subgroup_count; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.subgroup_count IS '用于计算的子组数';


--
-- Name: COLUMN spc_control_limit.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_control_limit.plant_code IS '分公司编码 SZ=深圳 / MZ=梅州（数据隔离）';


--
-- Name: spc_control_limit_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

CREATE SEQUENCE qms.spc_control_limit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spc_control_limit_id_seq; Type: SEQUENCE OWNED BY; Schema: qms; Owner: -
--

ALTER SEQUENCE qms.spc_control_limit_id_seq OWNED BY qms.spc_control_limit.id;


--
-- Name: spc_parameter; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_parameter (
    id bigint NOT NULL,
    process_id bigint NOT NULL,
    param_code character varying(32) NOT NULL,
    param_name character varying(64) NOT NULL,
    param_type character varying(16),
    unit character varying(16),
    upper_spec_limit numeric(18,6),
    lower_spec_limit numeric(18,6),
    target_value numeric(18,6),
    subgroup_size integer DEFAULT 5 NOT NULL,
    chart_type character varying(16) DEFAULT 'Xbar-R'::character varying NOT NULL,
    is_active character varying(8) DEFAULT '是'::character varying,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL
);


--
-- Name: TABLE spc_parameter; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_parameter IS 'SPC 关键参数定义表';


--
-- Name: COLUMN spc_parameter.process_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.process_id IS '关联 spc_process.id';


--
-- Name: COLUMN spc_parameter.param_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.param_code IS '参数编码';


--
-- Name: COLUMN spc_parameter.param_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.param_type IS '参数类型：尺寸/温度/压力/扭矩/电压';


--
-- Name: COLUMN spc_parameter.unit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.unit IS '单位（mm/°C/MPa/N·m/V）';


--
-- Name: COLUMN spc_parameter.upper_spec_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.upper_spec_limit IS '规格上限 USL';


--
-- Name: COLUMN spc_parameter.lower_spec_limit; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.lower_spec_limit IS '规格下限 LSL';


--
-- Name: COLUMN spc_parameter.target_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.target_value IS '目标值';


--
-- Name: COLUMN spc_parameter.subgroup_size; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.subgroup_size IS '子组大小 n（2~10）';


--
-- Name: COLUMN spc_parameter.chart_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.chart_type IS '控制图类型：Xbar-R / Xbar-s';


--
-- Name: COLUMN spc_parameter.is_active; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.is_active IS '是否启用：是/否';


--
-- Name: COLUMN spc_parameter.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_parameter.plant_code IS '分公司编码 SZ=深圳 / MZ=梅州（数据隔离）';


--
-- Name: spc_parameter_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

CREATE SEQUENCE qms.spc_parameter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spc_parameter_id_seq; Type: SEQUENCE OWNED BY; Schema: qms; Owner: -
--

ALTER SEQUENCE qms.spc_parameter_id_seq OWNED BY qms.spc_parameter.id;


--
-- Name: spc_process; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_process (
    id bigint NOT NULL,
    process_code character varying(32) NOT NULL,
    process_name character varying(64) NOT NULL,
    description character varying(256),
    sort_order integer DEFAULT 0,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL
);


--
-- Name: TABLE spc_process; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_process IS 'SPC 工序定义表（装配/焊接/检测）';


--
-- Name: COLUMN spc_process.process_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_process.process_code IS '工序编码（ASM 装配 / WDG 焊接 / INS 检测）';


--
-- Name: COLUMN spc_process.process_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_process.process_name IS '工序名称（装配/焊接/检测）';


--
-- Name: COLUMN spc_process.sort_order; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_process.sort_order IS '排序';


--
-- Name: COLUMN spc_process.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_process.plant_code IS '分公司编码 SZ=深圳 / MZ=梅州（数据隔离）';


--
-- Name: COLUMN spc_process.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_process.is_deleted IS '软删除标志 0 未删 / 1 已删';


--
-- Name: COLUMN spc_process.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_process.version IS '乐观锁版本号';


--
-- Name: spc_process_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

CREATE SEQUENCE qms.spc_process_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spc_process_id_seq; Type: SEQUENCE OWNED BY; Schema: qms; Owner: -
--

ALTER SEQUENCE qms.spc_process_id_seq OWNED BY qms.spc_process.id;


--
-- Name: spc_sample; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_sample (
    id bigint NOT NULL,
    subgroup_id bigint NOT NULL,
    sample_no integer NOT NULL,
    sample_value numeric(18,6) NOT NULL,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL
);


--
-- Name: TABLE spc_sample; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_sample IS 'SPC 采样明细表（子组下的单样本值）';


--
-- Name: COLUMN spc_sample.subgroup_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_sample.subgroup_id IS '关联 spc_subgroup.id';


--
-- Name: COLUMN spc_sample.sample_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_sample.sample_no IS '样本序号（1~n）';


--
-- Name: COLUMN spc_sample.sample_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_sample.sample_value IS '样本实测值';


--
-- Name: COLUMN spc_sample.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_sample.plant_code IS '分公司编码 SZ=深圳 / MZ=梅州（数据隔离）';


--
-- Name: spc_sample_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

CREATE SEQUENCE qms.spc_sample_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spc_sample_id_seq; Type: SEQUENCE OWNED BY; Schema: qms; Owner: -
--

ALTER SEQUENCE qms.spc_sample_id_seq OWNED BY qms.spc_sample.id;


--
-- Name: spc_subgroup; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.spc_subgroup (
    id bigint NOT NULL,
    param_id bigint NOT NULL,
    subgroup_no character varying(64) NOT NULL,
    sample_count integer NOT NULL,
    mean_value numeric(18,6),
    range_value numeric(18,6),
    std_dev numeric(18,6),
    sample_time timestamp without time zone,
    source_type character varying(16) DEFAULT '手动录入'::character varying,
    fai_record_id bigint,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    work_order_no character varying(64),
    batch_no character varying(64),
    process_code character varying(32),
    param_code character varying(32),
    unit character varying(16),
    standard_version integer,
    material_code character varying(64),
    material_name character varying(128),
    subgroup_status character varying(16) DEFAULT '已完成'::character varying NOT NULL
);


--
-- Name: TABLE spc_subgroup; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.spc_subgroup IS 'SPC 子组表';


--
-- Name: COLUMN spc_subgroup.param_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.param_id IS '关联 spc_parameter.id';


--
-- Name: COLUMN spc_subgroup.subgroup_no; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.subgroup_no IS '子组编号 SG-{plant}-{param}-{yyyyMMdd}-{4位流水}';


--
-- Name: COLUMN spc_subgroup.sample_count; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.sample_count IS '样本数';


--
-- Name: COLUMN spc_subgroup.mean_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.mean_value IS '子组均值 X̄';


--
-- Name: COLUMN spc_subgroup.range_value; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.range_value IS '子组极差 R';


--
-- Name: COLUMN spc_subgroup.std_dev; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.std_dev IS '子组标准差 s';


--
-- Name: COLUMN spc_subgroup.source_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.source_type IS '来源：手动录入/首件导入/自动采集';


--
-- Name: COLUMN spc_subgroup.fai_record_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.fai_record_id IS '关联首件记录 id（首件导入时必填）';


--
-- Name: COLUMN spc_subgroup.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.spc_subgroup.plant_code IS '分公司编码 SZ=深圳 / MZ=梅州（数据隔离）';


--
-- Name: spc_subgroup_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

CREATE SEQUENCE qms.spc_subgroup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: spc_subgroup_id_seq; Type: SEQUENCE OWNED BY; Schema: qms; Owner: -
--

ALTER SEQUENCE qms.spc_subgroup_id_seq OWNED BY qms.spc_subgroup.id;


--
-- Name: supplier; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.supplier (
    id bigint NOT NULL,
    supplier_code character varying(64) NOT NULL,
    supplier_name character varying(128) NOT NULL,
    contact_person character varying(64),
    contact_phone character varying(32),
    address character varying(256),
    risk_level character varying(8),
    status character varying(8) DEFAULT '启用'::character varying NOT NULL,
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


--
-- Name: TABLE supplier; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.supplier IS '供应商基础表（M2：供应商主数据，供 M1/M2/M7 各模块关联引用）';


--
-- Name: COLUMN supplier.supplier_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.supplier_code IS '供应商编号（业务唯一，如 SUP-SZ-01）';


--
-- Name: COLUMN supplier.supplier_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.supplier_name IS '供应商名称';


--
-- Name: COLUMN supplier.contact_person; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.contact_person IS '联系人';


--
-- Name: COLUMN supplier.contact_phone; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.contact_phone IS '联系电话';


--
-- Name: COLUMN supplier.address; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.address IS '地址';


--
-- Name: COLUMN supplier.risk_level; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.risk_level IS '风险等级：高=高风险 中=中风险 低=低风险（M7 审核频次分配依据）';


--
-- Name: COLUMN supplier.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.status IS '状态：启用/停用';


--
-- Name: COLUMN supplier.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.remark IS '备注';


--
-- Name: COLUMN supplier.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN supplier.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.plant_name IS '分公司名称';


--
-- Name: COLUMN supplier.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.created_by IS '创建人';


--
-- Name: COLUMN supplier.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.updated_by IS '更新人';


--
-- Name: COLUMN supplier.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN supplier.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.version IS '乐观锁版本号';


--
-- Name: COLUMN supplier.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.created_at IS '创建时间';


--
-- Name: COLUMN supplier.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.supplier.updated_at IS '更新时间';


--
-- Name: supplier_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.supplier ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.supplier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sys_login_log; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.sys_login_log (
    id bigint NOT NULL,
    user_id bigint,
    account character varying(64) NOT NULL,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    login_ip character varying(64),
    login_status character varying(16) NOT NULL,
    fail_reason character varying(256),
    login_time timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64),
    updated_by character varying(64) DEFAULT NULL::character varying,
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE sys_login_log; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.sys_login_log IS '登录日志表（框架表，sys_ 前缀，仅追加不修改）';


--
-- Name: COLUMN sys_login_log.id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.id IS '主键自增ID';


--
-- Name: COLUMN sys_login_log.user_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.user_id IS '用户ID（关联 sys_user.id，登录失败时可能为空）';


--
-- Name: COLUMN sys_login_log.account; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.account IS '登录账号';


--
-- Name: COLUMN sys_login_log.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.plant_code IS '分公司编码 SZ=深圳 MZ=梅州';


--
-- Name: COLUMN sys_login_log.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.plant_name IS '分公司名称';


--
-- Name: COLUMN sys_login_log.login_ip; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.login_ip IS '登录IP地址';


--
-- Name: COLUMN sys_login_log.login_status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.login_status IS '登录状态 成功=登录成功 失败=密码错误/验证码错误 锁定=账号被锁定';


--
-- Name: COLUMN sys_login_log.fail_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.fail_reason IS '失败原因（仅失败/锁定时有值，如：密码错误、验证码错误、账号已锁定N分钟）';


--
-- Name: COLUMN sys_login_log.login_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.login_time IS '登录时间';


--
-- Name: COLUMN sys_login_log.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.created_by IS '创建人（登录操作时为用户账号）';


--
-- Name: COLUMN sys_login_log.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.updated_by IS '更新人（日志表不更新）';


--
-- Name: COLUMN sys_login_log.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN sys_login_log.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.version IS '乐观锁版本号（日志表固定为1）';


--
-- Name: COLUMN sys_login_log.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.created_at IS '创建时间';


--
-- Name: COLUMN sys_login_log.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_login_log.updated_at IS '更新时间（日志表=created_at）';


--
-- Name: sys_login_log_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.sys_login_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.sys_login_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sys_module; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.sys_module (
    id bigint NOT NULL,
    module_code character varying(64) NOT NULL,
    module_name character varying(64) NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: sys_module_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.sys_module ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.sys_module_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sys_role; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.sys_role (
    id bigint NOT NULL,
    role_code character varying(8) NOT NULL,
    role_name character varying(32) NOT NULL,
    description character varying(128),
    status smallint DEFAULT 1 NOT NULL,
    plant_code character varying(8) DEFAULT '*'::character varying NOT NULL,
    plant_name character varying(32) DEFAULT '全局'::character varying NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    data_scope character varying(16) DEFAULT 'OWN_PLANT'::character varying NOT NULL
);


--
-- Name: TABLE sys_role; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.sys_role IS '系统角色表（框架表，sys_ 前缀）';


--
-- Name: COLUMN sys_role.id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.id IS '主键自增ID';


--
-- Name: COLUMN sys_role.role_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.role_code IS '角色编码 R01=操作工 R02=检验员 R03=班组长 R04=质量工程师 R05=SQE供应商质量 R06=质量经理';


--
-- Name: COLUMN sys_role.role_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.role_name IS '角色名称';


--
-- Name: COLUMN sys_role.description; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.description IS '角色描述';


--
-- Name: COLUMN sys_role.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.status IS '状态 1=启用 0=禁用';


--
-- Name: COLUMN sys_role.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（角色表默认 * 全局）';


--
-- Name: COLUMN sys_role.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.plant_name IS '分公司名称';


--
-- Name: COLUMN sys_role.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.created_by IS '创建人';


--
-- Name: COLUMN sys_role.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.updated_by IS '更新人';


--
-- Name: COLUMN sys_role.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN sys_role.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.version IS '乐观锁版本号';


--
-- Name: COLUMN sys_role.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.created_at IS '创建时间';


--
-- Name: COLUMN sys_role.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_role.updated_at IS '更新时间';


--
-- Name: sys_role_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.sys_role ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.sys_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sys_role_permission; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.sys_role_permission (
    id bigint NOT NULL,
    role_code character varying(8) NOT NULL,
    module_code character varying(64) NOT NULL,
    action_code character varying(16) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    is_deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT sys_role_permission_action_code_check CHECK (((action_code)::text = ANY ((ARRAY['VIEW'::character varying, 'EDIT'::character varying, 'APPROVE'::character varying, 'EXPORT'::character varying])::text[])))
);


--
-- Name: sys_role_permission_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.sys_role_permission ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.sys_role_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sys_user; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.sys_user (
    id bigint NOT NULL,
    account character varying(64) NOT NULL,
    password_hash character varying(256) NOT NULL,
    real_name character varying(64) NOT NULL,
    role_code character varying(8) NOT NULL,
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    login_fail_count integer DEFAULT 0 NOT NULL,
    locked_until timestamp without time zone,
    last_login_at timestamp without time zone,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    auth_version integer DEFAULT 1 NOT NULL
);


--
-- Name: TABLE sys_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.sys_user IS '系统用户表（框架表，sys_ 前缀）';


--
-- Name: COLUMN sys_user.id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.id IS '主键自增ID';


--
-- Name: COLUMN sys_user.account; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.account IS '登录账号（全局唯一，跨分公司不重复）';


--
-- Name: COLUMN sys_user.password_hash; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.password_hash IS 'BCrypt 哈希密码（禁止存储明文，60 字符）';


--
-- Name: COLUMN sys_user.real_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.real_name IS '真实姓名';


--
-- Name: COLUMN sys_user.role_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.role_code IS '角色编码（关联 sys_role.role_code，单角色模式）';


--
-- Name: COLUMN sys_user.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';


--
-- Name: COLUMN sys_user.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.plant_name IS '分公司名称';


--
-- Name: COLUMN sys_user.status; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.status IS '状态 1=启用 0=禁用';


--
-- Name: COLUMN sys_user.login_fail_count; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.login_fail_count IS '连续登录失败次数（Redis 主控，DB 仅兜底记录）';


--
-- Name: COLUMN sys_user.locked_until; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.locked_until IS '锁定截止时间（NULL=未锁定，>now()=锁定中，由 Redis TTL 控制）';


--
-- Name: COLUMN sys_user.last_login_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.last_login_at IS '最后登录时间';


--
-- Name: COLUMN sys_user.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.created_by IS '创建人';


--
-- Name: COLUMN sys_user.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.updated_by IS '更新人';


--
-- Name: COLUMN sys_user.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN sys_user.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.version IS '乐观锁版本号';


--
-- Name: COLUMN sys_user.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.created_at IS '创建时间';


--
-- Name: COLUMN sys_user.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.sys_user.updated_at IS '更新时间';


--
-- Name: sys_user_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.sys_user ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.sys_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: trace_node; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.trace_node (
    id bigint NOT NULL,
    node_type character varying(24) NOT NULL,
    barcode character varying(128) NOT NULL,
    name character varying(128) NOT NULL,
    product_code character varying(64),
    material_code character varying(64),
    material_batch_no character varying(128),
    specification character varying(128),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT trace_node_check CHECK (((((node_type)::text = 'MATERIAL'::text) AND (material_code IS NOT NULL) AND (material_batch_no IS NOT NULL)) OR ((node_type)::text <> 'MATERIAL'::text))),
    CONSTRAINT trace_node_node_type_check CHECK (((node_type)::text = ANY ((ARRAY['FINISHED_GOOD'::character varying, 'SEMI_FINISHED'::character varying, 'MATERIAL'::character varying])::text[])))
);


--
-- Name: trace_node_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.trace_node ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.trace_node_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: trace_relation; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.trace_relation (
    id bigint NOT NULL,
    parent_node_id bigint NOT NULL,
    child_node_id bigint NOT NULL,
    quantity numeric(18,3),
    work_order_no character varying(64),
    process_name character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_trace_relation_not_self CHECK ((parent_node_id <> child_node_id))
);


--
-- Name: trace_relation_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.trace_relation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.trace_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: verification_record; Type: TABLE; Schema: qms; Owner: -
--

CREATE TABLE qms.verification_record (
    id bigint NOT NULL,
    exception_id bigint NOT NULL,
    verify_type character varying(32) NOT NULL,
    result character varying(16),
    verifier_id bigint,
    verifier_name character varying(64),
    verify_date date,
    evidence text,
    remark text,
    signature_user character varying(64),
    signature_time timestamp without time zone,
    signature_reason character varying(256),
    plant_code character varying(8) NOT NULL,
    plant_name character varying(32) NOT NULL,
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE verification_record; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON TABLE qms.verification_record IS '验证记录表（M2：异常整改效果的验证确认，验证人电子签名确认，闭环前置条件）';


--
-- Name: COLUMN verification_record.exception_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.exception_id IS '关联异常单ID（外键 exception_order.id）';


--
-- Name: COLUMN verification_record.verify_type; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.verify_type IS '验证方式：供应商自证/内部确认/连续N批';


--
-- Name: COLUMN verification_record.result; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.result IS '验证结果：通过/不通过';


--
-- Name: COLUMN verification_record.verifier_id; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.verifier_id IS '验证人ID（关联 sys_user.id）';


--
-- Name: COLUMN verification_record.verifier_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.verifier_name IS '验证人姓名（冗余，便于展示）';


--
-- Name: COLUMN verification_record.verify_date; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.verify_date IS '验证日期';


--
-- Name: COLUMN verification_record.evidence; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.evidence IS '验证证据（附件URL或描述文本）';


--
-- Name: COLUMN verification_record.remark; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.remark IS '备注';


--
-- Name: COLUMN verification_record.signature_user; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.signature_user IS '电子签名人';


--
-- Name: COLUMN verification_record.signature_time; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.signature_time IS '电子签名时间';


--
-- Name: COLUMN verification_record.signature_reason; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.signature_reason IS '电子签名原因';


--
-- Name: COLUMN verification_record.plant_code; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（继承自异常单）';


--
-- Name: COLUMN verification_record.plant_name; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.plant_name IS '分公司名称';


--
-- Name: COLUMN verification_record.created_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.created_by IS '创建人';


--
-- Name: COLUMN verification_record.updated_by; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.updated_by IS '更新人';


--
-- Name: COLUMN verification_record.is_deleted; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.is_deleted IS '软删除 0=正常 1=已删除';


--
-- Name: COLUMN verification_record.version; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.version IS '乐观锁版本号';


--
-- Name: COLUMN verification_record.created_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.created_at IS '创建时间';


--
-- Name: COLUMN verification_record.updated_at; Type: COMMENT; Schema: qms; Owner: -
--

COMMENT ON COLUMN qms.verification_record.updated_at IS '更新时间';


--
-- Name: verification_record_id_seq; Type: SEQUENCE; Schema: qms; Owner: -
--

ALTER TABLE qms.verification_record ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME qms.verification_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: spc_capability id; Type: DEFAULT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_capability ALTER COLUMN id SET DEFAULT nextval('qms.spc_capability_id_seq'::regclass);


--
-- Name: spc_control_limit id; Type: DEFAULT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_control_limit ALTER COLUMN id SET DEFAULT nextval('qms.spc_control_limit_id_seq'::regclass);


--
-- Name: spc_parameter id; Type: DEFAULT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_parameter ALTER COLUMN id SET DEFAULT nextval('qms.spc_parameter_id_seq'::regclass);


--
-- Name: spc_process id; Type: DEFAULT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_process ALTER COLUMN id SET DEFAULT nextval('qms.spc_process_id_seq'::regclass);


--
-- Name: spc_sample id; Type: DEFAULT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_sample ALTER COLUMN id SET DEFAULT nextval('qms.spc_sample_id_seq'::regclass);


--
-- Name: spc_subgroup id; Type: DEFAULT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_subgroup ALTER COLUMN id SET DEFAULT nextval('qms.spc_subgroup_id_seq'::regclass);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: escalation escalation_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.escalation
    ADD CONSTRAINT escalation_pkey PRIMARY KEY (id);


--
-- Name: exception_8d exception_8d_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.exception_8d
    ADD CONSTRAINT exception_8d_pkey PRIMARY KEY (id);


--
-- Name: exception_order exception_order_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.exception_order
    ADD CONSTRAINT exception_order_pkey PRIMARY KEY (id);


--
-- Name: fai_change_trigger fai_change_trigger_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.fai_change_trigger
    ADD CONSTRAINT fai_change_trigger_pkey PRIMARY KEY (id);


--
-- Name: fai_inspection_item fai_inspection_item_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.fai_inspection_item
    ADD CONSTRAINT fai_inspection_item_pkey PRIMARY KEY (id);


--
-- Name: fai_inspection_record fai_inspection_record_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.fai_inspection_record
    ADD CONSTRAINT fai_inspection_record_pkey PRIMARY KEY (id);


--
-- Name: fai_inspection_standard_item fai_inspection_standard_item_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.fai_inspection_standard_item
    ADD CONSTRAINT fai_inspection_standard_item_pkey PRIMARY KEY (id);


--
-- Name: fai_inspection_standard fai_inspection_standard_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.fai_inspection_standard
    ADD CONSTRAINT fai_inspection_standard_pkey PRIMARY KEY (id);


--
-- Name: fai_signature fai_signature_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.fai_signature
    ADD CONSTRAINT fai_signature_pkey PRIMARY KEY (id);


--
-- Name: finished_goods_inspection finished_goods_inspection_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.finished_goods_inspection
    ADD CONSTRAINT finished_goods_inspection_pkey PRIMARY KEY (id);


--
-- Name: improvement_action improvement_action_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.improvement_action
    ADD CONSTRAINT improvement_action_pkey PRIMARY KEY (id);


--
-- Name: material_inspection material_inspection_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.material_inspection
    ADD CONSTRAINT material_inspection_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: production_repair production_repair_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.production_repair
    ADD CONSTRAINT production_repair_pkey PRIMARY KEY (id);


--
-- Name: qms_migration_log qms_migration_log_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.qms_migration_log
    ADD CONSTRAINT qms_migration_log_pkey PRIMARY KEY (id);


--
-- Name: rectification_plan rectification_plan_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.rectification_plan
    ADD CONSTRAINT rectification_plan_pkey PRIMARY KEY (id);


--
-- Name: spc_capability spc_capability_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_capability
    ADD CONSTRAINT spc_capability_pkey PRIMARY KEY (id);


--
-- Name: spc_coefficient spc_coefficient_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_coefficient
    ADD CONSTRAINT spc_coefficient_pkey PRIMARY KEY (n);


--
-- Name: spc_control_limit spc_control_limit_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_control_limit
    ADD CONSTRAINT spc_control_limit_pkey PRIMARY KEY (id);


--
-- Name: spc_parameter spc_parameter_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_parameter
    ADD CONSTRAINT spc_parameter_pkey PRIMARY KEY (id);


--
-- Name: spc_process spc_process_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_process
    ADD CONSTRAINT spc_process_pkey PRIMARY KEY (id);


--
-- Name: spc_sample spc_sample_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_sample
    ADD CONSTRAINT spc_sample_pkey PRIMARY KEY (id);


--
-- Name: spc_subgroup spc_subgroup_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.spc_subgroup
    ADD CONSTRAINT spc_subgroup_pkey PRIMARY KEY (id);


--
-- Name: supplier supplier_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.supplier
    ADD CONSTRAINT supplier_pkey PRIMARY KEY (id);


--
-- Name: sys_login_log sys_login_log_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.sys_login_log
    ADD CONSTRAINT sys_login_log_pkey PRIMARY KEY (id);


--
-- Name: sys_module sys_module_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.sys_module
    ADD CONSTRAINT sys_module_pkey PRIMARY KEY (id);


--
-- Name: sys_role_permission sys_role_permission_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.sys_role_permission
    ADD CONSTRAINT sys_role_permission_pkey PRIMARY KEY (id);


--
-- Name: sys_role sys_role_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.sys_role
    ADD CONSTRAINT sys_role_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.sys_user
    ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);


--
-- Name: trace_node trace_node_barcode_key; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.trace_node
    ADD CONSTRAINT trace_node_barcode_key UNIQUE (barcode);


--
-- Name: trace_node trace_node_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.trace_node
    ADD CONSTRAINT trace_node_pkey PRIMARY KEY (id);


--
-- Name: trace_relation trace_relation_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.trace_relation
    ADD CONSTRAINT trace_relation_pkey PRIMARY KEY (id);


--
-- Name: exception_8d uq_exception_8d_exception_id; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.exception_8d
    ADD CONSTRAINT uq_exception_8d_exception_id UNIQUE (exception_id);


--
-- Name: trace_relation uq_trace_relation; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.trace_relation
    ADD CONSTRAINT uq_trace_relation UNIQUE (parent_node_id, child_node_id);


--
-- Name: verification_record verification_record_pkey; Type: CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.verification_record
    ADD CONSTRAINT verification_record_pkey PRIMARY KEY (id);


--
-- Name: idx_audit_operation; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_operation ON qms.audit_log USING btree (operation_type);


--
-- Name: idx_audit_operator; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_operator ON qms.audit_log USING btree (operator_id);


--
-- Name: idx_audit_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_plant ON qms.audit_log USING btree (plant_code);


--
-- Name: idx_audit_record; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_record ON qms.audit_log USING btree (record_id);


--
-- Name: idx_audit_table; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_table ON qms.audit_log USING btree (table_name);


--
-- Name: idx_audit_table_time; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_table_time ON qms.audit_log USING btree (table_name, operation_time DESC);


--
-- Name: idx_audit_time; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_audit_time ON qms.audit_log USING btree (operation_time DESC);


--
-- Name: idx_esc_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_esc_plant ON qms.escalation USING btree (plant_code);


--
-- Name: idx_esc_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_esc_status ON qms.escalation USING btree (status);


--
-- Name: idx_esc_supplier; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_esc_supplier ON qms.escalation USING btree (supplier_id);


--
-- Name: idx_esc_supplier_material; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_esc_supplier_material ON qms.escalation USING btree (plant_code, supplier_code, material_code, status) WHERE (is_deleted = 0);


--
-- Name: idx_escalation_stage; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_escalation_stage ON qms.escalation USING btree (plant_code, status, process_stage, created_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_exception_8d_exception_id; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exception_8d_exception_id ON qms.exception_8d USING btree (exception_id);


--
-- Name: idx_exception_8d_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exception_8d_plant ON qms.exception_8d USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_exo_capa_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_capa_status ON qms.exception_order USING btree (capa_status);


--
-- Name: idx_exo_deadline; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_deadline ON qms.exception_order USING btree (deadline) WHERE ((status)::text <> '已闭环'::text);


--
-- Name: idx_exo_fingerprint; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_fingerprint ON qms.exception_order USING btree (plant_code, problem_fingerprint, created_at DESC) WHERE (is_deleted = 0);


--
-- Name: idx_exo_material_source; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_material_source ON qms.exception_order USING btree (source_type, source_id) WHERE ((is_deleted = 0) AND ((source_type)::text = '来料不良'::text) AND (source_id IS NOT NULL));


--
-- Name: idx_exo_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_plant ON qms.exception_order USING btree (plant_code);


--
-- Name: idx_exo_process_type; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_process_type ON qms.exception_order USING btree (process_type) WHERE (is_deleted = 0);


--
-- Name: idx_exo_reviewer; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_reviewer ON qms.exception_order USING btree (reviewer_id);


--
-- Name: idx_exo_sev; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_sev ON qms.exception_order USING btree (severity);


--
-- Name: idx_exo_source; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_source ON qms.exception_order USING btree (source_type);


--
-- Name: idx_exo_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_status ON qms.exception_order USING btree (status);


--
-- Name: idx_exo_sup; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_exo_sup ON qms.exception_order USING btree (supplier_id);


--
-- Name: idx_fai_ct_batch; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_ct_batch ON qms.fai_change_trigger USING btree (batch_no) WHERE (is_deleted = 0);


--
-- Name: idx_fai_ct_material; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_ct_material ON qms.fai_change_trigger USING btree (material_code) WHERE (is_deleted = 0);


--
-- Name: idx_fai_ct_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_ct_plant ON qms.fai_change_trigger USING btree (plant_code);


--
-- Name: idx_fai_ct_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_ct_status ON qms.fai_change_trigger USING btree (status) WHERE (is_deleted = 0);


--
-- Name: idx_fai_ct_trigger_type; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_ct_trigger_type ON qms.fai_change_trigger USING btree (trigger_type) WHERE (is_deleted = 0);


--
-- Name: idx_fai_item_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_item_code ON qms.fai_inspection_item USING btree (param_code) WHERE (is_deleted = 0);


--
-- Name: idx_fai_item_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_item_plant ON qms.fai_inspection_item USING btree (plant_code);


--
-- Name: idx_fai_item_record; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_item_record ON qms.fai_inspection_item USING btree (fai_record_id) WHERE (is_deleted = 0);


--
-- Name: idx_fai_item_spc_parameter; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_item_spc_parameter ON qms.fai_inspection_item USING btree (spc_parameter_id) WHERE ((spc_parameter_id IS NOT NULL) AND (is_deleted = 0));


--
-- Name: idx_fai_rec_batch; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_rec_batch ON qms.fai_inspection_record USING btree (batch_no) WHERE (is_deleted = 0);


--
-- Name: idx_fai_rec_material; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_rec_material ON qms.fai_inspection_record USING btree (material_code) WHERE (is_deleted = 0);


--
-- Name: idx_fai_rec_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_rec_plant ON qms.fai_inspection_record USING btree (plant_code);


--
-- Name: idx_fai_rec_result; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_rec_result ON qms.fai_inspection_record USING btree (inspection_result) WHERE (is_deleted = 0);


--
-- Name: idx_fai_rec_trigger; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_rec_trigger ON qms.fai_inspection_record USING btree (change_trigger_id) WHERE (is_deleted = 0);


--
-- Name: idx_fai_record_process_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_record_process_code ON qms.fai_inspection_record USING btree (process_code) WHERE (is_deleted = 0);


--
-- Name: idx_fai_sig_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_sig_plant ON qms.fai_signature USING btree (plant_code);


--
-- Name: idx_fai_sig_record; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_sig_record ON qms.fai_signature USING btree (fai_record_id) WHERE (is_deleted = 0);


--
-- Name: idx_fai_sig_type; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_sig_type ON qms.fai_signature USING btree (sign_type) WHERE (is_deleted = 0);


--
-- Name: idx_fai_standard_item_spc_parameter; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_standard_item_spc_parameter ON qms.fai_inspection_standard_item USING btree (spc_parameter_id) WHERE ((spc_parameter_id IS NOT NULL) AND (is_deleted = 0));


--
-- Name: idx_fai_std_active; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_std_active ON qms.fai_inspection_standard USING btree (is_active) WHERE (is_deleted = 0);


--
-- Name: idx_fai_std_material; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_std_material ON qms.fai_inspection_standard USING btree (material_code) WHERE (is_deleted = 0);


--
-- Name: idx_fai_std_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_std_plant ON qms.fai_inspection_standard USING btree (plant_code);


--
-- Name: idx_fai_std_process; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_std_process ON qms.fai_inspection_standard USING btree (process_name) WHERE (is_deleted = 0);


--
-- Name: idx_fai_stdi_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_stdi_plant ON qms.fai_inspection_standard_item USING btree (plant_code);


--
-- Name: idx_fai_stdi_standard; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fai_stdi_standard ON qms.fai_inspection_standard_item USING btree (standard_id) WHERE (is_deleted = 0);


--
-- Name: idx_fgi_material; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fgi_material ON qms.finished_goods_inspection USING btree (material_code);


--
-- Name: idx_fgi_order; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fgi_order ON qms.finished_goods_inspection USING btree (production_order_no);


--
-- Name: idx_fgi_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fgi_plant ON qms.finished_goods_inspection USING btree (plant_code);


--
-- Name: idx_fgi_qc; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fgi_qc ON qms.finished_goods_inspection USING btree (qc_review);


--
-- Name: idx_fgi_result; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fgi_result ON qms.finished_goods_inspection USING btree (inspection_result);


--
-- Name: idx_fgi_sn; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_fgi_sn ON qms.finished_goods_inspection USING btree (prod_batch_or_sn);


--
-- Name: idx_ima_exception; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_ima_exception ON qms.improvement_action USING btree (exception_id);


--
-- Name: idx_ima_owner; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_ima_owner ON qms.improvement_action USING btree (owner_id);


--
-- Name: idx_ima_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_ima_plant ON qms.improvement_action USING btree (plant_code);


--
-- Name: idx_ima_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_ima_status ON qms.improvement_action USING btree (status);


--
-- Name: idx_ima_type; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_ima_type ON qms.improvement_action USING btree (action_type);


--
-- Name: idx_mi_batch; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_batch ON qms.material_inspection USING btree (material_batch_no);


--
-- Name: idx_mi_date; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_date ON qms.material_inspection USING btree (inspection_date DESC);


--
-- Name: idx_mi_material; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_material ON qms.material_inspection USING btree (material_code);


--
-- Name: idx_mi_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_plant ON qms.material_inspection USING btree (plant_code);


--
-- Name: idx_mi_po; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_po ON qms.material_inspection USING btree (purchase_order);


--
-- Name: idx_mi_req; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_req ON qms.material_inspection USING btree (inspection_request_no);


--
-- Name: idx_mi_result; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_result ON qms.material_inspection USING btree (inspection_result);


--
-- Name: idx_mi_review; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_review ON qms.material_inspection USING btree (review_status);


--
-- Name: idx_mi_supplier; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_mi_supplier ON qms.material_inspection USING btree (supplier_code);


--
-- Name: idx_notification_business; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_notification_business ON qms.notification USING btree (business_type, business_id) WHERE (is_deleted = 0);


--
-- Name: idx_notification_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_notification_plant ON qms.notification USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_notification_user_read; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_notification_user_read ON qms.notification USING btree (user_id, is_read) WHERE (is_deleted = 0);


--
-- Name: idx_pr_defect; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_defect ON qms.production_repair USING btree (defect_code);


--
-- Name: idx_pr_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_plant ON qms.production_repair USING btree (plant_code);


--
-- Name: idx_pr_plant_process; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_plant_process ON qms.production_repair USING btree (plant_code, process);


--
-- Name: idx_pr_plant_psn; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_plant_psn ON qms.production_repair USING btree (plant_code, product_batch_or_sn);


--
-- Name: idx_pr_plant_workorder; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_plant_workorder ON qms.production_repair USING btree (plant_code, work_order_no);


--
-- Name: idx_pr_process; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_process ON qms.production_repair USING btree (process);


--
-- Name: idx_pr_psn; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_psn ON qms.production_repair USING btree (product_batch_or_sn);


--
-- Name: idx_pr_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_status ON qms.production_repair USING btree (repair_status);


--
-- Name: idx_pr_work_order; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_pr_work_order ON qms.production_repair USING btree (work_order_no);


--
-- Name: idx_rp_exception; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_rp_exception ON qms.rectification_plan USING btree (exception_id);


--
-- Name: idx_rp_owner; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_rp_owner ON qms.rectification_plan USING btree (owner_id);


--
-- Name: idx_rp_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_rp_plant ON qms.rectification_plan USING btree (plant_code);


--
-- Name: idx_rp_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_rp_status ON qms.rectification_plan USING btree (status);


--
-- Name: idx_sll_account; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sll_account ON qms.sys_login_log USING btree (account);


--
-- Name: idx_sll_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sll_plant ON qms.sys_login_log USING btree (plant_code);


--
-- Name: idx_sll_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sll_status ON qms.sys_login_log USING btree (login_status);


--
-- Name: idx_sll_time; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sll_time ON qms.sys_login_log USING btree (login_time DESC);


--
-- Name: idx_sll_user_id; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sll_user_id ON qms.sys_login_log USING btree (user_id);


--
-- Name: idx_spc_cap_param; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_cap_param ON qms.spc_capability USING btree (param_id);


--
-- Name: idx_spc_cap_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_cap_plant ON qms.spc_capability USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_spc_cl_param; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_cl_param ON qms.spc_control_limit USING btree (param_id);


--
-- Name: idx_spc_cl_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_cl_plant ON qms.spc_control_limit USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_spc_parameter_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_parameter_code ON qms.spc_parameter USING btree (param_code);


--
-- Name: idx_spc_parameter_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_parameter_plant ON qms.spc_parameter USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_spc_parameter_process; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_parameter_process ON qms.spc_parameter USING btree (process_id);


--
-- Name: idx_spc_process_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_process_code ON qms.spc_process USING btree (process_code);


--
-- Name: idx_spc_process_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_process_plant ON qms.spc_process USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_spc_sample_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_sample_plant ON qms.spc_sample USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_spc_sample_subgroup; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_sample_subgroup ON qms.spc_sample USING btree (subgroup_id);


--
-- Name: idx_spc_subgroup_fai; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_subgroup_fai ON qms.spc_subgroup USING btree (fai_record_id);


--
-- Name: idx_spc_subgroup_no; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_subgroup_no ON qms.spc_subgroup USING btree (subgroup_no);


--
-- Name: idx_spc_subgroup_param; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_subgroup_param ON qms.spc_subgroup USING btree (param_id);


--
-- Name: idx_spc_subgroup_pending; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_subgroup_pending ON qms.spc_subgroup USING btree (plant_code, subgroup_status, work_order_no, batch_no, process_code, param_id) WHERE (is_deleted = 0);


--
-- Name: idx_spc_subgroup_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_subgroup_plant ON qms.spc_subgroup USING btree (plant_code) WHERE (is_deleted = 0);


--
-- Name: idx_spc_subgroup_work_order; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_spc_subgroup_work_order ON qms.spc_subgroup USING btree (work_order_no) WHERE (is_deleted = 0);


--
-- Name: idx_sr_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sr_plant ON qms.sys_role USING btree (plant_code);


--
-- Name: idx_sr_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sr_status ON qms.sys_role USING btree (status) WHERE (is_deleted = 0);


--
-- Name: idx_su_locked; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_su_locked ON qms.sys_user USING btree (locked_until) WHERE (locked_until IS NOT NULL);


--
-- Name: idx_su_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_su_plant ON qms.sys_user USING btree (plant_code);


--
-- Name: idx_su_role; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_su_role ON qms.sys_user USING btree (role_code);


--
-- Name: idx_su_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_su_status ON qms.sys_user USING btree (status) WHERE (is_deleted = 0);


--
-- Name: idx_sup_name; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sup_name ON qms.supplier USING btree (supplier_name);


--
-- Name: idx_sup_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sup_plant ON qms.supplier USING btree (plant_code);


--
-- Name: idx_sup_risk; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sup_risk ON qms.supplier USING btree (risk_level);


--
-- Name: idx_sup_status; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sup_status ON qms.supplier USING btree (status) WHERE (is_deleted = 0);


--
-- Name: idx_sys_role_permission_role; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_sys_role_permission_role ON qms.sys_role_permission USING btree (role_code) WHERE (is_deleted = 0);


--
-- Name: idx_trace_node_batch; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_trace_node_batch ON qms.trace_node USING btree (material_batch_no) WHERE ((node_type)::text = 'MATERIAL'::text);


--
-- Name: idx_trace_relation_child; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_trace_relation_child ON qms.trace_relation USING btree (child_node_id);


--
-- Name: idx_trace_relation_parent; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_trace_relation_parent ON qms.trace_relation USING btree (parent_node_id);


--
-- Name: idx_vr_exception; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_vr_exception ON qms.verification_record USING btree (exception_id);


--
-- Name: idx_vr_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_vr_plant ON qms.verification_record USING btree (plant_code);


--
-- Name: idx_vr_result; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_vr_result ON qms.verification_record USING btree (result);


--
-- Name: idx_vr_type; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_vr_type ON qms.verification_record USING btree (verify_type);


--
-- Name: idx_vr_verifier; Type: INDEX; Schema: qms; Owner: -
--

CREATE INDEX idx_vr_verifier ON qms.verification_record USING btree (verifier_id);


--
-- Name: uq_exo_no; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_exo_no ON qms.exception_order USING btree (exception_no) WHERE (is_deleted = 0);


--
-- Name: uq_fai_no; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_fai_no ON qms.fai_inspection_record USING btree (fai_no) WHERE (is_deleted = 0);


--
-- Name: uq_fgi_report_no; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_fgi_report_no ON qms.finished_goods_inspection USING btree (report_no) WHERE (is_deleted = 0);


--
-- Name: uq_mi_record_no; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_mi_record_no ON qms.material_inspection USING btree (record_no) WHERE (is_deleted = 0);


--
-- Name: uq_pr_repair_no; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_pr_repair_no ON qms.production_repair USING btree (repair_no) WHERE (is_deleted = 0);


--
-- Name: uq_spc_parameter_code_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_spc_parameter_code_plant ON qms.spc_parameter USING btree (plant_code, param_code) WHERE (is_deleted = 0);


--
-- Name: uq_spc_process_code_plant; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_spc_process_code_plant ON qms.spc_process USING btree (plant_code, process_code) WHERE (is_deleted = 0);


--
-- Name: uq_spc_subgroup_fai_param; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_spc_subgroup_fai_param ON qms.spc_subgroup USING btree (fai_record_id, param_id) WHERE ((fai_record_id IS NOT NULL) AND (is_deleted = 0) AND ((source_type)::text = '首件自动导入'::text));


--
-- Name: uq_sr_role_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_sr_role_code ON qms.sys_role USING btree (role_code) WHERE (is_deleted = 0);


--
-- Name: uq_su_account; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_su_account ON qms.sys_user USING btree (account) WHERE (is_deleted = 0);


--
-- Name: uq_sup_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_sup_code ON qms.supplier USING btree (supplier_code) WHERE (is_deleted = 0);


--
-- Name: uq_sys_module_code; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_sys_module_code ON qms.sys_module USING btree (module_code) WHERE (is_deleted = 0);


--
-- Name: uq_sys_role_permission; Type: INDEX; Schema: qms; Owner: -
--

CREATE UNIQUE INDEX uq_sys_role_permission ON qms.sys_role_permission USING btree (role_code, module_code, action_code) WHERE (is_deleted = 0);


--
-- Name: trace_relation trace_relation_child_node_id_fkey; Type: FK CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.trace_relation
    ADD CONSTRAINT trace_relation_child_node_id_fkey FOREIGN KEY (child_node_id) REFERENCES qms.trace_node(id) ON DELETE CASCADE;


--
-- Name: trace_relation trace_relation_parent_node_id_fkey; Type: FK CONSTRAINT; Schema: qms; Owner: -
--

ALTER TABLE ONLY qms.trace_relation
    ADD CONSTRAINT trace_relation_parent_node_id_fkey FOREIGN KEY (parent_node_id) REFERENCES qms.trace_node(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
