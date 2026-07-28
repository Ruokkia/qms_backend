package com.kangli.qms.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务响应码枚举。
 * <p>0=成功；1xxx=认证模块错误；4xx=客户端错误；5xx=服务端错误。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ---- 通用 ----
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或Token已失效"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ---- 认证模块 1xxx ----
    ACCOUNT_OR_PASSWORD_ERROR(1001, "账号或密码错误"),
    ACCOUNT_LOCKED(1002, "账号已锁定，请稍后重试"),
    ACCOUNT_DISABLED(1003, "账号已禁用"),
    PLANT_CODE_MISMATCH(1004, "分公司不匹配"),
    REFRESH_TOKEN_INVALID(1005, "Refresh Token无效或已过期"),
    CAPTCHA_ERROR(1006, "验证码错误或已过期"),
    TOKEN_INVALID(1007, "Token无效"),
    TOKEN_EXPIRED(1008, "Token已过期"),

    // ---- M2 业务错误 2xxx ----
    CLOSE_PRECONDITION_NOT_MET(2001, "闭环前置条件不满足"),
    IMPORT_RECORD_INVALID(2005, "导入记录校验失败"),
    EIGHT_D_STEP_INVALID(2006, "8D步骤无法回退"),
    EIGHT_D_ALREADY_CLOSED(2007, "8D已闭环"),
    VERSION_CONFLICT(2008, "数据已被他人修改，请刷新后重试"),
    ;

    private final int code;
    private final String message;
}
