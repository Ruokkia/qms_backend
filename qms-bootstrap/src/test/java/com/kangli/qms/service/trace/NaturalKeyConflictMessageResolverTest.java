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
    void translatesTraceRelationNaturalKeyConflict() {
        assertEquals("该成品与来料已绑定，无需重复绑定",
                NaturalKeyConflictMessageResolver.resolve("duplicate key value violates unique constraint uq_trace_relation_binding"));
    }
}
