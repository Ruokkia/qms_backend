package com.kangli.qms.service.incoming.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportDTO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportResultVO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionReconcileResultVO;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.incoming.MaterialInspectionService;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendVO;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendRowVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final TransactionTemplate requiresNewTemplate;

    public MaterialInspectionServiceImpl(ExceptionService exceptionService,
                                         PlatformTransactionManager transactionManager) {
        this.exceptionService = exceptionService;
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public MaterialInspectionStatsVO stats() {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();

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
        stats.setSupplierRank(baseMapper.selectSupplierRank(plantCode));
        stats.setDailyTrend(baseMapper.selectDailyTrend(plantCode));
        return stats;
    }

    @Override
    public MaterialInspectionImportResultVO importRecords(MaterialInspectionImportDTO dto) {
        LoginUser loginUser = getCurrentLoginUser();
        boolean autoCreateException = dto.getAutoCreateException() == null || dto.getAutoCreateException();

        MaterialInspectionImportResultVO result = new MaterialInspectionImportResultVO();
        List<MaterialInspectionImportResultVO.FailItem> failList = new ArrayList<>();
        List<Long> createdExceptionIds = new ArrayList<>();
        int successCount = 0;

        List<MaterialInspection> list = dto.getList();
        if (list == null) {
            list = new ArrayList<>();
        }

        int index = 0;
        for (MaterialInspection record : list) {
            final int currentIndex = index;
            try {
                // 每条记录独立事务提交，任一条失败只回滚自己，避免 PG aborted transaction 毒化整批
                Long exId = requiresNewTemplate.execute(status -> importSingle(record, currentIndex, loginUser, autoCreateException));
                successCount++;
                if (exId != null) {
                    createdExceptionIds.add(exId);
                }
            } catch (BusinessException e) {
                MaterialInspectionImportResultVO.FailItem item = new MaterialInspectionImportResultVO.FailItem();
                item.setIndex(index);
                item.setRecordNo(record.getRecordNo());
                item.setReason(e.getMessage());
                failList.add(item);
            } catch (Exception e) {
                MaterialInspectionImportResultVO.FailItem item = new MaterialInspectionImportResultVO.FailItem();
                item.setIndex(index);
                item.setRecordNo(record.getRecordNo());
                item.setReason("系统错误：" + e.getMessage());
                failList.add(item);
            }
            index++;
        }

        result.setTotalCount(list.size());
        result.setSuccessCount(successCount);
        result.setFailCount(failList.size());
        result.setFailList(failList);
        result.setCreatedExceptionCount(createdExceptionIds.size());
        result.setCreatedExceptionIds(createdExceptionIds);

        log.info("物料检验导入完成：total={}, success={}, fail={}, createdException={}",
                list.size(), successCount, failList.size(), createdExceptionIds.size());
        return result;
    }

    /**
     * 单条物料检验记录导入：在调用方提供的 REQUIRES_NEW 事务内原子执行。
     * 校验 + 入库 + （不合格时）建异常单 在同一事务内提交，
     * 后续记录可看到前序已提交记录，从而避免同批次内单号重复与事务毒化。
     * 事务边界由 importRecords 中的 TransactionTemplate 控制，此处不再加 @Transactional。
     */
    private Long importSingle(MaterialInspection record, int index, LoginUser loginUser, boolean autoCreateException) {
        validateImportRecord(record, index);
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        baseMapper.insert(record);

        Long exId = null;
        if ("不合格".equals(record.getInspectionResult()) && autoCreateException) {
            ExceptionOrder order = exceptionService.createFromMaterialInspection(record, loginUser);
            exId = order.getId();
        }
        return exId;
    }

    @Override
    @Transactional
    public MaterialInspectionReconcileResultVO reconcile(String startDate, String endDate, String plantCode) {
        LoginUser loginUser = getCurrentLoginUser();
        String effectivePlantCode = plantCode != null ? plantCode : loginUser.getPlantCode().name();

        List<MaterialInspection> unqualifiedList = findUnlinkedUnqualified(startDate, endDate, effectivePlantCode);
        List<Long> createdExceptionIds = new ArrayList<>();

        for (MaterialInspection record : unqualifiedList) {
            ExceptionOrder order = exceptionService.createFromMaterialInspection(record, loginUser);
            createdExceptionIds.add(order.getId());
        }

        MaterialInspectionReconcileResultVO result = new MaterialInspectionReconcileResultVO();
        result.setScannedCount(unqualifiedList.size());
        result.setCreatedCount(createdExceptionIds.size());
        result.setCreatedExceptionIds(createdExceptionIds);

        log.info("物料检验对账完成：scanned={}, created={}", unqualifiedList.size(), createdExceptionIds.size());
        return result;
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

        if ("不合格".equals(record.getInspectionResult()) && autoCreateException) {
            exceptionService.createFromMaterialInspection(record, loginUser);
        }
        return record;
    }

    @Override
    public List<MaterialInspection> findUnlinkedUnqualified(String startDate, String endDate, String plantCode) {
        LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(30);
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.atTime(23, 59, 59);

        return baseMapper.selectUnlinkedUnqualified(
                plantCode, "不合格", "来料不良", startAt, endAt);
    }

    private void validateImportRecord(MaterialInspection record, int index) {
        if (record == null) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条记录为空");
        }
        if (!StringUtils.hasText(record.getRecordNo())) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条记录编号不能为空");
        }
        if (!StringUtils.hasText(record.getInspectionResult())) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条检验结果不能为空");
        }
        if (!"合格".equals(record.getInspectionResult()) && !"不合格".equals(record.getInspectionResult())) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条检验结果必须是 合格/不合格");
        }
        // recordNo 唯一性校验
        LambdaQueryWrapper<MaterialInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialInspection::getRecordNo, record.getRecordNo());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "记录编号已存在：" + record.getRecordNo());
        }
    }

    @Override
    public KeySupplierTrendVO keySupplierTrend() {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();

        // 重点供应商 Top5（近30天批次量）
        List<SupplierRankItemVO> top = baseMapper.selectKeySuppliers(plantCode);
        // 重点供应商近30天每日合格率矩阵
        List<KeySupplierTrendRowVO> rows = baseMapper.selectKeySupplierTrend(plantCode);

        KeySupplierTrendVO vo = new KeySupplierTrendVO();
        List<KeySupplierTrendVO.KeySupplierItemVO> items = new ArrayList<>();
        for (SupplierRankItemVO s : top) {
            KeySupplierTrendVO.KeySupplierItemVO item = new KeySupplierTrendVO.KeySupplierItemVO();
            item.setSupplierCode(s.getSupplierCode());
            item.setSupplierName(s.getSupplierName());
            item.setTotalBatches(s.getTotalBatches());
            item.setPassRate(s.getPassRate());
            items.add(item);
        }
        vo.setKeySuppliers(items);

        // 组装统一日期轴 + 每家供应商对齐日期轴的合格率序列
        List<String> dates = new ArrayList<>();
        Map<String, KeySupplierTrendVO.KeySupplierSeriesVO> seriesMap = new LinkedHashMap<>();
        for (KeySupplierTrendRowVO row : rows) {
            if (!dates.contains(row.getDate())) {
                dates.add(row.getDate());
            }
            KeySupplierTrendVO.KeySupplierSeriesVO series = seriesMap.computeIfAbsent(
                    row.getSupplierCode(),
                    k -> {
                        KeySupplierTrendVO.KeySupplierSeriesVO ns = new KeySupplierTrendVO.KeySupplierSeriesVO();
                        ns.setSupplierCode(row.getSupplierCode());
                        ns.setSupplierName(row.getSupplierName());
                        ns.setPassRateList(new ArrayList<>());
                        return ns;
                    });
            series.getPassRateList().add(row.getPassRate());
        }
        vo.setDates(dates);
        vo.setSeries(new ArrayList<>(seriesMap.values()));
        return vo;
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
