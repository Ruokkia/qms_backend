package com.kangli.qms.security;

import java.util.HashMap;
import java.util.Map;

/** Resolves protected API paths to the fixed first-phase module/action model. */
public class PermissionResolver {

    private static final Map<String, String> MODULES = new HashMap<>();
    static {
        MODULES.put("trace", "trace");
        MODULES.put("incoming-trace", "trace");
        MODULES.put("fai", "fai");
        MODULES.put("spc", "spc");
        MODULES.put("finished-goods", "finishedGoods");
        MODULES.put("suppliers", "supplier");
        MODULES.put("material-bindings", "material");
        MODULES.put("material-inspections", "incoming");
        MODULES.put("exceptions", "exception");
        MODULES.put("escalations", "exception");
        MODULES.put("improvement-actions", "exception");
        MODULES.put("rectification-plans", "exception");
        MODULES.put("verification-records", "exception");
        MODULES.put("production-defect-analytics", "productionDefect");
        MODULES.put("production-repairs", "productionDefect");
        MODULES.put("notifications", "notification");
    }

    public PermissionRequirement resolve(String requestUri, String method) {
        if (requestUri == null || !(requestUri.startsWith("/api/v1/") || requestUri.startsWith("/api/v2/"))) {
            throw new IllegalArgumentException("Unsupported protected API path");
        }
        String prefix = requestUri.startsWith("/api/v2/") ? "/api/v2/" : "/api/v1/";
        String resource = requestUri.substring(prefix.length());
        String firstSegment = resource.contains("/") ? resource.substring(0, resource.indexOf('/')) : resource;
        if ("admin".equals(firstSegment)) {
            return new PermissionRequirement("systemAdmin", actionFor(method));
        }
        String moduleCode = MODULES.get(firstSegment);
        if (moduleCode == null) {
            throw new IllegalArgumentException("No permission module mapping for " + requestUri);
        }
        if (isPersonalNotificationRead(requestUri, method)) {
            return new PermissionRequirement(moduleCode, PermissionAction.VIEW);
        }
        if (isExceptionApproval(requestUri, method)) {
            return new PermissionRequirement(moduleCode, PermissionAction.APPROVE);
        }
        return new PermissionRequirement(moduleCode, actionFor(method));
    }

    /** 关单和供应商升级审核会改变质量结论，不能与日常整改录入共用 EDIT 权限。 */
    private boolean isExceptionApproval(String requestUri, String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return requestUri.matches("/api/v1/exceptions/[^/]+/close")
                || requestUri.matches("/api/v1/escalations/[^/]+/(review|close)");
    }

    /** Marking one's own notification read is a personal inbox action, not notification administration. */
    private boolean isPersonalNotificationRead(String requestUri, String method) {
        return "POST".equalsIgnoreCase(method)
                && (requestUri.matches("/api/v1/notifications/[^/]+/read")
                || "/api/v1/notifications/read-all".equals(requestUri));
    }

    private PermissionAction actionFor(String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return PermissionAction.VIEW;
        }
        if ("DELETE".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            return PermissionAction.EDIT;
        }
        throw new IllegalArgumentException("No permission action mapping for HTTP method " + method);
    }

}
