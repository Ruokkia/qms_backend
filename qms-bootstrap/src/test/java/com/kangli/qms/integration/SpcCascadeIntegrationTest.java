package com.kangli.qms.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.service.spc.SpcProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPC 级联删除集成测试（test-plan M4-010 / M4-034）。
 */
class SpcCascadeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SpcProcessService processService;
    @Autowired
    private SpcParameterMapper parameterMapper;

    private static final String PROC_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private String randProcCode() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(PROC_CHARS.charAt(r.nextInt(PROC_CHARS.length())));
        }
        return sb.toString();
    }

    @Test
    void deleteParameter_cascadesSubgroups() throws Exception {
        String token = login("sz_mgr01", "123456");
        // 建工序
        String body = mvc.perform(post("/api/v1/spc/processes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processCode\":\"ITP\",\"processName\":\"装配\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        long procId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // 建参数
        body = mvc.perform(post("/api/v1/spc/parameters")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processId\":" + procId + ",\"paramCode\":\"IT-PARAM-IT\",\"paramName\":\"尺寸\",\"paramType\":\"尺寸\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        long paramId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // 建 1 个子组（<2 不触发 recalc）
        mvc.perform(post("/api/v1/spc/subgroups")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"paramId\":" + paramId + ",\"sampleValues\":[10.0,10.1,10.2,10.3,10.4],\"itemType\":\"PRODUCT\",\"itemCode\":\"IT-PC\",\"batchNo\":\"IT-BATCH\",\"barcode\":\"IT-BAR\",\"materialName\":\"IT物料\"}"))
                .andExpect(status().isOk());
        // 删参数
        mvc.perform(delete("/api/v1/spc/parameters/" + paramId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // 子组应被级联清空
        mvc.perform(get("/api/v1/spc/subgroups?paramId=" + paramId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /**
     * M4-034：删除工序后，其下所有参数应被级联逻辑删除（is_deleted=1），
     * 参数列表不再返回该工序下的参数。
     * 通过 service 层触发级联（processService.remove），直接验证参数库内已被逻辑删除。
     */
    @Test
    void deleteProcess_cascadesParameters() throws Exception {
        String token = login("sz_mgr01", "123456");
        // 建工序（processCode 需 ^[A-Z]{2,5}$；随机 5 位大写字母保证唯一）
        String procCode = randProcCode();
        String body = mvc.perform(post("/api/v1/spc/processes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processCode\":\"" + procCode + "\",\"processName\":\"装配-M4-034\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        long procId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // 建 2 个参数
        createParam(token, procId, "PM3401" + System.currentTimeMillis());
        createParam(token, procId, "PM3402" + System.currentTimeMillis());

        // 删工序（级联删参数）
        processService.remove(procId);

        // 参数应被级联逻辑删除（is_deleted=1），列表查询（仅未删除）应返回空
        List<SpcParameter> remain = parameterMapper.selectList(Wrappers.lambdaQuery(SpcParameter.class)
                .eq(SpcParameter::getProcessId, procId)
                .eq(SpcParameter::getIsDeleted, 0));
        assertTrue(remain.isEmpty(), "删除工序后，其参数应被级联逻辑删除，列表查询应返回空");
    }

    private long createParam(String token, long procId, String code) throws Exception {
        String body = mvc.perform(post("/api/v1/spc/parameters")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processId\":" + procId + ",\"paramCode\":\"" + code + "\",\"paramName\":\"尺寸\",\"paramType\":\"尺寸\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
