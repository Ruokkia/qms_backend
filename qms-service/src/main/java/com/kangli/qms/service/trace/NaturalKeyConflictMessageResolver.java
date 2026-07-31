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
        if (databaseMessage.contains("uq_cmb_nat_key")) {
            return "该绑定关系已存在（同一工单+产品条码+物料条码+工序），请勿重复绑定";
        }
        return null;
    }
}
