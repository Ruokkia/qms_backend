TRUNCATE qms.trace_relation, qms.trace_node RESTART IDENTITY;

INSERT INTO qms.trace_node (node_type, barcode, name, product_code, material_code, material_batch_no, specification) VALUES
('FINISHED_GOOD','FG-A100','自动进样盘','80.01.030006',NULL,NULL,'JY25'),
('SEMI_FINISHED','SF-A110','注塑组件','30.250.01913',NULL,NULL,'泵管接头组件'),
('SEMI_FINISHED','SF-A120','流路组件','30.250.01914',NULL,NULL,'流路装配'),
('MATERIAL','MAT-A01-LOT-20260721','医用塑胶粒',NULL,'10.09.200119','LOT-20260721','5kg/袋'),
('MATERIAL','MAT-A02-LOT-20260721','密封圈',NULL,'50.01.002001','LOT-20260721','φ12'),
('MATERIAL','MAT-A03-LOT-20260718','快速接头',NULL,'30.109.23161','LOT-20260718','KG2.0300'),
('FINISHED_GOOD','FG-B200','电解质分析仪','80.01.024004',NULL,NULL,'K-Lite8G'),
('SEMI_FINISHED','SF-B210','泵管组件','30.250.01726',NULL,NULL,'注塑版'),
('MATERIAL','MAT-B01-LOT-20260721','硅胶管',NULL,'20.03.010018','LOT-20260721','2m/卷'),
('MATERIAL','MAT-B02-LOT-20260719','不锈钢卡箍',NULL,'15.02.001008','LOT-20260719','φ8'),
('SEMI_FINISHED','SF-C310','共用模块','30.250.01799',NULL,NULL,'标准模块'),
('FINISHED_GOOD','FG-C300','备用成品','80.01.099001',NULL,NULL,'验证样机');

INSERT INTO qms.trace_relation (parent_node_id, child_node_id, quantity, work_order_no, process_name)
SELECT p.id, c.id, x.qty, x.work_order, x.process FROM (VALUES
('FG-A100','SF-A110',1.000,'WO-A100','总装'),
('FG-A100','SF-A120',1.000,'WO-A100','总装'),
('SF-A110','MAT-A01-LOT-20260721',2.000,'WO-A110','注塑'),
('SF-A110','MAT-A02-LOT-20260721',4.000,'WO-A110','注塑'),
('SF-A120','MAT-A03-LOT-20260718',2.000,'WO-A120','流路装配'),
('SF-A120','SF-C310',1.000,'WO-A120','流路装配'),
('FG-C300','SF-C310',1.000,'WO-C310','总装'),
('FG-B200','SF-B210',1.000,'WO-B200','总装'),
('SF-B210','MAT-B01-LOT-20260721',1.000,'WO-B210','组件装配'),
('SF-B210','MAT-B02-LOT-20260719',2.000,'WO-B210','组件装配')
) AS x(parent_barcode, child_barcode, qty, work_order, process)
JOIN qms.trace_node p ON p.barcode = x.parent_barcode
JOIN qms.trace_node c ON c.barcode = x.child_barcode;
