-- 供应商资质证照表
CREATE SEQUENCE IF NOT EXISTS qms.supplier_qualification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE qms.supplier_qualification (
    id bigint NOT NULL DEFAULT nextval('qms.supplier_qualification_id_seq'::regclass),
    supplier_id bigint NOT NULL,
    supplier_code character varying(64),
    supplier_name character varying(128),
    cert_type character varying(64) NOT NULL,
    cert_no character varying(128),
    issuer character varying(128),
    issue_date date,
    expire_date date,
    long_term smallint NOT NULL DEFAULT 0,
    file_urls text,
    remark text,
    plant_code character varying(16),
    plant_name character varying(32),
    created_by character varying(64),
    updated_by character varying(64),
    is_deleted smallint NOT NULL DEFAULT 0,
    version integer NOT NULL DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT supplier_qualification_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_qualification_supplier ON qms.supplier_qualification (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_qualification_expire ON qms.supplier_qualification (expire_date);
CREATE INDEX IF NOT EXISTS idx_supplier_qualification_plant ON qms.supplier_qualification (plant_code);
CREATE INDEX IF NOT EXISTS idx_supplier_qualification_deleted ON qms.supplier_qualification (is_deleted);
