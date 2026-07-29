package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.domain.exception.mapper.ImprovementActionMapper;
import com.kangli.qms.service.exception.ImprovementActionService;
import org.springframework.stereotype.Service;

@Service
public class ImprovementActionServiceImpl extends ServiceImpl<ImprovementActionMapper, ImprovementAction> implements ImprovementActionService {
}
