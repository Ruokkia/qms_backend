package com.kangli.qms.service.notification.enums;

/**
 * 通知类型统一枚举 — 覆盖系统内全部通知场景。
 *
 * <p>枚举 code 值与已有硬编码字符串保持一致，确保向后兼容。</p>
 */
public enum NotificationTypeEnum {

    /** 异常单创建通知 */
    EXCEPTION_CREATED("EXCEPTION_CREATED", "异常创建"),

    /** 异常单状态变更通知 */
    EXCEPTION_STATUS_CHANGED("EXCEPTION_STATUS_CHANGED", "状态变更"),

    /** 异常单闭环通知 */
    EXCEPTION_CLOSED("EXCEPTION_CLOSED", "异常闭环"),

    /** 供应商升级触发通知 */
    ESCALATION_TRIGGERED("ESCALATION_TRIGGERED", "升级触发"),

    /** 8D 整改负责人指派通知（D0 阶段） */
    EIGHT_D_LEADER_ASSIGNED("EIGHT_D_LEADER_ASSIGNED", "整改负责人指派"),

    /** 8D 团队待审核通知 */
    EIGHT_D_TEAM_PENDING_REVIEW("EIGHT_D_TEAM_PENDING_REVIEW", "8D团队待审核"),

    /** 8D 团队审核通过通知 */
    EIGHT_D_TEAM_APPROVED("EIGHT_D_TEAM_APPROVED", "8D团队审核通过"),

    /** 改善措施责任人指派通知 */
    ACTION_OWNER_ASSIGNED("ACTION_OWNER_ASSIGNED", "改善措施指派"),

    /** 整改计划负责人指派通知 */
    PLAN_OWNER_ASSIGNED("PLAN_OWNER_ASSIGNED", "整改计划指派"),

    /** 升级措施责任人指派通知 */
    ESCALATION_OWNER_ASSIGNED("ESCALATION_OWNER_ASSIGNED", "升级措施指派"),

    // ---- BOTH 模式 CAPA-8D 交错流程通知场景 ----

    /** D4 根因分析提交 */
    EIGHT_D_D4_SUBMITTED("EIGHT_D_D4_SUBMITTED", "D4根因分析提交"),

    /** CAPA 根因审批通过 */
    CAPA_ROOT_CAUSE_APPROVED("CAPA_ROOT_CAUSE_APPROVED", "CAPA根因审批通过"),

    /** D5 措施方案提交 */
    EIGHT_D_D5_SUBMITTED("EIGHT_D_D5_SUBMITTED", "D5措施方案提交"),

    /** CAPA 措施审批通过 */
    CAPA_MEASURES_APPROVED("CAPA_MEASURES_APPROVED", "CAPA措施审批通过"),

    /** CAPA 闭环 */
    CAPA_CLOSED("CAPA_CLOSED", "CAPA闭环"),

    // ---- 供应商物料变更管理通知场景 ----

    /** 供应商物料变更提交（通知审批人） */
    SUPPLIER_CHANGE_SUBMITTED("SUPPLIER_CHANGE_SUBMITTED", "供应商物料变更提交"),

    /** 供应商物料变更批准（通知申请人维护标准 + 加严检验） */
    SUPPLIER_CHANGE_APPROVED("SUPPLIER_CHANGE_APPROVED", "供应商物料变更批准"),

    /** 供应商物料变更驳回（通知申请人） */
    SUPPLIER_CHANGE_REJECTED("SUPPLIER_CHANGE_REJECTED", "供应商物料变更驳回");

    private final String code;
    private final String label;

    NotificationTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
