package com.kangli.qms.service.exception.impl;

import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.exception.vo.QualityExceptionDecisionVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 开发人员确认的来料异常判定规则。
 * 所有判断字段仅来自 qms.material_inspection。
 */
@Component
public class QualityExceptionRuleEvaluator {

    public static final BigDecimal SEVERE_DEFECT_RATE = new BigDecimal("5.00");
    public static final int REPEAT_SEVERE_COUNT_30_DAYS = 2;

    public QualityExceptionDecisionVO evaluate(MaterialInspection inspection, int repeatCount30Days) {
        BigDecimal defectRate = calculateDefectRate(inspection);
        List<String> reasons = new ArrayList<>();

        if (defectRate != null && defectRate.compareTo(SEVERE_DEFECT_RATE) >= 0) {
            reasons.add("不良率" + defectRate.toPlainString() + "%达到严重阈值5.00%");
        }
        if ("退货".equals(inspection.getHandlingMethod()) || "报废".equals(inspection.getHandlingMethod())) {
            reasons.add("处理方式为" + inspection.getHandlingMethod());
        }
        if (repeatCount30Days >= REPEAT_SEVERE_COUNT_30_DAYS) {
            reasons.add("同一供应商、同一物料30天内第" + repeatCount30Days + "批不合格");
        }

        boolean severe = !reasons.isEmpty();
        if (defectRate == null) {
            reasons.add("送检数量缺失或为0，未计算不良率");
        }
        if (!severe) {
            reasons.add("未命中严重规则，按一般不良处理");
        }

        QualityExceptionDecisionVO decision = new QualityExceptionDecisionVO();
        decision.setSeverity(severe ? "严重" : "一般");
        decision.setNotificationLevel(severe ? "严重" : "提醒");
        decision.setResponseHours(severe ? 24 : 48);
        decision.setDeadlineDays(severe ? 3 : 7);
        decision.setDefectRate(defectRate);
        decision.setRepeatCount30Days(repeatCount30Days);
        decision.setRuleReason(String.join("；", reasons));
        decision.setHandlingMethods(buildHandlingMethods(severe));
        decision.setRequiredMeasures(buildRequiredMeasures(severe));
        return decision;
    }

    /**
     * 首件检验不合格的严重等级评估。首件记录无供应商/不良率字段，无法复用来料口径，
     * 故采用「同一产品/工序30天内首件不合格重复次数」作为严重判定依据，与来料重复规则对称。
     */
    public QualityExceptionDecisionVO evaluateFai(FaiInspectionRecord record, int repeatCount30Days) {
        List<String> reasons = new ArrayList<>();
        if (repeatCount30Days >= REPEAT_SEVERE_COUNT_30_DAYS) {
            reasons.add("同一产品、同一工序30天内第" + repeatCount30Days + "次首件不合格");
        }
        boolean severe = !reasons.isEmpty();

        QualityExceptionDecisionVO decision = new QualityExceptionDecisionVO();
        decision.setSeverity(severe ? "严重" : "一般");
        decision.setNotificationLevel(severe ? "严重" : "提醒");
        decision.setResponseHours(severe ? 24 : 48);
        decision.setDeadlineDays(severe ? 3 : 7);
        decision.setRepeatCount30Days(repeatCount30Days);
        decision.setRuleReason(String.join("；", severe
                ? reasons : Collections.singletonList("首件不合格，未命中严重规则，按一般不良处理")));
        decision.setHandlingMethods(buildHandlingMethods(severe));
        decision.setRequiredMeasures(buildRequiredMeasures(severe));
        return decision;
    }

    /**
     * 成品入库检验不合格的严重等级评估。成品记录无供应商维度，采用「不合格率 ≥ 5%」作为严重判定依据，
     * 与来料不良率规则对称；无不合格数量或未计算时按一般不良处理。
     */
    public QualityExceptionDecisionVO evaluateFinishedGoods(FinishedGoodsInspection inspection) {
        List<String> reasons = new ArrayList<>();
        BigDecimal defectRate = calculateFinishedDefectRate(inspection);
        if (defectRate != null && defectRate.compareTo(SEVERE_DEFECT_RATE) >= 0) {
            reasons.add("成品不合格率" + defectRate.toPlainString() + "%达到严重阈值5.00%");
        }
        boolean severe = !reasons.isEmpty();
        if (defectRate == null) {
            reasons.add("检验数量缺失或为0，未计算不合格率");
        }
        if (!severe) {
            reasons.add("未命中严重规则，按一般不良处理");
        }

        QualityExceptionDecisionVO decision = new QualityExceptionDecisionVO();
        decision.setSeverity(severe ? "严重" : "一般");
        decision.setNotificationLevel(severe ? "严重" : "提醒");
        decision.setResponseHours(severe ? 24 : 48);
        decision.setDeadlineDays(severe ? 3 : 7);
        decision.setDefectRate(defectRate);
        decision.setRuleReason(String.join("；", reasons));
        decision.setHandlingMethods(buildHandlingMethods(severe));
        decision.setRequiredMeasures(buildRequiredMeasures(severe));
        return decision;
    }

    private BigDecimal calculateFinishedDefectRate(FinishedGoodsInspection inspection) {
        if (inspection.getInspectedQty() == null
                || inspection.getInspectedQty().compareTo(BigDecimal.ZERO) <= 0
                || inspection.getUnqualifiedQty() == null) {
            return null;
        }
        return inspection.getUnqualifiedQty()
                .multiply(new BigDecimal("100"))
                .divide(inspection.getInspectedQty(), 2, RoundingMode.HALF_UP);
    }

    public QualityRuleCatalogVO catalog() {
        QualityRuleCatalogVO catalog = new QualityRuleCatalogVO();
        catalog.setVersion("1A-2A-3A / 2026-07");
        catalog.setDataSource("仅使用来料检验入库审核表");
        catalog.setRepeatKey("分公司 + 供应商编号 + 物料代码");

        catalog.getSeverityRules().add(new QualityRuleCatalogVO.RuleItem(
                "不良率规则", "不良率 ≥ 5%", "严重", "自动发起8D，24小时内响应，3天内完成整改"));
        catalog.getSeverityRules().add(new QualityRuleCatalogVO.RuleItem(
                "处置方式规则", "处理方式为退货或报废", "严重", "自动发起8D并通知质量经理"));
        catalog.getSeverityRules().add(new QualityRuleCatalogVO.RuleItem(
                "重复发生规则", "同一供应商、同一物料30天内第2批不合格", "严重", "自动发起8D并标记重复问题"));
        catalog.getSeverityRules().add(new QualityRuleCatalogVO.RuleItem(
                "一般不良规则", "未命中以上严重条件", "一般", "自动发起CAPA，48小时内响应，7天内完成整改"));

        catalog.getNotificationRules().add(new QualityRuleCatalogVO.RuleItem(
                "一般异常", "判定等级为一般", "提醒", "通知当前处理人、检验员和SQE"));
        catalog.getNotificationRules().add(new QualityRuleCatalogVO.RuleItem(
                "严重异常", "判定等级为严重", "严重", "追加通知质量经理；采购负责人角色配置后同步接收"));

        catalog.getEscalationRules().add(new QualityRuleCatalogVO.RuleItem(
                "一级预警", "30天内2批不合格", "加严关注", "后续5批建议加严检验"));
        catalog.getEscalationRules().add(new QualityRuleCatalogVO.RuleItem(
                "二级升级", "90天内3批不合格", "升级审核", "自动生成升级审核并强制8D"));
        catalog.getEscalationRules().add(new QualityRuleCatalogVO.RuleItem(
                "三级升级", "180天内升级后再次达到阈值", "采购策略建议", "建议降低采购份额20%，须质量和采购审核"));

        catalog.getHandlingMethods().add("隔离不合格批次，禁止未经批准入库或投入使用");
        catalog.getHandlingMethods().add("选择退货、挑选、特采或报废，并记录处置依据");
        catalog.getHandlingMethods().add("评估相同供应商、物料和库存批次的潜在影响");

        catalog.getGeneralMeasures().addAll(buildRequiredMeasures(false));
        catalog.getSevereMeasures().addAll(buildRequiredMeasures(true));
        return catalog;
    }

    private BigDecimal calculateDefectRate(MaterialInspection inspection) {
        if (inspection.getSubmittedQty() == null
                || inspection.getSubmittedQty().compareTo(BigDecimal.ZERO) <= 0
                || inspection.getUnqualifiedQty() == null) {
            return null;
        }
        return inspection.getUnqualifiedQty()
                .multiply(new BigDecimal("100"))
                .divide(inspection.getSubmittedQty(), 2, RoundingMode.HALF_UP);
    }

    private List<String> buildHandlingMethods(boolean severe) {
        List<String> methods = new ArrayList<>();
        methods.add("隔离当前不合格批次，禁止未经批准入库或投入使用");
        methods.add("确认退货、挑选、特采或报废等处置方式并保留记录");
        if (severe) {
            methods.add("24小时内完成应急响应并评估对在制品、库存及已交付产品的影响");
        }
        return methods;
    }

    private List<String> buildRequiredMeasures(boolean severe) {
        List<String> measures = new ArrayList<>();
        if (severe) {
            measures.add("自动发起8D，完成D1至D8并上传原因分析和验证证据");
            measures.add("制定临时遏制、根因纠正和防止复发措施");
            measures.add("质量经理与采购负责人参与审核");
        } else {
            measures.add("自动发起CAPA，记录根因、纠正措施和预防措施");
            measures.add("整改完成后提交验证，验证通过方可闭环");
        }
        return measures;
    }
}
