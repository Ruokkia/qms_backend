package com.kangli.qms.service.trace;

import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.service.finishedgoods.FinishedGoodsInspectionService;
import com.kangli.qms.service.incoming.MaterialInspectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class TraceMasterLinkServiceTest {

    @Autowired
    private FinishedGoodsInspectionService finishedGoodsInspectionService;

    @Autowired
    private MaterialInspectionService materialInspectionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncomingTraceService incomingTraceService;

    @Test
    void manualTraceNodeCreation_rejectsFinishedGoodsAndMaterialNodes() {
        assertThrows(IllegalArgumentException.class, () -> incomingTraceService.createNode(Map.of(
                "nodeType", "FINISHED_GOOD",
                "barcode", "TEST-MANUAL-FG-001",
                "name", "Manual finished good"
        )));
    }

    @Test
    void savingFinishedGoods_createsTraceNodeLinkedByForeignKey() {
        FinishedGoodsInspection record = new FinishedGoodsInspection();
        record.setReportNo("TEST-FG-LINK-001");
        record.setProductName("Trace link test finished good");
        record.setMaterialCode("TEST-FG-CODE");
        record.setModelSpec("TEST-MODEL");
        record.setProdBatchOrSn("TEST-FG-BATCH-001");
        record.setPlantCode("SZ");
        record.setPlantName("深圳");

        finishedGoodsInspectionService.save(record);

        Map<String, Object> node = jdbcTemplate.queryForMap(
                "select node_type, barcode, finished_goods_inspection_id from qms.trace_node where finished_goods_inspection_id=?",
                record.getId());
        assertEquals("FINISHED_GOOD", node.get("node_type"));
        assertEquals("TEST-FG-BATCH-001", node.get("barcode"));
        assertNotNull(node.get("finished_goods_inspection_id"));

        record.setProdBatchOrSn("TEST-FG-BATCH-002");
        finishedGoodsInspectionService.updateById(record);

        assertEquals("TEST-FG-BATCH-002", jdbcTemplate.queryForObject(
                "select barcode from qms.trace_node where finished_goods_inspection_id=?",
                String.class, record.getId()));
    }

    @Test
    void savingMaterial_createsTraceNodeLinkedByForeignKey() {
        MaterialInspection record = new MaterialInspection();
        record.setRecordNo("TEST-MAT-LINK-001");
        record.setMaterialName("Trace link test material");
        record.setMaterialCode("TEST-MAT-CODE");
        record.setMaterialBatchNo("TEST-MAT-BATCH-001");
        record.setSpecModel("TEST-SPEC");
        record.setPlantCode("SZ");
        record.setPlantName("深圳");

        materialInspectionService.save(record);

        Map<String, Object> node = jdbcTemplate.queryForMap(
                "select node_type, barcode, material_inspection_id from qms.trace_node where material_inspection_id=?",
                record.getId());
        assertEquals("MATERIAL", node.get("node_type"));
        assertEquals("TEST-MAT-BATCH-001", node.get("barcode"));
        assertNotNull(node.get("material_inspection_id"));

        record.setMaterialBatchNo("TEST-MAT-BATCH-002");
        materialInspectionService.updateById(record);

        assertEquals("TEST-MAT-BATCH-002", jdbcTemplate.queryForObject(
                "select barcode from qms.trace_node where material_inspection_id=?",
                String.class, record.getId()));
    }
}
