package com.kangli.qms.security;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionInterceptorTest {

    @Test
    void allowsAuthenticatedUsersToChangeTheirOwnPasswordWithoutMenuPermission() {
        PermissionService permissionService = mock(PermissionService.class);
        PermissionInterceptor interceptor = new PermissionInterceptor(permissionService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/change-password");
        when(request.getMethod()).thenReturn("POST");

        LoginUserHolder.set(LoginUser.builder().userId(7L).roleCode("R08").build());
        try {
            assertTrue(interceptor.preHandle(request, response, new Object()));
        } finally {
            LoginUserHolder.clear();
        }
    }
}
