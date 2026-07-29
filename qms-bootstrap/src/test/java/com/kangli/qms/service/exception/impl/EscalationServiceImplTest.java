package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.domain.exception.vo.EscalationCheckResultVO;
import com.kangli.qms.domain.exception.vo.TriggeredSupplierVO;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.dto.EscalationCreateDTO;
import com.kangli.qms.service.exception.dto.EscalationPlanDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
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

    @Test
    void create_rejectsUnknownSupplierBeforePersistingEscalation() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, supplierMapper);
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        when(supplierMapper.selectById(999L)).thenReturn(null);

        EscalationCreateDTO dto = new EscalationCreateDTO();
        dto.setSupplierId(999L);
        dto.setEscalationReason("重复来料异常");
        dto.setEscalationAction("加密审核");

        assertThrows(RuntimeException.class, () -> service.create(dto));
    }

    @Test
    void create_usesSupplierMasterAndPendingReviewStatus() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = spy(new EscalationServiceImpl(exceptionOrderMapper, supplierMapper));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        Supplier supplier = new Supplier();
        supplier.setId(11L);
        supplier.setSupplierCode("SUP-SZ-01");
        supplier.setSupplierName("深圳电子元件有限公司");
        supplier.setPlantCode("SZ");
        supplier.setStatus("启用");
        when(supplierMapper.selectById(11L)).thenReturn(supplier);
        doReturn(true).when(service).save(any(Escalation.class));

        EscalationCreateDTO dto = new EscalationCreateDTO();
        dto.setSupplierId(11L);
        dto.setEscalationReason("重复来料异常");
        dto.setEscalationAction("加密审核");
        dto.setMaterialCode("MC-001");

        Escalation created = service.create(dto);

        assertEquals("11", created.getSupplierId());
        assertEquals("SUP-SZ-01", created.getSupplierCode());
        assertEquals("MC-001", created.getMaterialCode());
        assertEquals("PENDING_REVIEW", created.getStatus());
        assertEquals("PENDING_REVIEW", created.getProcessStage());
        verify(service).save(created);
    }

    @Test
    void savePlan_bindsCurrentLoginUserAsPlanWriter() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = spy(new EscalationServiceImpl(exceptionOrderMapper, supplierMapper));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage("PLAN");
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));
        EscalationPlanDTO dto = new EscalationPlanDTO();
        dto.setActionPlan("增加来料抽检频次");
        dto.setOwnerName("供应商质量工程师");

        Escalation result = service.savePlan(1L, dto);

        assertEquals("测试用户", result.getPlanFilledBy());
        verify(service).updateById(result);
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
