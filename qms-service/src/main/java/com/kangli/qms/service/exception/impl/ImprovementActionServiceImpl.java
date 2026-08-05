package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.mapper.ImprovementActionMapper;
import com.kangli.qms.service.exception.ImprovementActionService;
import org.springframework.stereotype.Service;

@Service
public class ImprovementActionServiceImpl extends ServiceImpl<ImprovementActionMapper, ImprovementAction> implements ImprovementActionService {

    private final ExceptionOrderMapper exceptionOrderMapper;

    public ImprovementActionServiceImpl(ImprovementActionMapper improvementActionMapper,
                                        ExceptionOrderMapper exceptionOrderMapper) {
        this.exceptionOrderMapper = exceptionOrderMapper;
    }

    @Override
    public boolean save(ImprovementAction entity) {
        // 已闭环异常单禁止追加改善措施，避免闭环后数据污染
        if (entity.getExceptionId() != null) {
            ExceptionOrder order = exceptionOrderMapper.selectById(entity.getExceptionId());
            if (order != null && "已闭环".equals(order.getStatus())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "异常单已闭环，禁止追加改善措施");
            }
        }
        return super.save(entity);
    }
}
