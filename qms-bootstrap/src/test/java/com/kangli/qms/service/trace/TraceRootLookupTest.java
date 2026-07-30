package com.kangli.qms.service.trace;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.enums.PlantCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceRootLookupTest {

    @Test
    void resolvesFinishedGoodsRootByMasterRecordId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(88L), eq("SZ")))
                .thenReturn("FG-20260730-001");
        IncomingTraceService service = new IncomingTraceService(jdbc);

        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());
        try {
            assertEquals("FG-20260730-001", service.rootBarcode("FINISHED_GOODS", 88L));
        } finally {
            LoginUserHolder.clear();
        }
    }

    @Test
    void resolvesMaterialRootByMasterRecordId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(125L), eq("SZ")))
                .thenReturn("MAT-20260730-001");
        IncomingTraceService service = new IncomingTraceService(jdbc);

        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());
        try {
            assertEquals("MAT-20260730-001", service.rootBarcode("MATERIAL", 125L));
        } finally {
            LoginUserHolder.clear();
        }
    }
}
