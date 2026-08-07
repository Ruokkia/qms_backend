package com.kangli.qms.service.incoming;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.service.incoming.dto.CriticalMaterialBindingResponse;

public interface CriticalMaterialBindingService extends IService<CriticalMaterialBinding> {

    CriticalMaterialBindingResponse detail(Long id);

    PageResult<CriticalMaterialBindingResponse> page(Page<CriticalMaterialBinding> page,
                                                      LambdaQueryWrapper<CriticalMaterialBinding> wrapper);

    CriticalMaterialBindingResponse create(CriticalMaterialBinding record, LoginUser loginUser);

    CriticalMaterialBindingResponse update(Long id, CriticalMaterialBinding record, LoginUser loginUser);

    void delete(Long id);
}
