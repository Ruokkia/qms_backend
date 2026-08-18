package com.kangli.qms.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PermissionResolver 全量路由矩阵测试（对应测试方案 XC-001 ~ XC-008）。
 *
 * <p>设计意图：每个受保护首段都必须映射到正确模块；写操作映射 EDIT；关单/升级审核映射 APPROVE；
 * 本人通知已读降级 VIEW；非法前缀/未映射抛异常。另验证 EXPORT 动作不可达（疑似缺陷 D12）。</p>
 */
class PermissionResolverMatrixTest {

    private final PermissionResolver resolver = new PermissionResolver();

    /** 与 PermissionResolver.MODULES 一致的全量 firstSegment -> moduleCode 映射。 */
    private static final List<String[]> MAPPING = Arrays.asList(
            new String[]{"trace", "trace"},
            new String[]{"incoming-trace", "trace"},
            new String[]{"fai", "fai"},
            new String[]{"spc", "spc"},
            new String[]{"finished-goods", "finishedGoods"},
            new String[]{"suppliers", "supplier"},
            new String[]{"supplier-assessments", "supplier"},
            new String[]{"tooling", "tooling"},
            new String[]{"material-bindings", "material"},
            new String[]{"material-inspections", "incoming"},
            new String[]{"exceptions", "exception"},
            new String[]{"escalations", "exception"},
            new String[]{"improvement-actions", "exception"},
            new String[]{"rectification-plans", "exception"},
            new String[]{"verification-records", "exception"},
            new String[]{"production-defect-analytics", "productionDefect"},
            new String[]{"production-repairs", "productionDefect"},
            new String[]{"notifications", "notification"}
    );

    private static String prefix(String firstSegment) {
        return "incoming-trace".equals(firstSegment) ? "/api/v2/" : "/api/v1/";
    }

    static Stream<Arguments> firstSegments() {
        return MAPPING.stream().map(a -> Arguments.of(a[0], a[1]));
    }

    /** XC-001：全量首段 GET -> moduleCode + VIEW。 */
    @ParameterizedTest
    @MethodSource("firstSegments")
    void xc001_getMapsToView(String firstSegment, String moduleCode) {
        PermissionRequirement r = resolver.resolve(prefix(firstSegment) + firstSegment + "/x", "GET");
        assertEquals(moduleCode, r.getModuleCode());
        assertEquals(PermissionAction.VIEW, r.getAction());
    }

    /** XC-002：全量首段 POST/PUT/PATCH/DELETE -> moduleCode + EDIT。 */
    @ParameterizedTest
    @MethodSource("firstSegments")
    void xc002_writeMethodsMapToEdit(String firstSegment, String moduleCode) {
        for (String m : Arrays.asList("POST", "PUT", "PATCH", "DELETE")) {
            PermissionRequirement r = resolver.resolve(prefix(firstSegment) + firstSegment + "/x", m);
            assertEquals(moduleCode, r.getModuleCode(), firstSegment + " " + m);
            assertEquals(PermissionAction.EDIT, r.getAction(), firstSegment + " " + m);
        }
    }

    /** XC-003：异常关单 -> CLOSE，升级审核 -> ESCALATION_REVIEW，升级关单 -> ESCALATION_CLOSE（细粒度操作）。 */
    @Test
    void xc003_fineGrainedExceptionActions() {
        assertEquals(PermissionAction.CLOSE, resolver.resolve("/api/v1/exceptions/12/close", "POST").getAction());
        assertEquals(PermissionAction.ESCALATION_REVIEW, resolver.resolve("/api/v1/escalations/8/review", "POST").getAction());
        assertEquals(PermissionAction.ESCALATION_CLOSE, resolver.resolve("/api/v1/escalations/8/close", "POST").getAction());
        assertEquals(PermissionAction.RESET, resolver.resolve("/api/v1/exceptions/12/reset", "POST").getAction());
        assertEquals(PermissionAction.EDIT_8D, resolver.resolve("/api/v1/exceptions/12/eight-d", "PUT").getAction());
        assertEquals(PermissionAction.NEXT_STEP_8D, resolver.resolve("/api/v1/exceptions/12/eight-d/next-step", "POST").getAction());
    }

    /** XC-004：本人通知已读 / 全部已读 -> VIEW。 */
    @Test
    void xc004_personalNotificationReadMapsToView() {
        assertEquals(PermissionAction.VIEW, resolver.resolve("/api/v1/notifications/12/read", "POST").getAction());
        assertEquals(PermissionAction.VIEW, resolver.resolve("/api/v1/notifications/read-all", "POST").getAction());
    }

    /** XC-005：admin 首段 -> systemAdmin，GET->VIEW、POST->EDIT。 */
    @Test
    void xc005_adminMapsToSystemAdmin() {
        assertEquals("systemAdmin", resolver.resolve("/api/v1/admin/users", "GET").getModuleCode());
        assertEquals(PermissionAction.VIEW, resolver.resolve("/api/v1/admin/users", "GET").getAction());
        assertEquals(PermissionAction.EDIT, resolver.resolve("/api/v1/admin/users", "POST").getAction());
    }

    /** XC-006：非 /api/v1/ 或 /api/v2/ 前缀 -> IllegalArgumentException。 */
    @Test
    void xc006_unsupportedPrefixThrows() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("/other/v1/foo", "GET"));
    }

    /** XC-007：未映射首段 -> IllegalArgumentException。 */
    @Test
    void xc007_unmappedFirstSegmentThrows() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("/api/v1/foobar", "GET"));
    }

    /**
     * XC-008：EXPORT 动作不可达（疑似缺陷 D12）。
     * actionFor 永不返回 EXPORT，遍历全量首段 × 全部方法，确认无任何路径解析为 EXPORT。
     */
    @Test
    void xc008_exportActionIsUnreachable() {
        List<String> methods = Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE");
        for (String[] a : MAPPING) {
            String fs = a[0];
            String p = prefix(fs) + fs + "/x";
            for (String m : methods) {
                assertNotEquals(PermissionAction.EXPORT, resolver.resolve(p, m).getAction(),
                        fs + " " + m + " 不应解析为 EXPORT");
            }
        }
        for (String m : methods) {
            assertNotEquals(PermissionAction.EXPORT, resolver.resolve("/api/v1/admin/users", m).getAction());
        }
    }
}
