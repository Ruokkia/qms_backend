package com.kangli.qms.config;

import java.security.Principal;

/**
 * STOMP 会话身份，name 为 userId，用于 {@code /user/{userId}/...} 用户目的地路由。
 */
public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
