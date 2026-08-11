package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.domain.incoming.mapper.CriticalMaterialBindingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-010 / D7 回归：controller 层 getById 跨厂越权读。
 *
 * <p>D7 根因：历史 controller 的 getById / detail(id) 仅做裸查询，未补全 plantCode 过滤，
 * 导致 SZ 账号可越权读取 MZ 记录（数据隔离越权）。基础设施层 {@code PlantTenantInterceptor}
 * （MyBatis-Plus TenantLine）已对所有单表 SELECT（含 selectById/getById）自动追加
 * {@code AND plant_code = ?}，从底层统一补齐隔离。</p>
 *
 * <p>本测试回归两个被 D7 证据点名的 controller 端点：
 * <ul>
 *   <li>{@code GET /api/v1/material-bindings/{id}}</li>
 *   <li>{@code GET /api/v1/finished-goods/{id}}</li>
 * </ul>
 * 验证：MZ 创建/写入的记录，SZ 账号读取时受隔离保护返回 code=404（不泄漏），
 * 而 MZ 账号自身读取返回 code=0。</p>
 *
 * <p>@Transactional 回滚所有 DB 改动，不污染开发库 qms。Redis 由 BaseIntegrationTest.clearRedis() 清理。</p>
 */
class CrossPlantGetByIdRegressionTest extends BaseIntegrationTest {

    @Autowired
    private CriticalMaterialBindingMapper bindingMapper;
    @Autowired
    private FinishedGoodsInspectionMapper fgMapper;

    /**
     * material-bindings：MZ 账号（R06，拥有 material-EDIT）通过 X-Plant-Code 头指定 MZ 创建，
     * SZ 越权读应被隔离为 404。
     */
    @Test
    void mzCreatedBinding_invisibleToSzGetById() throws Exception {
        // 仅 R06 拥有 material-EDIT 权限；R06 可切换分公司，需用 X-Plant-Code 头固定 MZ 上下文
        String mz = login("mz_mgr01", "123456");
        String body = mvc.perform(post("/api/v1/material-bindings")
                        .header("Authorization", "Bearer " + mz)
                        .header("X-Plant-Code", "MZ")
                        .contentType("application/json")
                        .content("{\"workOrderNo\":\"MZ-WO-REG\",\"productBarcode\":\"MZ-SN-REG\",\"materialCode\":\"MZ-MAT-REG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        // SZ 账号越权读 -> 受隔离保护返回 404（不泄漏 MZ 数据）
        String sz = login("sz_insp01", "123456");
        mvc.perform(get("/api/v1/material-bindings/" + id).header("Authorization", "Bearer " + sz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        // MZ 账号自身读 -> 0（同厂隔离上下文下可读）
        mvc.perform(get("/api/v1/material-bindings/" + id)
                        .header("Authorization", "Bearer " + mz)
                        .header("X-Plant-Code", "MZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * finished-goods：MZ 记录经 mapper 直接写入（plantCode=MZ），SZ 越权读应被隔离为 404。
     * finished-goods controller 无 create 端点，故直接插入一条 MZ 记录以复现越权场景。
     */
    @Test
    void mzInsertedFinishedGoods_invisibleToSzGetById() throws Exception {
        // 直接插入一条 plantCode=MZ 的成品检验记录（拦截器对 INSERT 不注入 plantCode，由实体携带）
        FinishedGoodsInspection fg = new FinishedGoodsInspection();
        fg.setReportNo("MZ-FG-REG-" + System.nanoTime());
        fg.setProductName("MZ隔离回归");
        fg.setPlantCode("MZ");
        fg.setPlantName("梅州");
        fg.setCreatedBy("mz_insp01");
        fg.setUpdatedBy("mz_insp01");
        fgMapper.insert(fg);
        long id = fg.getId();

        // SZ 账号越权读 -> 受隔离保护返回 404（不泄漏 MZ 数据）
        String sz = login("sz_insp01", "123456");
        mvc.perform(get("/api/v1/finished-goods/" + id).header("Authorization", "Bearer " + sz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        // MZ 账号自身读 -> 0
        String mz = login("mz_insp01", "123456");
        mvc.perform(get("/api/v1/finished-goods/" + id).header("Authorization", "Bearer " + mz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
