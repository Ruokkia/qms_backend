package com.kangli.qms.service.trace;

/** Converts database natural-key conflicts into user-facing business messages. */
public final class NaturalKeyConflictMessageResolver {

    private NaturalKeyConflictMessageResolver() {
    }

    public static String resolve(String databaseMessage) {
        if (databaseMessage == null) {
            return null;
        }
        if (databaseMessage.contains("uq_finished_goods_report_no_active")) {
            return "报告编号已存在，请勿重复提交";
        }
        if (databaseMessage.contains("uq_trace_relation_binding")) {
            return "该成品与来料已绑定，无需重复绑定";
        }
        return null;
    }
}
