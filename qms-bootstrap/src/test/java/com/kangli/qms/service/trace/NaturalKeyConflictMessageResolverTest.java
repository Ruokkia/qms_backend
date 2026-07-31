package com.kangli.qms.service.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NaturalKeyConflictMessageResolverTest {

    @Test
    void translatesFinishedGoodsReportNaturalKeyConflict() {
        assertEquals("报告编号已存在，请勿重复提交",
                NaturalKeyConflictMessageResolver.resolve("duplicate key value violates unique constraint uq_finished_goods_report_no_active"));
    }

    @Test
    void translatesCriticalMaterialBindingNaturalKeyConflict() {
        assertEquals("该绑定关系已存在（同一工单+产品条码+物料条码+工序），请勿重复绑定",
                NaturalKeyConflictMessageResolver.resolve("duplicate key value violates unique constraint uq_cmb_nat_key"));
    }
}
