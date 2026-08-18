package com.kangli.qms.service.incoming.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendRowVO;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.exception.event.UnqualifiedInspectionEvent;
import com.kangli.qms.service.incoming.MaterialInspectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MaterialInspectionServiceImpl
        extends ServiceImpl<MaterialInspectionMapper, MaterialInspection>
        implements MaterialInspectionService {

    private final ExceptionService exceptionService;
    private final ApplicationEventPublisher eventPublisher;

    public MaterialInspectionServiceImpl(ExceptionService exceptionService,
                                         ApplicationEventPublisher eventPublisher) {
        this.exceptionService = exceptionService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public MaterialInspectionStatsVO stats() {
        String plantCode = getCurrentLoginUser().getPlantCode().name();
        MaterialInspectionStatsVO stats = baseMapper.selectStatsBase(plantCode);
        if (stats == null) {
            stats = new MaterialInspectionStatsVO();
            stats.setTotalBatches(0);
            stats.setQualifiedBatches(0);
            stats.setUnqualifiedBatches(0);
            stats.setQualifiedRate(BigDecimal.ZERO);
            stats.setPendingReviewCount(0);
            stats.setUrgentCount(0);
        }
        stats.setTopDefectDesc(baseMapper.selectTopDefectDesc(plantCode));
        stats.setSupplierRank(baseMapper.selectSupplierRank(plantCode, null, null));
        stats.setDailyTrend(baseMapper.selectDailyTrend(plantCode));
        return stats;
    }

    @Override
    @Transactional
    public MaterialInspection saveWithException(MaterialInspection record, boolean autoCreateException) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        baseMapper.insert(record);
        // 检验不合格 → 发布事件，由 ExceptionTriggerListener 异步实时派生异常单（解耦检验业务与异常生成）。
        if ("不合格".equals(record.getInspectionResult())) {
            eventPublisher.publishEvent(UnqualifiedInspectionEvent.fromMaterial(record));
        }
        return record;
    }

    @Override
    @Transactional
    public boolean save(MaterialInspection entity) {
        return super.save(entity);
    }

    @Override
    public MaterialInspection getByBarcode(String barcode) {
        return lambdaQuery()
                .eq(MaterialInspection::getMaterialBarcode, barcode)
                .eq(MaterialInspection::getPlantCode, getCurrentLoginUser().getPlantCode().name())
                .orderByDesc(MaterialInspection::getUpdatedAt)
                .list()
                .stream()
                .filter(e -> e.getIsDeleted() == null || e.getIsDeleted() != 1)
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public boolean updateById(MaterialInspection entity) {
        MaterialInspection before = getById(entity.getId());
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "物料检验记录不存在：" + entity.getId());
        }
        String oldResult = before.getInspectionResult();
        boolean updated = super.updateById(entity);
        if (!updated) {
            return false;
        }
        MaterialInspection after = getById(entity.getId());
        // 检验不合格（含合格→不合格状态变更）→ 发布事件，异步实时派生异常单。
        if ("不合格".equals(after.getInspectionResult())) {
            eventPublisher.publishEvent(UnqualifiedInspectionEvent.fromMaterial(after));
        }
        return true;
    }

    @Override
    public KeySupplierTrendVO keySupplierTrend(int topN, String startDate, String endDate) {
        String plantCode = getCurrentLoginUser().getPlantCode().name();
        List<SupplierRankItemVO> top = baseMapper.selectKeySuppliers(plantCode, topN, startDate, endDate);
        List<KeySupplierTrendRowVO> rows = baseMapper.selectKeySupplierTrend(plantCode, topN, startDate, endDate);

        KeySupplierTrendVO vo = new KeySupplierTrendVO();
        List<KeySupplierTrendVO.KeySupplierItemVO> items = new ArrayList<>();
        for (SupplierRankItemVO supplier : top) {
            KeySupplierTrendVO.KeySupplierItemVO item = new KeySupplierTrendVO.KeySupplierItemVO();
            item.setSupplierCode(supplier.getSupplierCode());
            item.setSupplierName(supplier.getSupplierName());
            item.setTotalBatches(supplier.getTotalBatches());
            item.setPassRate(supplier.getPassRate());
            items.add(item);
        }
        vo.setKeySuppliers(items);

        List<String> dates = new ArrayList<>();
        Map<String, KeySupplierTrendVO.KeySupplierSeriesVO> seriesMap = new LinkedHashMap<>();
        for (KeySupplierTrendRowVO row : rows) {
            if (!dates.contains(row.getDate())) {
                dates.add(row.getDate());
            }
            KeySupplierTrendVO.KeySupplierSeriesVO series = seriesMap.computeIfAbsent(row.getSupplierCode(), key -> {
                KeySupplierTrendVO.KeySupplierSeriesVO created = new KeySupplierTrendVO.KeySupplierSeriesVO();
                created.setSupplierCode(row.getSupplierCode());
                created.setSupplierName(row.getSupplierName());
                created.setPassRateList(new ArrayList<>());
                return created;
            });
            series.getPassRateList().add(row.getPassRate());
        }
        vo.setDates(dates);
        vo.setSeries(new ArrayList<>(seriesMap.values()));
        return vo;
    }

    @Override
    public List<SupplierRankItemVO> supplierRank(String startDate, String endDate) {
        return baseMapper.selectSupplierRank(getCurrentLoginUser().getPlantCode().name(), startDate, endDate);
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
