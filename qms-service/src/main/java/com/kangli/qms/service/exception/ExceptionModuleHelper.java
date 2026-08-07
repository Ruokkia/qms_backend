package com.kangli.qms.service.exception;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import lombok.extern.slf4j.Slf4j;

/**
 * M2 异常整改模块公共工具方法。
 * <p>集中管理跨 Service 共享的用户获取、权限断言、流程判断等逻辑。</p>
 */
@Slf4j
public final class ExceptionModuleHelper {

    private ExceptionModuleHelper() {
    }

    // ==================== 流程类型判断 ====================

    /** 流程类型是否包含 8D */
    public static boolean processIncludes8D(String processType) {
        return ExceptionConstants.PROCESS_EIGHT_D.equals(processType)
                || ExceptionConstants.PROCESS_BOTH.equals(processType);
    }

    // ==================== 当前用户获取 ====================

    /** 获取当前登录用户，若未获取到则抛出未认证异常 */
    public static LoginUser currentUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到当前登录用户");
        }
        return loginUser;
    }

    /** 获取当前用户 ID */
    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    /** 获取当前用户账号 */
    public static String currentAccount() {
        return currentUser().getAccount();
    }

    /** 获取当前用户真实姓名（兜底账号） */
    public static String currentRealName() {
        LoginUser u = currentUser();
        return u.getRealName() != null ? u.getRealName() : u.getAccount();
    }

    /** 获取当前用户分公司编码（严格模式，无用户时抛异常） */
    public static String currentPlantCode() {
        return currentUser().getPlantCode().name();
    }

    /**
     * 获取当前用户分公司编码，无用户时兜底返回 "SZ"（非严格场景用）。
     * 关键路径请使用 {@link #currentPlantCode()} 会在无用户时抛异常。
     */
    public static String currentPlantCodeSafe() {
        try {
            return currentUser().getPlantCode().name();
        } catch (Exception e) {
            return "SZ";
        }
    }

    /** 当前操作人字符串，格式：username / realName */
    public static String currentOperator() {
        LoginUser u = currentUser();
        return String.format("%s/%s",
                u.getAccount(),
                u.getRealName() != null ? u.getRealName() : u.getAccount());
    }

    // ==================== 权限断言 ====================

    /** 断言当前用户拥有指定角色之一，否则抛出 403。R00 管理员自动绕过 */
    public static void assertRole(String... allowedRoles) {
        assertRole(currentUser(), allowedRoles);
    }

    /** 断言指定用户拥有指定角色之一，否则抛出 403。R00 管理员自动绕过 */
    public static void assertRole(LoginUser loginUser, String... allowedRoles) {
        String currentRole = loginUser.getRoleCode();
        // R00 超级管理员绕过所有角色检查
        if (ExceptionConstants.ROLE_SUPER_ADMIN.equals(currentRole)) {
            return;
        }
        for (String role : allowedRoles) {
            if (role.equals(currentRole)) {
                return;
            }
        }
        throw new BusinessException(ResultCode.FORBIDDEN,
                "当前角色（" + currentRole + "）无权执行此操作，需具备角色: " + String.join(", ", allowedRoles));
    }

    /** 审批权限：R00 管理员 + R06 质量经理 + 指定审批角色可审批 */
    public static void requireApprovalPrivilege(String approverRole) {
        String currentRole = currentUser().getRoleCode();
        if (ExceptionConstants.ROLE_SUPER_ADMIN.equals(currentRole)) return;
        if (ExceptionConstants.ROLE_QUALITY_MANAGER.equals(currentRole)) return;
        if (approverRole.equals(currentRole)) return;
        throw new BusinessException(ResultCode.FORBIDDEN,
                "当前角色（" + currentRole + "）无权审批，需 " + approverRole);
    }

    // ==================== 常见校验 ====================

    /** 校验 8D 未闭环 */
    public static void require8DNotClosed(String status) {
        if (ExceptionConstants.STATUS_APPROVED.equals(status)) {
            throw new BusinessException(ResultCode.EIGHT_D_ALREADY_CLOSED);
        }
    }
}
