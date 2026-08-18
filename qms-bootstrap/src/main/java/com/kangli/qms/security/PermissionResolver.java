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
        MODULES.put("supplier-audits", "supplierAudit");
        MODULES.put("supplier-assessments", "supplier");
        MODULES.put("supplier-material-changes", "supplierMaterialChange");
        MODULES.put("tooling", "tooling");
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
        MODULES.put("after-sales", "afterSales");
        MODULES.put("measurement", "measurement");
        MODULES.put("quality-system", "qualitySystem");
    }

    public PermissionRequirement resolve(String requestUri, String method) {
        if (requestUri == null || !(requestUri.startsWith("/api/v1/") || requestUri.startsWith("/api/v2/"))) {
            throw new IllegalArgumentException("Unsupported protected API path");
        }
        String prefix = requestUri.startsWith("/api/v2/") ? "/api/v2/" : "/api/v1/";
        String resource = requestUri.substring(prefix.length());
        String firstSegment = resource.contains("/") ? resource.substring(0, resource.indexOf('/')) : resource;
        if ("admin".equals(firstSegment)) {
            if (isRoleDeletion(requestUri, method)) {
                return new PermissionRequirement("systemAdmin", PermissionAction.VIEW);
            }
            return new PermissionRequirement("systemAdmin", actionFor(method));
        }
        String moduleCode = MODULES.get(firstSegment);
        if (moduleCode == null) {
            throw new IllegalArgumentException("No permission module mapping for " + requestUri);
        }
        if (isPersonalNotificationRead(requestUri, method)) {
            return new PermissionRequirement(moduleCode, PermissionAction.VIEW);
        }
        PermissionAction approvalAction = exceptionApprovalAction(requestUri, method);
        if (approvalAction != null) {
            return new PermissionRequirement(moduleCode, approvalAction);
        }
        return new PermissionRequirement(moduleCode, actionFor(method));
    }

    private boolean isRoleDeletion(String requestUri, String method) {
        return "DELETE".equalsIgnoreCase(method)
                && requestUri.matches("/api/v1/admin/roles/[^/]+");
    }

    /** 关单、升级审核/关单、8D 编辑/推进、重置等细粒度操作。 */
    private PermissionAction exceptionApprovalAction(String requestUri, String method) {
        // 8D 报告编辑（PUT）
        if ("PUT".equalsIgnoreCase(method) && requestUri.matches("/api/v1/exceptions/[^/]+/eight-d(?:/[^/]+)?")) {
            return PermissionAction.EDIT_8D;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }
        // 8D 下一步推进
        if (requestUri.matches("/api/v1/exceptions/[^/]+/eight-d/next-step")) {
            return PermissionAction.NEXT_STEP_8D;
        }
        if (requestUri.matches("/api/v1/exceptions/[^/]+/close")) {
            return PermissionAction.CLOSE;
        }
        if (requestUri.matches("/api/v1/escalations/[^/]+/review")) {
            return PermissionAction.ESCALATION_REVIEW;
        }
        if (requestUri.matches("/api/v1/escalations/[^/]+/close")) {
            return PermissionAction.ESCALATION_CLOSE;
        }
        if (requestUri.matches("/api/v1/exceptions/[^/]+/reset")) {
            return PermissionAction.RESET;
        }
        return null;
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
