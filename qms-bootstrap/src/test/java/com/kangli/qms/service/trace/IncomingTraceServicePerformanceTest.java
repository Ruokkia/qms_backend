package com.kangli.qms.service.trace;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.CriticalMaterialBindingMapper;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.enums.PlantCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncomingTraceServicePerformanceTest {

    @AfterEach
    void clearUser() {
        LoginUserHolder.clear();
    }

    @Test
    void fullTraceLoadsBindingsOnceForMultiLevelChain() {
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        CriticalMaterialBindingMapper bindingMapper = mock(CriticalMaterialBindingMapper.class);
        FinishedGoodsInspectionMapper fgMapper = mock(FinishedGoodsInspectionMapper.class);
        MaterialInspectionMapper matMapper = mock(MaterialInspectionMapper.class);

        FinishedGoodsInspection root = fg(1L, "FG-ROOT", "成品");
        FinishedGoodsInspection semi = fg(2L, "SF-1", "半成品");
        when(fgMapper.selectList(any())).thenReturn(List.of(root, semi));
        when(matMapper.selectList(any())).thenReturn(List.of());
        when(fgMapper.selectCount(any())).thenReturn(2L);

        when(bindingMapper.selectList(any())).thenReturn(List.of(
                binding("FG-ROOT", "SF-1", "半成品"),
                binding("SF-1", "MAT-1", "物料")
        ));

        IncomingTraceService service = new IncomingTraceService(bindingMapper, fgMapper, matMapper);
        service.tree("FG-ROOT", "FULL");

        verify(bindingMapper, times(1)).selectList(any());
        // one root lookup + one batched target lookup; no per-level FG query remains.
        verify(fgMapper, times(2)).selectList(any());
        assertThat(service).isNotNull();
    }

    private static FinishedGoodsInspection fg(Long id, String barcode, String category) {
        FinishedGoodsInspection fg = new FinishedGoodsInspection();
        fg.setId(id);
        fg.setProdBatchOrSn(barcode);
        fg.setCategory(category);
        fg.setPlantCode("SZ");
        return fg;
    }

    private static CriticalMaterialBinding binding(String product, String material, String category) {
        CriticalMaterialBinding binding = new CriticalMaterialBinding();
        binding.setProductBarcode(product);
        binding.setMaterialBarcode(material);
        binding.setCategory(category);
        binding.setPlantCode("SZ");
        return binding;
    }
}
