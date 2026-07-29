package com.kangli.qms.service;

import com.kangli.qms.dto.LoginDTO;
import com.kangli.qms.dto.RefreshDTO;
import com.kangli.qms.dto.ChangePasswordDTO;
import com.kangli.qms.vo.LoginVO;
import com.kangli.qms.vo.UserInfoVO;

/**
 * 认证服务接口。
 */
public interface AuthService {

    /**
     * 登录：校验账号密码 → 生成双 Token → 写入 Redis/日志。
     *
     * @param dto    登录请求
     * @param loginIp 登录IP
     * @return 登录响应（含双 Token + 用户信息）
     */
    LoginVO login(LoginDTO dto, String loginIp);

    /**
     * 刷新 Token：校验 Refresh Token → 生成新的双 Token（旧的即时失效）。
     *
     * @param dto 刷新请求
     * @return 登录响应（新双 Token + 用户信息）
     */
    LoginVO refresh(RefreshDTO dto);

    /**
     * 退出登录：Access Token 加入黑名单 + Refresh Token 从 Redis 删除（双向失效）。
     *
     * @param accessToken  当前 Access Token（加黑名单）
     * @param refreshToken 当前 Refresh Token（可选，传则从 Redis 删除使其立即失效）
     */
    void logout(String accessToken, String refreshToken);

    void changePassword(ChangePasswordDTO dto);

    /**
     * 获取当前登录用户信息（从 ThreadLocal 取身份，查库补全）。
     *
     * @return 用户信息 VO
     */
    UserInfoVO getCurrentUser();
}
