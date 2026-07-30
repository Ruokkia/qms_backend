-- 将应用层防重下沉至数据库，防止并发请求在“先查后写”窗口内写入重复数据。
CREATE UNIQUE INDEX IF NOT EXISTS uq_finished_goods_report_no_active
    ON qms.finished_goods_inspection (plant_code, report_no)
    WHERE is_deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_relation_binding
    ON qms.trace_relation (plant_code, parent_node_id, child_node_id);
