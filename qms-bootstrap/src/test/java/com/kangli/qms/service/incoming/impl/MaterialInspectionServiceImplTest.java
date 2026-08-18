package com.kangli.qms.service.incoming.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.ExceptionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.context.ApplicationEventPublisher;


/**
 * P0 缺口补测：M1 来料检验业务逻辑（plantCode 注入 / 不合格自动建单 / updateById 防重）。
 *
 * <p>对应 test-plan §18.6 真正缺失项 #2。来料创建与 updateById 的不合格自动建单路径
 * 此前仅在跨模块集成测试（CreateFromMaterialInspectionTest）间接覆盖，Service 自身无单测。</p>
 *
 * <p>覆盖维度：
 * <ul>
 *   <li>saveWithException：自动注入 plantCode/plantName/createdBy/updatedBy；不合格且 autoCreateException=true 触发建单；
 *       合格或 autoCreateException=false 不建单。</li>
 *   <li>updateById：记录不存在抛 NOT_FOUND；最终状态为不合格时触发建单；合格→不合格仅触发一次（幂等靠 sourceId+sourceType 防重）。</li>
 *   <li>getCurrentLoginUser：未登录或 plantCode 缺失抛 UNAUTHORIZED。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("M1 来料检验业务逻辑")
class MaterialInspectionServiceImplTest {

    @Mock
    private MaterialInspectionMapper mapper;
    @Mock
    private ExceptionService exceptionService;

    private MaterialInspectionServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "init");
        TableInfoHelper.initTableInfo(assistant, MaterialInspection.class);
    }

    @BeforeEach
    void setUp() {
        service = new MaterialInspectionServiceImpl(exceptionService, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LoginUserHolder.set(LoginUser.builder()
                .userId(1L).account("sz-user").realName("张三").plantCode(PlantCode.SZ).build());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    // ===================== saveWithException =====================

    @Test
    @DisplayName("M1-010 saveWithException：自动注入 plantCode/plantName/createdBy/updatedBy")
    void saveWithException_injectsPlantContext() {
        when(mapper.insert(any(MaterialInspection.class))).thenReturn(1);

        MaterialInspection rec = new MaterialInspection();
        rec.setInspectionResult("合格");
        rec.setSupplierCode("SUP-1");
        rec.setMaterialBatchNo("B-1");

        service.saveWithException(rec, false);

        assertEquals("SZ", rec.getPlantCode());
        assertEquals("深圳", rec.getPlantName());
        assertEquals("张三", rec.getCreatedBy());
        assertEquals("张三", rec.getUpdatedBy());
        verify(mapper, times(1)).insert(any(MaterialInspection.class));
    }

    @Test
    @DisplayName("M1-010 saveWithException：不合格 + autoCreate=true 自动建异常单")
    void saveWithException_unqualified_autoCreate() {
        when(mapper.insert(any(MaterialInspection.class))).thenReturn(1);

        MaterialInspection rec = new MaterialInspection();
        rec.setInspectionResult("不合格");
        rec.setUnqualifiedQty(new BigDecimal("3"));
        rec.setSupplierCode("SUP-1");
        rec.setMaterialBatchNo("B-2");

        service.saveWithException(rec, true);
        verify(exceptionService, times(1)).createFromMaterialInspection(any(MaterialInspection.class), any(LoginUser.class));
    }

    @Test
    @DisplayName("M1-010 saveWithException：不合格但 autoCreate=false 不建单")
    void saveWithException_unqualified_noAutoCreate() {
        when(mapper.insert(any(MaterialInspection.class))).thenReturn(1);

        MaterialInspection rec = new MaterialInspection();
        rec.setInspectionResult("不合格");
        rec.setUnqualifiedQty(new BigDecimal("3"));

        service.saveWithException(rec, false);
        verify(exceptionService, never()).createFromMaterialInspection(any(), any());
    }

    @Test
    @DisplayName("M1-010 saveWithException：合格不建单")
    void saveWithException_qualified_noException() {
        when(mapper.insert(any(MaterialInspection.class))).thenReturn(1);

        MaterialInspection rec = new MaterialInspection();
        rec.setInspectionResult("合格");
        rec.setSupplierCode("SUP-1");

        service.saveWithException(rec, true);
        verify(exceptionService, never()).createFromMaterialInspection(any(), any());
    }

    // ===================== updateById =====================

    @Test
    @DisplayName("M1-011 updateById：记录不存在抛 NOT_FOUND")
    void updateById_notFound_throws() {
        when(mapper.selectById(999L)).thenReturn(null);

        MaterialInspection upd = new MaterialInspection();
        upd.setId(999L);
        upd.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateById(upd));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any());
    }

    @Test
    @DisplayName("M1-011 updateById：最终状态不合格触发建单")
    void updateById_unqualified_autoCreate() {
        MaterialInspection before = new MaterialInspection();
        before.setId(501L);
        before.setInspectionResult("合格");
        before.setIsDeleted((short) 0);
        when(mapper.selectById(501L)).thenReturn(before);
        when(mapper.updateById(any())).thenReturn(1);

        MaterialInspection after = new MaterialInspection();
        after.setId(501L);
        after.setInspectionResult("不合格");
        after.setUnqualifiedQty(new BigDecimal("2"));

        when(mapper.selectById(501L)).thenReturn(before).thenReturn(after);

        service.updateById(after);
        verify(exceptionService, times(1)).createFromMaterialInspection(any(MaterialInspection.class), any(LoginUser.class));
    }

    @Test
    @DisplayName("M1-011 updateById：合格不建单")
    void updateById_qualified_noException() {
        MaterialInspection before = new MaterialInspection();
        before.setId(502L);
        before.setInspectionResult("不合格");
        before.setIsDeleted((short) 0);
        when(mapper.selectById(502L)).thenReturn(before);
        when(mapper.updateById(any())).thenReturn(1);

        MaterialInspection after = new MaterialInspection();
        after.setId(502L);
        after.setInspectionResult("合格");

        when(mapper.selectById(502L)).thenReturn(before).thenReturn(after);

        service.updateById(after);
        verify(exceptionService, never()).createFromMaterialInspection(any(), any());
    }

    // ===================== 登录态边界 =====================

    @Test
    @DisplayName("M1-012 saveWithException：未注入登录态抛 UNAUTHORIZED")
    void saveWithException_noLogin_throws() {
        LoginUserHolder.clear();
        MaterialInspection rec = new MaterialInspection();
        rec.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveWithException(rec, false));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("M1-012 saveWithException：登录态缺 plantCode 抛 UNAUTHORIZED")
    void saveWithException_noPlantCode_throws() {
        LoginUserHolder.set(LoginUser.builder().userId(1L).account("sz-user").plantCode(null).build());
        MaterialInspection rec = new MaterialInspection();
        rec.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveWithException(rec, false));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    // ===================== 工具方法 =====================

    private MaterialInspection withId(Long id, String result) {
        MaterialInspection e = new MaterialInspection();
        e.setId(id);
        e.setInspectionResult(result);
        e.setIsDeleted((short) 0);
        return e;
    }
}
