package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.exception.entity.RectificationPlan;
import com.kangli.qms.domain.exception.mapper.RectificationPlanMapper;
import com.kangli.qms.service.exception.RectificationPlanService;
import org.springframework.stereotype.Service;

@Service
public class RectificationPlanServiceImpl extends ServiceImpl<RectificationPlanMapper, RectificationPlan>
        implements RectificationPlanService {
}
