package com.kangli.qms.service.finishedgoods.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.service.finishedgoods.FinishedGoodsInspectionService;
import com.kangli.qms.service.trace.IncomingTraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinishedGoodsInspectionServiceImpl
        extends ServiceImpl<FinishedGoodsInspectionMapper, FinishedGoodsInspection>
        implements FinishedGoodsInspectionService {

    private final IncomingTraceService incomingTraceService;

    public FinishedGoodsInspectionServiceImpl(IncomingTraceService incomingTraceService) {
        this.incomingTraceService = incomingTraceService;
    }

    @Override
    @Transactional
    public boolean save(FinishedGoodsInspection entity) {
        boolean saved = super.save(entity);
        if (saved) {
            incomingTraceService.syncFinishedGoodsNode(entity);
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateById(FinishedGoodsInspection entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            incomingTraceService.syncFinishedGoodsNode(getById(entity.getId()));
        }
        return updated;
    }
}
