package com.kangli.qms.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalKeyConstraintMigrationTest {

    @Test
    void migrationDefinesPartialUniqueKeysForActiveReportsAndTraceBindings() throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V20260730104000001__enforce_finished_goods_and_trace_natural_keys.sql");

        assertNotNull(input, "应提供自然键唯一约束迁移脚本");
        String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.contains("create unique index")
                        && sql.contains("finished_goods_inspection")
                        && sql.contains("plant_code, report_no")
                        && sql.contains("where is_deleted = 0"),
                "有效成品报告编号必须在同一分公司内唯一");
        assertTrue(sql.contains("create unique index")
                        && sql.contains("trace_relation")
                        && sql.contains("plant_code, parent_node_id, child_node_id"),
                "同一分公司内的追溯父子绑定必须唯一");
    }
}
