-- M1-3 关键物料绑定清单表（追溯关系桥）。
-- 每条记录 = "产品 → 子项"的一对绑定关系。
-- category 标识子项类型：半成品 → 指向 finished_goods_inspection，物料 → 指向 material_inspection。
-- 自然键唯一索引 uq_cmb_nat_key 一并在此创建（V20260729120000001 的守卫已失效，不会重跑）。

CREATE TABLE qms.critical_material_binding (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category character varying(50),
    work_order_no character varying(100),
    product_barcode character varying(200),
    product_material_no character varying(100),
    product_name character varying(200),
    work_order_qty numeric(20,4),
    material_barcode character varying(200),
    material_code character varying(100),
    material_name character varying(200),
    spec_model character varying(200),
    scanner character varying(100),
    scan_time timestamp without time zone,
    process_code character varying(100),
    process_name character varying(100),
    is_active character varying(10) DEFAULT '是'::character varying,
    deactivate_operator character varying(100),
    deactivate_time timestamp without time zone,
    remark text,
    plant_code character varying(50) NOT NULL,
    plant_name character varying(100) NOT NULL,
    created_by character varying(50),
    updated_by character varying(50),
    is_deleted smallint DEFAULT 0 NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

-- 按产品条码查询（向下展开）
CREATE INDEX idx_cmb_product ON qms.critical_material_binding USING btree (plant_code, product_barcode) WHERE (is_deleted = 0);

-- 按物料条码查询（向上追溯）
CREATE INDEX idx_cmb_material ON qms.critical_material_binding USING btree (plant_code, material_barcode) WHERE (is_deleted = 0);

-- 自然键唯一索引（V20260729120000001 因 IF to_regclass 守卫在建表前执行而未创建，此处补建）
CREATE UNIQUE INDEX uq_cmb_nat_key ON qms.critical_material_binding USING btree (work_order_no, product_barcode, material_barcode, process_code) WHERE (is_deleted = 0);
