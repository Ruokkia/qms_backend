package com.kangli.qms.api.auth;

import com.kangli.qms.common.R;
import com.kangli.qms.service.auth.dto.LoginDTO;
import com.kangli.qms.service.auth.dto.RefreshDTO;
import com.kangli.qms.service.auth.dto.ChangePasswordDTO;
import com.kangli.qms.service.auth.AuthService;
import com.kangli.qms.service.auth.CaptchaService;
import com.kangli.qms.domain.auth.vo.CaptchaVO;
import com.kangli.qms.domain.auth.vo.LoginVO;
import com.kangli.qms.domain.auth.vo.UserInfoVO;
import com.kangli.qms.domain.auth.vo.LoginDirectoryUserVO;
import com.kangli.qms.service.auth.LoginDirectoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 认证模块 Controller — 登录 / 刷新 / 登出 / 获取当前用户。
 *
 * <p>路径前缀：/api/v1/auth</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Api(tags = "认证模块")
public class AuthController {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final LoginDirectoryService loginDirectoryService;

    public AuthController(AuthService authService, CaptchaService captchaService, LoginDirectoryService loginDirectoryService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.loginDirectoryService = loginDirectoryService;
    }

    @GetMapping("/captcha")
    @ApiOperation(value = "获取图形验证码", notes = "返回 base64 图片 + captchaKey；登录时需回传 captchaKey + captcha")
    public R<CaptchaVO> captcha() {
        CaptchaVO vo = captchaService.generateCaptcha();
        return R.ok(vo);
    }

    @GetMapping("/directory")
    @ApiOperation(value = "开发环境账号目录", notes = "仅返回启用账号的展示字段，不返回密码、Token 或登录状态")
    public R<java.util.List<LoginDirectoryUserVO>> directory() {
        return R.ok(loginDirectoryService.listEnabledUsers());
    }

    @PostMapping("/login")
    @ApiOperation(value = "登录", notes = "校验账号密码 → 生成双 Token（Access 2h + Refresh 7d）→ 返回用户信息")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String loginIp = getClientIp(request);
        LoginVO vo = authService.login(dto, loginIp);
        return R.ok(vo, "登录成功");
    }

    @PostMapping("/refresh")
    @ApiOperation(value = "刷新Token", notes = "通过 Refresh Token 静默刷新，返回新的双 Token（旧的 Refresh Token 即时失效）")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        LoginVO vo = authService.refresh(dto);
        return R.ok(vo, "Token刷新成功");
    }

    @PostMapping("/logout")
    @ApiOperation(value = "退出登录", notes = "将当前 Access Token 加入黑名单，并删除 Redis 中的 Refresh Token（需通过 X-Refresh-Token 头传入），实现双向失效")
    public R<Void> logout(@RequestHeader(value = AUTH_HEADER, required = false) String authHeader,
                          @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshToken) {
        String token = extractToken(authHeader);
        String refresh = StringUtils.hasText(refreshToken) ? refreshToken.trim() : null;
        authService.logout(token, refresh);
        return R.ok(null, "已退出登录");
    }

    @PostMapping("/change-password")
    @ApiOperation(value = "修改当前用户密码", notes = "校验当前密码后更新密码，并使全部旧会话失效")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok(null, "密码已修改，请重新登录");
    }

    @GetMapping("/me")
    @ApiOperation(value = "获取当前用户信息", notes = "从 JWT 解析用户身份，查询数据库返回完整用户信息")
    public R<UserInfoVO> me() {
        UserInfoVO vo = authService.getCurrentUser();
        return R.ok(vo);
    }

    // ============ 工具方法 ============

    /**
     * 从 Authorization 头提取 Bearer Token。
     */
    private String extractToken(String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length()).trim();
    }

    /**
     * 获取客户端真实 IP（穿透代理）。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            if (idx > 0) {
                return ip.substring(0, idx).trim();
            }
            return ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
