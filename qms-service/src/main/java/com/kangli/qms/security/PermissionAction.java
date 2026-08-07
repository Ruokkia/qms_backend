package com.kangli.qms.security;

public enum PermissionAction {
    VIEW, EDIT, APPROVE, EXPORT,
    // 8D 专项操作
    EDIT_8D, NEXT_STEP_8D,
    // 异常单关单
    CLOSE,
    // 供应商升级审核/关单
    ESCALATION_REVIEW, ESCALATION_CLOSE,
    // 重置
    RESET
}
