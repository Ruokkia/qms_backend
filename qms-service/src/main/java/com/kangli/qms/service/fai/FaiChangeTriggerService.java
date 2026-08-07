package com.kangli.qms.service.fai;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.fai.dto.CreateChangeTriggerRequest;
import com.kangli.qms.service.fai.dto.FaiChangeTriggerQuery;
import com.kangli.qms.service.fai.dto.FaiChangeTriggerResponse;

/**
 * M3 首件检验变更触发 Service。
 */
public interface FaiChangeTriggerService {

    /**
     * 创建变更触发（状态默认 待检验）。
     */
    FaiChangeTriggerResponse create(CreateChangeTriggerRequest request, LoginUser loginUser);

    /**
     * 分页查询变更触发（按分公司隔离）。
     */
    PageResult<FaiChangeTriggerResponse> page(FaiChangeTriggerQuery query, String plantCode);

    /**
     * 变更触发详情。
     */
    FaiChangeTriggerResponse detail(Long id);

    /**
     * 作废变更触发（仅草稿态：status=待检验 且未关联检验单）。
     * @param id       变更触发主键
     * @param reason   作废原因
     * @param loginUser 当前操作人
     * @throws BusinessException 若已建单（hasInspection=true）则拒绝作废
     */
    void voidTrigger(Long id, String reason, LoginUser loginUser);
}
