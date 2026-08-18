package com.kangli.qms.service.exception;

import java.util.List;

/**
 * M2 异常整改模块常量定义。
 * <p>统一管理 8D 步骤、审批状态、流程类型、CAPA 相位等硬编码字符串。</p>
 */
public final class ExceptionConstants {

    // ---- 8D 步骤 ----
    public static final String D0 = "D0";
    public static final String D1 = "D1";
    public static final String D2 = "D2";
    public static final String D3 = "D3";
    public static final String D4 = "D4";
    public static final String D5 = "D5";
    public static final String D6 = "D6";
    public static final String D7 = "D7";
    public static final String D8 = "D8";

    /** 8D 步骤推进顺序（D1→D8），D0 不进入步骤链 */
    public static final List<String> EIGHT_D_STEP_ORDER = List.of(D1, D2, D3, D4, D5, D6, D7, D8);

    // ---- 阶段审批状态 ----
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** 审批通过终态集合 */
    public static final java.util.Set<String> APPROVAL_FINAL_STATUSES =
            java.util.Set.of(STATUS_APPROVED, STATUS_REJECTED);

    // ---- 整改流程类型 ----
    public static final String PROCESS_CAPA = "CAPA";
    public static final String PROCESS_EIGHT_D = "8D";
    public static final String PROCESS_BOTH = "BOTH";

    // ---- CAPA 相位（CAPA / BOTH 模式） ----
    public static final String CAPA_PHASE_INITIATE = "INITIATE";
    public static final String CAPA_PHASE_ROOT_CAUSE_APPROVED = "ROOT_CAUSE_APPROVED";
    public static final String CAPA_PHASE_MEASURES_APPROVED = "MEASURES_APPROVED";
    public static final String CAPA_PHASE_CLOSED = "CLOSED";

    // ---- 异常单状态 ----
    public static final String EXCEPTION_STATUS_PENDING = "待整改";
    public static final String EXCEPTION_STATUS_IN_PROGRESS = "整改中";
    public static final String EXCEPTION_STATUS_PENDING_VERIFY = "待验证";
    public static final String EXCEPTION_STATUS_CLOSED = "已闭环";

    // ---- 改善措施状态 ----
    public static final String ACTION_STATUS_PENDING = "PENDING";
    public static final String ACTION_STATUS_DONE = "DONE";

    // ---- 权限角色 ----
    public static final String ROLE_SUPER_ADMIN = "R00";
    public static final String ROLE_QUALITY_MANAGER = "R06";

    private ExceptionConstants() {
    }
}
