package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分公司隔离数据级验证（test-plan L6 反向）：MZ 用户新建异常单（plant_code=MZ），
 * SZ 用户读不到（404），MZ 用户自己能读。@Transactional 回滚建单。
 */
class PlantIsolationDataIntegrationTest extends BaseIntegrationTest {

    @Test
    void mzCreatedException_invisibleToSz() throws Exception {
        // MZ 检验员建异常单（后端强制 plant_code=MZ）
        String mz = login("mz_insp01", "123456");
        String body = mvc.perform(post("/api/v1/exceptions")
                        .header("Authorization", "Bearer " + mz)
                        .contentType("application/json")
                        .content("{\"sourceType\":\"手动录入\",\"severity\":\"一般\",\"defectDesc\":\"MZ隔离测试\",\"materialCode\":\"MZ-MAT\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // SZ 用户读不到（plant_code=SZ 隔离）-> 404
        String sz = login("sz_insp01", "123456");
        mvc.perform(get("/api/v1/exceptions/" + id).header("Authorization", "Bearer " + sz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        // MZ 用户自己能读 -> 0
        mvc.perform(get("/api/v1/exceptions/" + id).header("Authorization", "Bearer " + mz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
