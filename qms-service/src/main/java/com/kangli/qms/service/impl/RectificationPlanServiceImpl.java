package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.entity.RectificationPlan;
import com.kangli.qms.mapper.RectificationPlanMapper;
import com.kangli.qms.service.RectificationPlanService;
import org.springframework.stereotype.Service;

@Service
public class RectificationPlanServiceImpl extends ServiceImpl<RectificationPlanMapper, RectificationPlan>
        implements RectificationPlanService {
}
