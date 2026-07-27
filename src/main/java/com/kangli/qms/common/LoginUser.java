package com.kangli.qms.common;

import com.kangli.qms.enums.PlantCode;
import lombok.Builder;
import lombok.Data;

/**
 * 登录用户上下文（从 JWT 解析后注入 ThreadLocal，贯穿整次请求）。
 * <p>由 {@link com.kangli.qms.config.JwtInterceptor} 在认证通过后写入，
 * 请求结束后由拦截器清除。</p>
 */
@Data
@Builder
public class LoginUser {

    /** 用户ID */
    private Long userId;

    /** 登录账号 */
    private String account;

    /** 真实姓名（可选，仅 /auth/me 接口需要查库补全） */
    private String realName;

    /** 角色编码 R01~R06 */
    private String roleCode;

    /** 分公司编码 */
    private PlantCode plantCode;

    /** 是否可切换分公司（仅 R06） */
    private boolean canSwitchArea;

    private Integer authVersion;
}
