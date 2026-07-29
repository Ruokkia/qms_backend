package com.kangli.qms.service.finishedgoods.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.service.finishedgoods.FinishedGoodsInspectionService;
import org.springframework.stereotype.Service;

@Service
public class FinishedGoodsInspectionServiceImpl
        extends ServiceImpl<FinishedGoodsInspectionMapper, FinishedGoodsInspection>
        implements FinishedGoodsInspectionService {
}
