package com.kangli.qms.service.exception.impl;

import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.exception.vo.QualityExceptionDecisionVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityExceptionRuleEvaluatorTest {

    private final QualityExceptionRuleEvaluator evaluator = new QualityExceptionRuleEvaluator();

    @Test
    void classifiesGeneralDefectAsCapa() {
        MaterialInspection inspection = inspection("100", "2", "挑选");

        QualityExceptionDecisionVO decision = evaluator.evaluate(inspection, 1);

        assertEquals("一般", decision.getSeverity());
        assertEquals("CAPA", decision.getProcessType());
        assertEquals("提醒", decision.getNotificationLevel());
        assertEquals(7, decision.getDeadlineDays());
    }

    @Test
    void classifiesFivePercentDefectRateAsSevere() {
        MaterialInspection inspection = inspection("100", "5", "挑选");

        QualityExceptionDecisionVO decision = evaluator.evaluate(inspection, 1);

        assertEquals("严重", decision.getSeverity());
        assertEquals("8D", decision.getProcessType());
        assertTrue(decision.getRuleReason().contains("5.00%"));
    }

    @Test
    void classifiesReturnOrScrapAsSevere() {
        QualityExceptionDecisionVO returnDecision = evaluator.evaluate(inspection("100", "1", "退货"), 1);
        QualityExceptionDecisionVO scrapDecision = evaluator.evaluate(inspection("100", "1", "报废"), 1);

        assertEquals("严重", returnDecision.getSeverity());
        assertEquals("严重", scrapDecision.getSeverity());
    }

    @Test
    void classifiesSecondDefectForSameSupplierAndMaterialAsSevere() {
        MaterialInspection inspection = inspection("100", "1", "挑选");

        QualityExceptionDecisionVO decision = evaluator.evaluate(inspection, 2);

        assertEquals("严重", decision.getSeverity());
        assertEquals(2, decision.getRepeatCount30Days());
        assertTrue(decision.getRuleReason().contains("30天内第2批"));
    }

    @Test
    void zeroSubmittedQuantityDoesNotProduceInvalidRate() {
        MaterialInspection inspection = inspection("0", "1", "挑选");

        QualityExceptionDecisionVO decision = evaluator.evaluate(inspection, 1);

        assertEquals("一般", decision.getSeverity());
        assertEquals(null, decision.getDefectRate());
        assertTrue(decision.getRuleReason().contains("送检数量缺失或为0"));
    }

    @Test
    void exposesTheSameRulesAndMeasuresShownToUsers() {
        QualityRuleCatalogVO catalog = evaluator.catalog();

        assertEquals("仅使用来料检验入库审核表", catalog.getDataSource());
        assertTrue(catalog.getSeverityRules().stream()
                .anyMatch(item -> item.getCondition().contains("不良率 ≥ 5%")));
        assertTrue(catalog.getEscalationRules().stream()
                .anyMatch(item -> item.getCondition().contains("90天内3批")));
        assertTrue(catalog.getHandlingMethods().size() >= 3);
    }

    private MaterialInspection inspection(String submittedQty, String unqualifiedQty, String handlingMethod) {
        MaterialInspection inspection = new MaterialInspection();
        inspection.setInspectionResult("不合格");
        inspection.setSubmittedQty(new BigDecimal(submittedQty));
        inspection.setUnqualifiedQty(new BigDecimal(unqualifiedQty));
        inspection.setHandlingMethod(handlingMethod);
        inspection.setSupplierCode("SUP-01");
        inspection.setMaterialCode("MAT-01");
        return inspection;
    }
}
