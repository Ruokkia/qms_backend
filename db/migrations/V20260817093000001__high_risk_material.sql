-- 高风险物料清单（固化数据）
CREATE TABLE qms.high_risk_material (
    id bigserial PRIMARY KEY,
    material_code character varying(64) NOT NULL,
    material_name character varying(128) NOT NULL,
    risk_level character varying(16) NOT NULL DEFAULT '高',
    extra_requirement text,
    remark text
);

CREATE INDEX IF NOT EXISTS idx_high_risk_material_code ON qms.high_risk_material (material_code);

-- 供应商-高风险物料关联
CREATE TABLE qms.supplier_high_risk_material (
    id bigserial PRIMARY KEY,
    supplier_id bigint NOT NULL,
    supplier_code character varying(64),
    supplier_name character varying(128),
    material_id bigint NOT NULL,
    material_code character varying(64),
    material_name character varying(128),
    extra_requirement text,
    plant_code character varying(16),
    plant_name character varying(32),
    created_by character varying(64),
    is_deleted smallint NOT NULL DEFAULT 0,
    version integer NOT NULL DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_supplier_high_risk_supplier ON qms.supplier_high_risk_material (supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_high_risk_deleted ON qms.supplier_high_risk_material (is_deleted);

-- 审核记录增加高风险物料额外资料要求字段
ALTER TABLE qms.supplier_audit_record ADD COLUMN IF NOT EXISTS extra_requirement text;

-- 高风险物料种子数据（GMP 关键物料）
INSERT INTO qms.high_risk_material (material_code, material_name, risk_level, extra_requirement, remark) VALUES
('PCBA', 'PCBA 电路板组件', '高', '提供 IPC-A-610 验收报告、关键元器件 BOM 及材质证明、RoHS/REACH 合规声明、焊接工艺过程能力(CPK≥1.33)研究', '直接影响电气安全与可靠性'),
('NEEDLE', '采样针组', '高', '提供生物相容性(ISO 10993)报告、无菌验证报告、关键尺寸全检记录、过程能力研究', '与样本直接接触，影响检测结果准确性'),
('WIRE', '线束', '高', '提供线材 UL/CCC 认证、拉力与导通测试报告、RoHS 合规声明', '影响整机安全与信号完整性'),
('MOLD', '注塑外壳', '中', '提供材质证明、关键尺寸报告、阻燃等级(UL94)测试报告', '结构件');
