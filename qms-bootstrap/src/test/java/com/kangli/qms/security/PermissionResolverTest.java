package com.kangli.qms.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionResolverTest {

    private final PermissionResolver resolver = new PermissionResolver();

    @Test
    void resolvesAdminRequestsAsSystemAdminPermissions() {
        PermissionRequirement requirement = resolver.resolve("/api/v1/admin/users", "POST");

        assertEquals("systemAdmin", requirement.getModuleCode());
        assertEquals(PermissionAction.EDIT, requirement.getAction());
    }

    @Test
    void allowsRoleDeletionForAnyoneWhoCanAccessSystemAdminMenu() {
        PermissionRequirement requirement = resolver.resolve("/api/v1/admin/roles/R08", "DELETE");

        assertEquals("systemAdmin", requirement.getModuleCode());
        assertEquals(PermissionAction.VIEW, requirement.getAction());
    }

    @Test
    void resolvesIncomingTraceV2ReadRequestsAsTraceViewPermissions() {
        PermissionRequirement requirement = resolver.resolve("/api/v2/incoming-trace/nodes", "GET");

        assertEquals("trace", requirement.getModuleCode());
        assertEquals(PermissionAction.VIEW, requirement.getAction());
    }

    @Test
    void rejectsUnmappedApiRequestsByDefault() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("/api/v1/unmapped/resource", "GET"));
    }

    @Test
    void mapsExistingNotificationAndRectificationRoutesToTheirBusinessModules() {
        assertEquals("notification", resolver.resolve("/api/v1/notifications/unread", "GET").getModuleCode());
        assertEquals("exception", resolver.resolve("/api/v1/rectification-plans/12", "PUT").getModuleCode());
    }

    @Test
    void mapsSupplierAssessmentAndToolingRoutesToTheirModules() {
        assertEquals("supplier", resolver.resolve("/api/v1/supplier-assessments", "GET").getModuleCode());
        assertEquals(PermissionAction.EDIT,
                resolver.resolve("/api/v1/supplier-assessments", "POST").getAction());
        assertEquals("tooling", resolver.resolve("/api/v1/tooling/12/records", "GET").getModuleCode());
        assertEquals(PermissionAction.EDIT,
                resolver.resolve("/api/v1/tooling/12/usage", "POST").getAction());
    }

    @Test
    void treatsMarkingOwnNotificationReadAsViewPermission() {
        assertEquals(PermissionAction.VIEW,
                resolver.resolve("/api/v1/notifications/12/read", "POST").getAction());
        assertEquals(PermissionAction.VIEW,
                resolver.resolve("/api/v1/notifications/read-all", "POST").getAction());
    }

    @Test
    void reservesExceptionClosureAndSupplierEscalationReviewForApproval() {
        assertEquals(PermissionAction.APPROVE,
                resolver.resolve("/api/v1/exceptions/12/close", "POST").getAction());
        assertEquals(PermissionAction.APPROVE,
                resolver.resolve("/api/v1/escalations/8/review", "POST").getAction());
    }
}
