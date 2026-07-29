package com.kangli.qms.service.trace;

import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

/** Graph-based incoming-material traceability service. */
@Service
public class IncomingTraceService {
    private final JdbcTemplate jdbc;
    public IncomingTraceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> tree(String rootBarcode, String direction) {
        if (rootBarcode == null || rootBarcode.trim().isEmpty()) throw new IllegalArgumentException("追溯条码或批次不能为空");
        if ("BATCH_IMPACT".equals(direction)) return batchImpact(rootBarcode.trim());
        Map<String, Object> root = nodeByBarcode(rootBarcode.trim());
        if (root == null) throw new IllegalArgumentException("未找到追溯节点：" + rootBarcode);
        Set<Long> visited = new LinkedHashSet<>();
        if ("FULL".equals(direction)) {
            List<Map<String,Object>> upward = expand(((Number) root.get("id")).longValue(), true, new LinkedHashSet<>(), visited);
            root.put("upward", upward);
            root.put("children", expand(((Number) root.get("id")).longValue(), false, new LinkedHashSet<>(), visited));
        } else {
            boolean up = "UP".equals(direction);
            root.put("children", expand(((Number) root.get("id")).longValue(), up, new LinkedHashSet<>(), visited));
        }
        return result(root, visited.size(), direction, 0);
    }

    public Map<String, Object> node(long id) {
        Map<String, Object> node = jdbc.query("select * from qms.trace_node where id=?", rs -> rs.next() ? mapNode(rs) : null, id);
        if (node == null) throw new IllegalArgumentException("节点不存在");
        node.put("parents", linked(id, true));
        node.put("children", linked(id, false));
        return node;
    }

    /** Data-review endpoints for the traceability acceptance screen. */
    public List<Map<String, Object>> nodes() {
        return jdbc.query("select * from qms.trace_node order by id", (rs, i) -> mapNode(rs));
    }

    public List<Map<String, Object>> relations() {
        String sql = "select r.id,r.parent_node_id,r.child_node_id,r.quantity,r.work_order_no,r.process_name,"
                + "p.barcode parent_barcode,p.name parent_name,c.barcode child_barcode,c.name child_name "
                + "from qms.trace_relation r join qms.trace_node p on p.id=r.parent_node_id "
                + "join qms.trace_node c on c.id=r.child_node_id order by r.id";
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("parentNodeId", rs.getLong("parent_node_id"));
            row.put("parentBarcode", rs.getString("parent_barcode"));
            row.put("parentName", rs.getString("parent_name"));
            row.put("childNodeId", rs.getLong("child_node_id"));
            row.put("childBarcode", rs.getString("child_barcode"));
            row.put("childName", rs.getString("child_name"));
            row.put("quantity", rs.getBigDecimal("quantity"));
            row.put("workOrderNo", rs.getString("work_order_no"));
            row.put("processName", rs.getString("process_name"));
            return row;
        });
    }

    public long createNode(Map<String, Object> body) {
        String type = text(body, "nodeType"); String barcode = text(body, "barcode"); String name = text(body, "name");
        if ("FINISHED_GOOD".equals(type) || "MATERIAL".equals(type)) {
            throw new IllegalArgumentException("成品和来料追溯节点由检验主数据自动同步，不能手工创建");
        }
        if (!Arrays.asList("FINISHED_GOOD", "SEMI_FINISHED", "MATERIAL").contains(type) || barcode.isEmpty() || name.isEmpty()) throw new IllegalArgumentException("请填写类型、条码和名称");
        if ("MATERIAL".equals(type) && (text(body,"materialCode").isEmpty() || text(body,"materialBatchNo").isEmpty())) throw new IllegalArgumentException("物料必须填写物料代码和物料批号");
        jdbc.update("insert into qms.trace_node(node_type,barcode,name,product_code,material_code,material_batch_no,specification) values(?,?,?,?,?,?,?)", type, barcode, name, nullable(body,"productCode"), nullable(body,"materialCode"), nullable(body,"materialBatchNo"), nullable(body,"specification"));
        return jdbc.queryForObject("select id from qms.trace_node where barcode=?", Long.class, barcode);
    }

    public long createRelation(Map<String, Object> body) {
        long parent = number(body,"parentNodeId"), child = number(body,"childNodeId");
        if (parent == child || node(parent) == null || node(child) == null) throw new IllegalArgumentException("请选择两个不同的有效节点");
        if (reachable(child, parent, new HashSet<Long>())) throw new IllegalArgumentException("该关系会形成追溯环路");
        jdbc.update("insert into qms.trace_relation(parent_node_id,child_node_id,quantity,work_order_no,process_name) values(?,?,?,?,?)", parent, child, decimal(body,"quantity"), nullable(body,"workOrderNo"), nullable(body,"processName"));
        return jdbc.queryForObject("select currval(pg_get_serial_sequence('qms.trace_relation','id'))", Long.class);
    }

    public Map<String,Object> summary() {
        Map<String,Object> s = new LinkedHashMap<>();
        Map<String,Object> inspection = jdbc.queryForMap(
                "select count(*) as total_batches, "
                        + "count(*) filter (where inspection_result='合格') as qualified_batches, "
                        + "count(*) filter (where inspection_result='不合格') as abnormal_batches, "
                        + "count(*) filter (where review_status='待审核' and inspection_result is null) as in_check_batches, "
                        + "coalesce(sum(submitted_qty), 0) as total_submitted, "
                        + "coalesce(sum(unqualified_qty), 0) as total_unqualified, "
                        + "count(distinct nullif(coalesce(supplier_code, supplier_name), '')) as supplier_count "
                        + "from qms.material_inspection where is_deleted=0");
        long totalBatches = ((Number) inspection.get("total_batches")).longValue();
        long qualifiedBatches = ((Number) inspection.get("qualified_batches")).longValue();
        long totalSubmitted = ((Number) inspection.get("total_submitted")).longValue();
        long totalUnqualified = ((Number) inspection.get("total_unqualified")).longValue();
        s.put("totalBatches", totalBatches);
        s.put("qualifiedBatches", qualifiedBatches);
        s.put("abnormalBatches", ((Number) inspection.get("abnormal_batches")).longValue());
        s.put("inCheckBatches", ((Number) inspection.get("in_check_batches")).longValue());
        s.put("passRate", totalBatches == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(qualifiedBatches * 100.0 / totalBatches).setScale(2, java.math.RoundingMode.HALF_UP));
        s.put("ppm", totalSubmitted == 0 ? 0L : Math.round(totalUnqualified * 1000000.0 / totalSubmitted));
        s.put("supplierCount", ((Number) inspection.get("supplier_count")).longValue());
        s.put("nodeCount", jdbc.queryForObject("select count(*) from qms.trace_node", Long.class));
        s.put("snCount", jdbc.queryForObject("select count(*) from qms.trace_node where node_type='FINISHED_GOOD'", Long.class));
        s.put("finishedGoods", s.get("snCount"));
        s.put("semiFinished", jdbc.queryForObject("select count(*) from qms.trace_node where node_type='SEMI_FINISHED'", Long.class));
        s.put("materialBatches", jdbc.queryForObject("select count(distinct material_batch_no) from qms.trace_node where node_type='MATERIAL'", Long.class));
        return s;
    }

    /** 快捷绑定：将来料检验记录与成品检验记录关联到追溯图 */
    public Map<String, Object> bindMaterialToFinishedGoods(Long materialInspectionId, Long finishedGoodsInspectionId) {
        // 1. 查询来料记录
        Map<String, Object> mat = jdbc.query(
            "select material_batch_no, material_code, material_name, supplier_name from qms.material_inspection where id=? and is_deleted=0",
            rs -> rs.next() ? mapMatRow(rs) : null, materialInspectionId);
        if (mat == null) throw new IllegalArgumentException("来料记录不存在：" + materialInspectionId);

        // 2. 查询成品记录
        Map<String, Object> fg = jdbc.query(
            "select prod_batch_or_sn, product_name, material_code, report_no from qms.finished_goods_inspection where id=? and is_deleted=0",
            rs -> rs.next() ? mapFgRow(rs) : null, finishedGoodsInspectionId);
        if (fg == null) throw new IllegalArgumentException("成品记录不存在：" + finishedGoodsInspectionId);

        String matBatchNo = (String) mat.get("materialBatchNo");
        String matCode = (String) mat.get("materialCode");
        String matName = (String) mat.get("materialName");
        String fgSn = (String) fg.get("prodBatchOrSn");
        String fgName = (String) fg.get("productName");
        String fgReportNo = (String) fg.get("reportNo");

        if (matBatchNo == null || matBatchNo.trim().isEmpty()) throw new IllegalArgumentException("来料记录缺少物料批号");
        if (fgSn == null || fgSn.trim().isEmpty()) throw new IllegalArgumentException("成品记录缺少生产批号/产品编号");

        // 3. 确保成品节点存在（FINISHED_GOOD）
        long fgNodeId = ensureNode("FINISHED_GOOD", fgSn.trim(), fgName != null ? fgName : fgSn,
                null, null, null, null, finishedGoodsInspectionId, null);

        // 4. 确保来料节点存在（MATERIAL）
        long matNodeId = ensureNode("MATERIAL", matBatchNo.trim(), matName != null ? matName : matBatchNo,
                null, matCode, matBatchNo.trim(), null, null, materialInspectionId);

        // 5. 检查是否已绑定
        Integer existing = jdbc.query(
            "select count(*) from qms.trace_relation where parent_node_id=? and child_node_id=?",
            rs -> rs.next() ? rs.getInt(1) : 0, fgNodeId, matNodeId);
        if (existing != null && existing > 0) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bound", false);
            result.put("message", "该成品与来料已绑定，无需重复绑定");
            result.put("finishedGoodsNodeId", fgNodeId);
            result.put("materialNodeId", matNodeId);
            return result;
        }

        // 6. 创建关系（成品→来料，成品为父节点）
        jdbc.update(
            "insert into qms.trace_relation(parent_node_id, child_node_id, work_order_no) values(?,?,?)",
            fgNodeId, matNodeId, fgReportNo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bound", true);
        result.put("message", "绑定成功");
        result.put("finishedGoodsNodeId", fgNodeId);
        result.put("finishedGoodsSn", fgSn);
        result.put("finishedGoodsName", fgName);
        result.put("materialNodeId", matNodeId);
        result.put("materialBatchNo", matBatchNo);
        result.put("materialName", matName);
        result.put("relationParentId", fgNodeId);
        result.put("relationChildId", matNodeId);
        return result;
    }

    public void syncFinishedGoodsNode(FinishedGoodsInspection record) {
        if (record == null || record.getId() == null || !StringUtils.hasText(record.getProdBatchOrSn())) {
            return;
        }
        ensureNode("FINISHED_GOOD", record.getProdBatchOrSn().trim(),
                StringUtils.hasText(record.getProductName()) ? record.getProductName() : record.getProdBatchOrSn().trim(),
                record.getMaterialCode(), null, null, record.getModelSpec(), record.getId(), null);
    }

    public void syncMaterialNode(MaterialInspection record) {
        if (record == null || record.getId() == null || !StringUtils.hasText(record.getMaterialBatchNo())
                || !StringUtils.hasText(record.getMaterialCode())) {
            return;
        }
        ensureNode("MATERIAL", record.getMaterialBatchNo().trim(),
                StringUtils.hasText(record.getMaterialName()) ? record.getMaterialName() : record.getMaterialBatchNo().trim(),
                null, record.getMaterialCode(), record.getMaterialBatchNo().trim(), record.getSpecModel(), null, record.getId());
    }

    private long ensureNode(String type, String barcode, String name, String productCode, String materialCode,
                            String materialBatchNo, String specification, Long finishedGoodsInspectionId,
                            Long materialInspectionId) {
        String masterColumn = finishedGoodsInspectionId != null ? "finished_goods_inspection_id" : "material_inspection_id";
        Long masterId = finishedGoodsInspectionId != null ? finishedGoodsInspectionId : materialInspectionId;
        Long existing = jdbc.query(
            "select id from qms.trace_node where " + masterColumn + "=?",
            rs -> rs.next() ? rs.getLong("id") : null, masterId);
        if (existing == null) {
            existing = jdbc.query("select id from qms.trace_node where barcode=?", rs -> rs.next() ? rs.getLong("id") : null, barcode);
        }
        if (existing != null) {
            jdbc.update("update qms.trace_node set node_type=?, barcode=?, name=?, product_code=?, material_code=?, "
                            + "material_batch_no=?, specification=?, finished_goods_inspection_id=?, material_inspection_id=? where id=?",
                    type, barcode, name, productCode, materialCode, materialBatchNo, specification,
                    finishedGoodsInspectionId, materialInspectionId, existing);
            return existing;
        }
        jdbc.update(
            "insert into qms.trace_node(node_type,barcode,name,product_code,material_code,material_batch_no,specification,finished_goods_inspection_id,material_inspection_id) values(?,?,?,?,?,?,?,?,?)",
            type, barcode, name, productCode, materialCode, materialBatchNo, specification,
            finishedGoodsInspectionId, materialInspectionId);
        return jdbc.queryForObject("select id from qms.trace_node where " + masterColumn + "=?", Long.class, masterId);
    }

    private Map<String, Object> mapMatRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("materialBatchNo", rs.getString("material_batch_no"));
        r.put("materialCode", rs.getString("material_code"));
        r.put("materialName", rs.getString("material_name"));
        r.put("supplierName", rs.getString("supplier_name"));
        return r;
    }

    private Map<String, Object> mapFgRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("prodBatchOrSn", rs.getString("prod_batch_or_sn"));
        r.put("productName", rs.getString("product_name"));
        r.put("materialCode", rs.getString("material_code"));
        r.put("reportNo", rs.getString("report_no"));
        return r;
    }

    private Map<String,Object> batchImpact(String batch) {
        List<Map<String,Object>> lots=jdbc.query("select * from qms.trace_node where node_type='MATERIAL' and material_batch_no=? order by barcode",(rs,i)->mapNode(rs),batch);
        if(lots.isEmpty()) throw new IllegalArgumentException("未找到物料批次："+batch);
        Set<Long> visited=new LinkedHashSet<>(); for(Map<String,Object> lot:lots) lot.put("children",expand(((Number)lot.get("id")).longValue(),true,new LinkedHashSet<>(),visited));
        Map<String,Object> root=new LinkedHashMap<>(); root.put("id",0);root.put("nodeType","BATCH");root.put("barcode",batch);root.put("name","来料批次影响范围");root.put("children",lots);
        return result(root,visited.size(),"BATCH_IMPACT",lots.size());
    }
    /**
     * Builds a complete path tree.  The path set protects against cycles only on the
     * current branch; a graph node reached through another valid branch must remain
     * visible instead of being globally suppressed.
     */
    private List<Map<String,Object>> expand(long id, boolean up, Set<Long> path, Set<Long> visited) {
        if (!path.add(id)) return Collections.emptyList();
        visited.add(id);
        List<Map<String,Object>> rows = linked(id, up);
        for (Map<String,Object> row : rows) {
            long childId = ((Number) row.get("id")).longValue();
            row.put("children", expand(childId, up, new LinkedHashSet<>(path), visited));
        }
        return rows;
    }
    private List<Map<String,Object>> linked(long id, boolean up) { String sql=up?"select n.* from qms.trace_relation r join qms.trace_node n on n.id=r.parent_node_id where r.child_node_id=? order by n.barcode":"select n.* from qms.trace_relation r join qms.trace_node n on n.id=r.child_node_id where r.parent_node_id=? order by n.barcode"; return jdbc.query(sql,(rs,i)->mapNode(rs),id); }
    private boolean reachable(long from,long target,Set<Long> seen){if(from==target)return true;if(!seen.add(from))return false;for(Map<String,Object> n:linked(from,false))if(reachable(((Number)n.get("id")).longValue(),target,seen))return true;return false;}
    private Map<String,Object> nodeByBarcode(String barcode){return jdbc.query("select * from qms.trace_node where barcode=?",rs->rs.next()?mapNode(rs):null,barcode);}
    private Map<String,Object> mapNode(java.sql.ResultSet rs)throws java.sql.SQLException {Map<String,Object> n=new LinkedHashMap<>();n.put("id",rs.getLong("id"));n.put("nodeType",rs.getString("node_type"));n.put("barcode",rs.getString("barcode"));n.put("name",rs.getString("name"));n.put("productCode",rs.getString("product_code"));n.put("materialCode",rs.getString("material_code"));n.put("materialBatchNo",rs.getString("material_batch_no"));n.put("specification",rs.getString("specification"));n.put("finishedGoodsInspectionId",rs.getObject("finished_goods_inspection_id"));n.put("materialInspectionId",rs.getObject("material_inspection_id"));return n;}
    private Map<String,Object> result(Map<String,Object> root,int count,String direction,int lots){Map<String,Object> r=new LinkedHashMap<>();r.put("root",root);r.put("direction",direction);r.put("summary",summary());r.put("visitedNodes",count);r.put("batchLots",lots);return r;}
    private String text(Map<String,Object>b,String k){Object v=b.get(k);return v==null?"":v.toString().trim();} private Object nullable(Map<String,Object>b,String k){String v=text(b,k);return v.isEmpty()?null:v;} private long number(Map<String,Object>b,String k){try{return Long.parseLong(text(b,k));}catch(Exception e){throw new IllegalArgumentException("缺少有效的 "+k);}} private BigDecimal decimal(Map<String,Object>b,String k){String v=text(b,k);return v.isEmpty()?null:new BigDecimal(v);}
}
