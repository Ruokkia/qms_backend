-- 新建 8D 步骤留痕表，并记录每一步保存/推进的操作快照，支撑 8D 流程审计追溯。
-- 同时补充异常整改状态机所需的状态约束注释（不改变既有列结构）。

CREATE TABLE IF NOT EXISTS qms.exception_8d_step_log (
    id            bigint NOT NULL,
    exception_id  bigint NOT NULL,
    step          character varying(4) NOT NULL,
    step_content  text,
    operation     character varying(16) NOT NULL,
    operator      character varying(64),
    operated_at   timestamp without time zone DEFAULT now() NOT NULL,
    plant_code    character varying(8) NOT NULL,
    plant_name    character varying(32),
    is_deleted    smallint DEFAULT 0 NOT NULL,
    version       integer DEFAULT 1 NOT NULL,
    created_at    timestamp without time zone DEFAULT now() NOT NULL,
    updated_at    timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_exception_8d_step_log PRIMARY KEY (id)
);

COMMENT ON TABLE qms.exception_8d_step_log IS '8D 步骤留痕表（M2：记录每一步保存/推进的操作快照，支撑审计追溯）';

COMMENT ON COLUMN qms.exception_8d_step_log.exception_id IS '关联异常单ID（外键 exception_order.id）';
COMMENT ON COLUMN qms.exception_8d_step_log.step IS '操作步骤：D1-D8';
COMMENT ON COLUMN qms.exception_8d_step_log.step_content IS '该步骤填写内容快照';
COMMENT ON COLUMN qms.exception_8d_step_log.operation IS '操作类型：SAVE（保存内容）/ NEXT_STEP（推进到下一步）';
COMMENT ON COLUMN qms.exception_8d_step_log.operator IS '操作人（真实姓名）';
COMMENT ON COLUMN qms.exception_8d_step_log.operated_at IS '操作时间';
COMMENT ON COLUMN qms.exception_8d_step_log.plant_code IS '分公司编码 SZ=深圳 MZ=梅州（数据隔离维度）';

-- 序列（若基线未创建）
CREATE SEQUENCE IF NOT EXISTS qms.exception_8d_step_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE qms.exception_8d_step_log ALTER COLUMN id
    SET DEFAULT nextval('qms.exception_8d_step_log_id_seq');

-- 查询历史索引：按异常单排序
CREATE INDEX IF NOT EXISTS idx_8d_step_log_exception_id
    ON qms.exception_8d_step_log (exception_id, operated_at);

-- 状态机说明（仅注释，不改变列）：
-- exception_order.status 合法流转：待整改 → 整改中 → 待验证 → 已闭环
-- exception_order.capa_status 合法流转：待发起 → 进行中 → 已完成（仅闭环时置已完成）
