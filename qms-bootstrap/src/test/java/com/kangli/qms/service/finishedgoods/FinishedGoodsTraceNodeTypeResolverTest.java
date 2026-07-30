package com.kangli.qms.service.finishedgoods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinishedGoodsTraceNodeTypeResolverTest {

    @Test
    void 半成品应同步为半成品追溯节点() {
        assertEquals("SEMI_FINISHED", FinishedGoodsTraceNodeTypeResolver.resolve("半成品"));
    }

    @Test
    void 成品和历史空分类应同步为成品追溯节点() {
        assertEquals("FINISHED_GOOD", FinishedGoodsTraceNodeTypeResolver.resolve("成品"));
        assertEquals("FINISHED_GOOD", FinishedGoodsTraceNodeTypeResolver.resolve(null));
    }
}
