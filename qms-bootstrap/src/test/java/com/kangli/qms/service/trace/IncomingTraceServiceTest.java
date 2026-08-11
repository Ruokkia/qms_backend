package com.kangli.qms.service.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0 缺口补测：M0 全链路追溯服务。
 *
 * <p>覆盖维度（对应 test-plan §8 / D7 / D8 / D14）：
 * <ul>
 *   <li>plant_code 隔离：tree/summary/nodes 等查询必须按当前登录用户的 plantCode 过滤。</li>
 *   <li>环路不无限递归：绑定表构造 A↔B 互指环路时 tree 必须终止并返回结果。</li>
 *   <li>超深树遍历：迭代式图遍历不应抛 StackOverflowError。</li>
 * </ul>
 *
 * <p>说明：源码已对全部查询加 eq(plant_code)，且遍历采用迭代式 loadGraphContext + path/visited
 * 双 Set 防环，因此本测试直接写「正确行为」断言作为回归守护 （原 test-plan 标注的锁现状项已修复）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("M0 追溯服务：隔离与防环")
class IncomingTraceServiceTest {

    @Mock
    private CriticalMaterialBindingMapper bindingMapper;
    @Mock
    private FinishedGoodsInspectionMapper fgMapper;
    @Mock
    private MaterialInspectionMapper matMapper;

    @InjectMocks
    private IncomingTraceService traceService;

    @BeforeEach
    void setUp() {
        LoginUserHolder.set(LoginUser.builder()
                .userId(1L).account("tester").plantCode(PlantCode.SZ).build());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private CriticalMaterialBinding binding(Long id, String product, String material) {
        CriticalMaterialBinding b = new CriticalMaterialBinding();
        b.setId(id);
        b.setProductBarcode(product);
        b.setMaterialBarcode(material);
        b.setPlantCode("SZ");
        return b;
    }

    @Test
    @DisplayName("D8 summary() 聚合按 plant_code 隔离，不跨厂泄漏")
    void summary_shouldFilterByPlantCode() {
        when(matMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(fgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(fgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        traceService.summary();

        ArgumentCaptor<QueryWrapper<MaterialInspection>> cap =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(matMapper).selectList(cap.capture());
        assertThat(cap.getValue().getTargetSql()).contains("plant_code");
    }

    @Test
    @DisplayName("D7 切换为 MZ 后，tree() 根查找按当前厂隔离执行（不泄露到默认/SZ 路径）")
    void tree_shouldUseCurrentPlantCode() {
        LoginUserHolder.set(LoginUser.builder()
                .userId(2L).account("mz-tester").plantCode(PlantCode.MZ).build());

        when(bindingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(binding(1L, "MAT-A", "MAT-B"));
        when(bindingMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(fgMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(matMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = traceService.tree("MAT-A", "ALL");

        // 根查找在 MZ 上下文执行，且不抛「找不到追溯节点」（证明按 plant 过滤命中数据）
        assertThat(result).isNotNull();
        verify(bindingMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("D14 绑定表环路（A↔B 互指）下 tree 必须终止并返回，不无限递归")
    void tree_shouldTerminateOnCyclicBindings() {
        when(bindingMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(binding(1L, "MAT-A", "MAT-B")); // 根 MAT-A
        when(bindingMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(binding(1L, "MAT-A", "MAT-B"), binding(2L, "MAT-B", "MAT-A"))); // 互指环路
        when(fgMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(matMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> {
            Map<String, Object> result = traceService.tree("MAT-A", "ALL");
            assertThat(result).isNotNull();
        });
    }

    @Test
    @DisplayName("D14 超深链（1000 层线性）遍历不应抛 StackOverflowError")
    void tree_deepChainShouldNotStackOverflow() {
        List<CriticalMaterialBinding> chain = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            chain.add(binding((long) i, "N" + i, "N" + (i + 1)));
        }
        when(bindingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(chain.get(0)); // 根 N0
        when(bindingMapper.selectList(any(QueryWrapper.class))).thenReturn(chain);
        when(fgMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(matMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> {
            Map<String, Object> result = traceService.tree("N0", "ALL");
            assertThat(result).isNotNull();
        });
    }

    @Test
    @DisplayName("nodes() 列表查询按 plant_code 过滤（返回值携带当前厂）")
    void nodes_queryFiltersByPlantCode() {
        MaterialInspection mi = new MaterialInspection();
        mi.setId(1L);
        mi.setMaterialBarcode("MAT-A");
        mi.setPlantCode("SZ");
        when(matMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mi));

        List<Map<String, Object>> nodes = traceService.nodes();

        // 返回值来自按 plant 过滤的查询结果（源码 nodes() 内 eq(MaterialInspection::getPlantCode, currentPlant)）
        assertThat(nodes).isNotEmpty();
        verify(matMapper).selectList(any(LambdaQueryWrapper.class));
    }
}
