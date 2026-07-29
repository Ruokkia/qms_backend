package com.kangli.qms.service.spc;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.service.spc.dto.SpcParameterRequest;
import com.kangli.qms.service.spc.dto.SpcParameterResponse;

import java.util.List;

/**
 * M4 SPC 关键参数管理 Service。
 */
public interface SpcParameterService {

    /** 参数列表（按工序 + 分公司过滤） */
    List<SpcParameterResponse> list(Long processId, String plantCode);

    /** 参数详情 */
    SpcParameterResponse detail(Long id);

    /** 创建参数 */
    SpcParameterResponse create(SpcParameterRequest request, LoginUser loginUser);

    /** 更新参数（乐观锁） */
    SpcParameterResponse update(Long id, SpcParameterRequest request, LoginUser loginUser);

    /** 删除参数（逻辑删除） */
    void remove(Long id);
}
