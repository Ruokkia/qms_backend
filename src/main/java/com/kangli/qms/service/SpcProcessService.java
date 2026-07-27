package com.kangli.qms.service;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.dto.SpcProcessRequest;
import com.kangli.qms.dto.SpcProcessResponse;

import java.util.List;

/**
 * M4 SPC 工序管理 Service。
 */
public interface SpcProcessService {

    /** 工序列表（按分公司隔离） */
    List<SpcProcessResponse> list(String plantCode);

    /** 创建工序 */
    SpcProcessResponse create(SpcProcessRequest request, LoginUser loginUser);

    /** 更新工序（乐观锁） */
    SpcProcessResponse update(Long id, SpcProcessRequest request, LoginUser loginUser);

    /** 删除工序（逻辑删除） */
    void remove(Long id);
}
