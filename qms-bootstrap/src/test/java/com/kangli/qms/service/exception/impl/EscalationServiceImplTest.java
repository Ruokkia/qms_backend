package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.vo.EscalationCheckResultVO;
import com.kangli.qms.domain.exception.vo.TriggeredSupplierVO;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.enums.PlantCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EscalationServiceImplTest {

    @AfterEach
    void clearLoginUser() {
        LoginUserHolder.clear();
    }

    @Test
    void checkEscalation_excludesRowsWithoutMappedSupplier() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, supplierMapper);
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        TriggeredSupplierVO mapped = candidate(11L, "SUP-SZ-01", "1,2,3");
        TriggeredSupplierVO unmapped = candidate(null, "SUP-SZD-01", "4,5,6");
        when(exceptionOrderMapper.selectRepeatExceptions(eq("SZ"), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(mapped, unmapped));

        EscalationCheckResultVO result = service.checkEscalation(null);

        assertEquals(1, result.getTotalChecked());
        assertEquals("SUP-SZ-01", result.getTriggeredSuppliers().get(0).getSupplierCode());
        assertEquals(Arrays.asList(1L, 2L, 3L), result.getTriggeredSuppliers().get(0).getRelatedExceptionIds());
    }

    private TriggeredSupplierVO candidate(Long supplierId, String supplierCode, String exceptionIds) {
        TriggeredSupplierVO candidate = new TriggeredSupplierVO();
        candidate.setSupplierId(supplierId);
        candidate.setSupplierCode(supplierCode);
        candidate.setMaterialCode("MAT-001");
        candidate.setRepeatCount(3);
        candidate.setRelatedExceptionIdsStr(exceptionIds);
        return candidate;
    }
}
