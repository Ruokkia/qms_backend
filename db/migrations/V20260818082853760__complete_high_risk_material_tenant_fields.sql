-- 高风险物料主数据由分公司数据隔离插件自动附加 plant_code 条件，补齐租户字段并初始化两地数据。
ALTER TABLE qms.high_risk_material ADD COLUMN IF NOT EXISTS plant_code varchar(16);
ALTER TABLE qms.high_risk_material ADD COLUMN IF NOT EXISTS plant_name varchar(32);
CREATE UNIQUE INDEX IF NOT EXISTS uq_high_risk_material_plant_code
    ON qms.high_risk_material (material_code, plant_code);

INSERT INTO qms.high_risk_material (material_code, material_name, risk_level, extra_requirement, remark, plant_code, plant_name) VALUES
('PCBA', 'PCBA 电路板组件', '高', '提供 IPC-A-610 验收报告、关键元器件 BOM 及材质证明、RoHS/REACH 合规声明、焊接工艺过程能力(CPK≥1.33)研究', '直接影响电气安全与可靠性', 'SZ', '深圳'),
('NEEDLE', '采样针组', '高', '提供生物相容性(ISO 10993)报告、无菌验证报告、关键尺寸全检记录、过程能力研究', '与样本直接接触，影响检测结果准确性', 'SZ', '深圳'),
('WIRE', '线束', '高', '提供线材 UL/CCC 认证、拉力与导通测试报告、RoHS 合规声明', '影响整机安全与信号完整性', 'SZ', '深圳'),
('MOLD', '注塑外壳', '中', '提供材质证明、关键尺寸报告、阻燃等级(UL94)测试报告', '结构件', 'SZ', '深圳'),
('PCBA', 'PCBA 电路板组件', '高', '提供 IPC-A-610 验收报告、关键元器件 BOM 及材质证明、RoHS/REACH 合规声明、焊接工艺过程能力(CPK≥1.33)研究', '直接影响电气安全与可靠性', 'MZ', '梅州'),
('NEEDLE', '采样针组', '高', '提供生物相容性(ISO 10993)报告、无菌验证报告、关键尺寸全检记录、过程能力研究', '与样本直接接触，影响检测结果准确性', 'MZ', '梅州'),
('WIRE', '线束', '高', '提供线材 UL/CCC 认证、拉力与导通测试报告、RoHS 合规声明', '影响整机安全与信号完整性', 'MZ', '梅州'),
('MOLD', '注塑外壳', '中', '提供材质证明、关键尺寸报告、阻燃等级(UL94)测试报告', '结构件', 'MZ', '梅州')
ON CONFLICT (material_code, plant_code) DO NOTHING;
