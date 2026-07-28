package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.entity.FinishedGoodsInspection;
import com.kangli.qms.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.service.FinishedGoodsInspectionService;
import org.springframework.stereotype.Service;

@Service
public class FinishedGoodsInspectionServiceImpl
        extends ServiceImpl<FinishedGoodsInspectionMapper, FinishedGoodsInspection>
        implements FinishedGoodsInspectionService {
}
