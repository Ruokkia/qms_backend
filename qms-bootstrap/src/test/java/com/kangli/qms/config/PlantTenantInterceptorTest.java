package com.kangli.qms.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantTenantInterceptorTest {

    @Test
    void ignoresGlobalNotificationConfigTable() throws Exception {
        Field field = PlantTenantInterceptor.class.getDeclaredField("IGNORE_TABLES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> ignoredTables = (Set<String>) field.get(null);

        assertTrue(ignoredTables.contains("notification_config"));
    }
}
