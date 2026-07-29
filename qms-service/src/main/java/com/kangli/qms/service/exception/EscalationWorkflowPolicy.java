package com.kangli.qms.service.exception;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;

/** Supplier escalation stages are deliberately explicit to prevent direct closure. */
public final class EscalationWorkflowPolicy {
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String PLAN = "PLAN";
    public static final String EXECUTION = "EXECUTION";
    public static final String VERIFICATION = "VERIFICATION";
    public static final String PENDING_CLOSE_APPROVAL = "PENDING_CLOSE_APPROVAL";
    public static final String CLOSED = "CLOSED";
    public static final String REJECTED = "REJECTED";

    private EscalationWorkflowPolicy() { }

    public static void requireStage(String actual, String expected, String action) {
        if (!expected.equals(actual)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前处于“" + label(actual) + "”，不能执行" + action);
        }
    }

    public static String label(String stage) {
        if (PENDING_REVIEW.equals(stage)) return "待审核";
        if (PLAN.equals(stage)) return "制定措施";
        if (EXECUTION.equals(stage)) return "执行跟踪";
        if (VERIFICATION.equals(stage)) return "效果验证";
        if (PENDING_CLOSE_APPROVAL.equals(stage)) return "待关闭审批";
        if (CLOSED.equals(stage)) return "已关闭";
        if (REJECTED.equals(stage)) return "已驳回";
        return stage;
    }
}
