package com.kangli.qms.service.finishedgoods;

/** Determines the traceability node type from the finished-goods master category. */
public final class FinishedGoodsTraceNodeTypeResolver {

    private FinishedGoodsTraceNodeTypeResolver() {
    }

    public static String resolve(String category) {
        return "半成品".equals(category) ? "SEMI_FINISHED" : "FINISHED_GOOD";
    }
}
