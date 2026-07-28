package com.kangli.qms.service;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.dto.CreateChangeTriggerRequest;
import com.kangli.qms.dto.FaiChangeTriggerQuery;
import com.kangli.qms.dto.FaiChangeTriggerResponse;

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
}
