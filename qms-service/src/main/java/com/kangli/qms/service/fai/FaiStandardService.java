package com.kangli.qms.service.fai;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSaveRequest;

import java.util.List;

/**
 * M3 首件检验标准模板 Service。
 */
public interface FaiStandardService {

    /**
     * 标准模板列表（按分公司隔离）。
     */
    List<FaiStandardResponse> list(String plantCode);

    /**
     * 查询某 物料+工序 的最新激活标准（含参数项）。
     */
    FaiStandardResponse latestActive(String materialCode, String processName, String plantCode);

    /**
     * 新增标准模板（含参数项）。返回新标准 id。
     */
    Long createStandard(FaiStandardSaveRequest req, LoginUser loginUser);

    /**
     * 更新标准模板（含参数项）。覆盖式更新参数项明细。
     */
    void updateStandard(Long id, FaiStandardSaveRequest req, LoginUser loginUser);

    /**
     * 逻辑删除标准模板（含参数项）。
     */
    void deleteStandard(Long id, LoginUser loginUser);

    /**
     * 按 id 查询标准模板（含参数项），不存在返回 null。
     */
    FaiStandardResponse getStandard(Long id);
}
