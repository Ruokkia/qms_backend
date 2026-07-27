package com.kangli.qms.security;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final PermissionResolver resolver = new PermissionResolver();
    private final PermissionService permissionService;

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if ("/api/v1/auth/me".equals(request.getRequestURI()) || "/api/v1/auth/logout".equals(request.getRequestURI())) return true;
        LoginUser user = LoginUserHolder.get();
        if (user == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        try {
            PermissionRequirement requirement = resolver.resolve(request.getRequestURI(), request.getMethod());
            if (!permissionService.hasPermission(user.getRoleCode(), requirement)) {
                throw new BusinessException(ResultCode.FORBIDDEN, permissionDeniedMessage(requirement));
            }
            return true;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.FORBIDDEN, "接口尚未配置权限");
        }
    }

    private String permissionDeniedMessage(PermissionRequirement requirement) {
        if ("exception".equals(requirement.getModuleCode()) && requirement.getAction() == PermissionAction.EDIT) {
            return "当前操作需要「异常管理-维护」权限。可执行角色：检验员、质量工程师、SQE、质量经理、超级管理员。";
        }
        if ("exception".equals(requirement.getModuleCode()) && requirement.getAction() == PermissionAction.APPROVE) {
            return "当前操作需要「异常管理-审批」权限。可执行角色：质量经理、超级管理员。";
        }
        return "无权执行该操作，所需权限：" + requirement.getModuleCode() + "-" + requirement.getAction();
    }
}
