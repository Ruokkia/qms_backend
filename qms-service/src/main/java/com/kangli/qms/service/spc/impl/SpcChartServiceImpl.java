package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcChartDataDTO;
import com.kangli.qms.service.spc.dto.SpcChartPoint;
import com.kangli.qms.service.spc.dto.SpcSampleResponse;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcControlLimitMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.spc.SpcChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * M4 SPC 控制图实现（控制限计算 + 图数据组装）。
 */
@Slf4j
@Service
public class SpcChartServiceImpl implements SpcChartService {

    private static final int SCALE = 6;
    private static final String CHART_TYPE_XBAR_S = "Xbar-s";

    private final SpcSubgroupMapper subgroupMapper;
    private final SpcParameterMapper parameterMapper;
    private final SpcControlLimitMapper controlLimitMapper;
    private final SpcSampleMapper sampleMapper;
    private final SpcCoefficientMapper coefficientMapper;
    private final SpcProcessMapper processMapper;
    private final FaiInspectionStandardMapper standardMapper;
    private final FaiInspectionStandardItemMapper standardItemMapper;

    public SpcChartServiceImpl(SpcSubgroupMapper subgroupMapper,
                              SpcParameterMapper parameterMapper,
                              SpcControlLimitMapper controlLimitMapper,
                              SpcSampleMapper sampleMapper,
                              SpcCoefficientMapper coefficientMapper,
                              SpcProcessMapper processMapper,
                              FaiInspectionStandardMapper standardMapper,
                              FaiInspectionStandardItemMapper standardItemMapper) {
        this.subgroupMapper = subgroupMapper;
        this.parameterMapper = parameterMapper;
        this.controlLimitMapper = controlLimitMapper;
        this.sampleMapper = sampleMapper;
        this.coefficientMapper = coefficientMapper;
        this.processMapper = processMapper;
        this.standardMapper = standardMapper;
        this.standardItemMapper = standardItemMapper;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public SpcControlLimit recalcControlLimits(Long paramId, String plantCode, String itemType, String itemCode) {
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param == null || param.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        // 按维度隔离：itemType + itemCode 同时非空时，仅统计同一产品/物料的有效子组；
        // 否则按全局基线（含所有有效子组，但仍排除空 item_code 的无效 TMP 测试子组）。
        boolean hasItem = itemCode != null && !itemCode.isEmpty()
                && itemType != null && !itemType.isEmpty();
        LambdaQueryWrapper<SpcSubgroup> recalcQuery = Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0)
                .isNotNull(SpcSubgroup::getItemCode)
                .ne(SpcSubgroup::getItemCode, "")
                .orderByDesc(SpcSubgroup::getSampleTime, SpcSubgroup::getId);
        if (hasItem) {
            recalcQuery.eq(SpcSubgroup::getItemType, itemType)
                    .eq(SpcSubgroup::getItemCode, itemCode);
        }
        List<SpcSubgroup> subs = subgroupMapper.selectList(recalcQuery);

        // 子组数 < 2：清空（同一维度的）控制限记录
        if (subs.size() < 2) {
            LambdaQueryWrapper<SpcControlLimit> delQuery = Wrappers.lambdaQuery(SpcControlLimit.class)
                    .eq(SpcControlLimit::getParamId, paramId)
                    .eq(SpcControlLimit::getPlantCode, plantCode)
                    .eq(SpcControlLimit::getChartType, param.getChartType());
            if (hasItem) {
                delQuery.eq(SpcControlLimit::getItemType, itemType)
                        .eq(SpcControlLimit::getItemCode, itemCode);
            }
            controlLimitMapper.delete(delQuery);
            return null;
        }

        BigDecimal xBarBar = mean(subs.stream().map(SpcSubgroup::getMeanValue).collect(Collectors.toList()));
        BigDecimal rBar = mean(subs.stream().map(SpcSubgroup::getRangeValue).collect(Collectors.toList()));
        BigDecimal sBar = mean(subs.stream().map(SpcSubgroup::getStdDev).collect(Collectors.toList()));
        int n = param.getSubgroupSize();
        SpcCoefficient coef = coefficientMapper.selectById(n);
        if (coef == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SPC 系数表未初始化 n=" + n);
        }
        // 防御性校验：计算用到的系数缺失会触发空指针，提前拦截给出可读错误
        boolean xbarS = CHART_TYPE_XBAR_S.equals(param.getChartType());
        List<String> missing = new ArrayList<>();
        if (coef.getA2() == null) missing.add("A2");
        if (coef.getD4() == null) missing.add("D4");
        if (coef.getD3L() == null) missing.add("D3");
        if (xbarS) {
            if (coef.getA3() == null) missing.add("A3");
            if (coef.getB3() == null) missing.add("B3");
            if (coef.getB4() == null) missing.add("B4");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "SPC 系数缺失: " + String.join(",", missing) + " (n=" + n + ")，请执行系数修正迁移");
        }

        SpcControlLimit cl = new SpcControlLimit();
        cl.setParamId(paramId);
        cl.setChartType(param.getChartType());
        cl.setPlantCode(plantCode);
        cl.setPlantName(plantCode.equals("SZ") ? "深圳" : "梅州");
        // 带维度写入，与控制图读取维度一致
        if (hasItem) {
            cl.setItemType(itemType);
            cl.setItemCode(itemCode);
        }
        cl.setSubgroupCount(subs.size());
        cl.setCalcDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        if (CHART_TYPE_XBAR_S.equals(param.getChartType())) {
            cl.setXbarUcl(scale(xBarBar.add(scale(coef.getA3().multiply(sBar)))));
            cl.setXbarCl(xBarBar);
            cl.setXbarLcl(scale(xBarBar.subtract(scale(coef.getA3().multiply(sBar)))));
            cl.setSUcl(scale(coef.getB4().multiply(sBar)));
            cl.setSCl(sBar);
            cl.setSLcl(scale(coef.getB3().multiply(sBar)));
        } else {
            cl.setXbarUcl(scale(xBarBar.add(scale(coef.getA2().multiply(rBar)))));
            cl.setXbarCl(xBarBar);
            cl.setXbarLcl(scale(xBarBar.subtract(scale(coef.getA2().multiply(rBar)))));
            cl.setRUcl(scale(coef.getD4().multiply(rBar)));
            cl.setRCl(rBar);
            cl.setRLcl(scale(coef.getD3L().multiply(rBar)));
        }

        LambdaQueryWrapper<SpcControlLimit> upsertDelQuery = Wrappers.lambdaQuery(SpcControlLimit.class)
                .eq(SpcControlLimit::getParamId, paramId)
                .eq(SpcControlLimit::getPlantCode, plantCode)
                .eq(SpcControlLimit::getChartType, param.getChartType());
        if (hasItem) {
            upsertDelQuery.eq(SpcControlLimit::getItemType, itemType)
                    .eq(SpcControlLimit::getItemCode, itemCode);
        }
        controlLimitMapper.delete(upsertDelQuery);
        controlLimitMapper.insert(cl);
        return cl;
    }

    @Override
    public SpcChartDataDTO getChartData(Long paramId, String chartType, String plantCode, String itemType, String itemCode, String batchNo) {
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param == null || param.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        boolean hasItem = itemCode != null && !itemCode.isEmpty()
                && itemType != null && !itemType.isEmpty();
        SpcControlLimit cl = null;
        if (hasItem) {
            // 先查维度专属控制限（精确匹配 item_type + item_code）
            cl = controlLimitMapper.selectOne(Wrappers.lambdaQuery(SpcControlLimit.class)
                    .eq(SpcControlLimit::getParamId, paramId)
                    .eq(SpcControlLimit::getPlantCode, plantCode)
                    .eq(SpcControlLimit::getChartType, chartType)
                    .eq(SpcControlLimit::getItemType, itemType)
                    .eq(SpcControlLimit::getItemCode, itemCode)
                    .eq(SpcControlLimit::getIsDeleted, 0)
                    .orderByDesc(SpcControlLimit::getCreatedAt).last("LIMIT 1"));
            // 维度专属未命中时，降级到全局基线（兼容存量 NULL 维度记录）
            if (cl == null) {
                cl = controlLimitMapper.selectOne(Wrappers.lambdaQuery(SpcControlLimit.class)
                        .eq(SpcControlLimit::getParamId, paramId)
                        .eq(SpcControlLimit::getPlantCode, plantCode)
                        .eq(SpcControlLimit::getChartType, chartType)
                        .eq(SpcControlLimit::getIsDeleted, 0)
                        .isNull(SpcControlLimit::getItemCode)
                        .orderByDesc(SpcControlLimit::getCreatedAt).last("LIMIT 1"));
            }
        } else {
            // 全局基线视图：读 item_type/item_code 为 NULL 的控制限
            cl = controlLimitMapper.selectOne(Wrappers.lambdaQuery(SpcControlLimit.class)
                    .eq(SpcControlLimit::getParamId, paramId)
                    .eq(SpcControlLimit::getPlantCode, plantCode)
                    .eq(SpcControlLimit::getChartType, chartType)
                    .eq(SpcControlLimit::getIsDeleted, 0)
                    .isNull(SpcControlLimit::getItemCode)
                    .orderByDesc(SpcControlLimit::getCreatedAt).last("LIMIT 1"));
        }

        LambdaQueryWrapper<SpcSubgroup> subQuery = Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0);
        // 按产品/物料维度隔离控制图点：分类必须与代码同时具备才生效（等值匹配，与 recalc 维度一致）。
        // 仅选分类（未填代码）时不加 itemType 过滤，避免把 item_type 为 NULL 的历史子组全部排除。
        // 始终排除空 item_code 的无效 TMP 测试子组，避免脏数据污染控制图。
        if (itemCode != null && !itemCode.isEmpty()
                && itemType != null && !itemType.isEmpty()) {
            subQuery.eq(SpcSubgroup::getItemType, itemType)
                    .eq(SpcSubgroup::getItemCode, itemCode);
        } else {
            // 未指定 item 维度时，仍要剔除空 item_code 的无效 TMP 子组，
            // 否则全局基线视图会混入 322~330°C 的测试子组把控制限拉偏。
            subQuery.isNotNull(SpcSubgroup::getItemCode)
                    .ne(SpcSubgroup::getItemCode, "");
        }
        // 按批次过滤，支持多批次对比分析
        if (batchNo != null && !batchNo.isEmpty()) {
            subQuery.eq(SpcSubgroup::getBatchNo, batchNo);
        }
        subQuery.orderByDesc(SpcSubgroup::getSampleTime, SpcSubgroup::getId);
        List<SpcSubgroup> subs = subgroupMapper.selectList(subQuery);
        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        Map<Long, List<SpcSampleResponse>> samplesBySub = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                            .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0)
                            .orderByAsc(SpcSample::getSampleNo))
                    .forEach(s -> {
                        SpcSampleResponse r = new SpcSampleResponse();
                        r.setId(s.getId());
                        r.setSubgroupId(s.getSubgroupId());
                        r.setSampleNo(s.getSampleNo());
                        r.setSampleValue(s.getSampleValue());
                        r.setBarcode(s.getBarcode());
                        samplesBySub
                                .computeIfAbsent(s.getSubgroupId(), k -> new ArrayList<>())
                                .add(r);
                    });
        }

        SpcChartDataDTO dto = new SpcChartDataDTO();
        dto.setParamId(paramId);
        dto.setParamCode(param.getParamCode());
        dto.setParamName(param.getParamName());
        dto.setChartType(chartType);
        List<SpcChartPoint> points = subs.stream().map(s -> {
            SpcChartPoint p = new SpcChartPoint();
            p.setSubgroupId(s.getId());
            p.setSubgroupNo(s.getSubgroupNo());
            p.setItemType(s.getItemType());
            p.setItemCode(s.getItemCode());
            p.setBatchNo(s.getBatchNo());
            p.setBarcode(s.getBarcode());
            p.setX(s.getMeanValue());
            if (CHART_TYPE_XBAR_S.equals(chartType)) {
                p.setS(s.getStdDev());
            } else {
                p.setR(s.getRangeValue());
            }
            p.setSamples(samplesBySub.get(s.getId()));
            return p;
        }).collect(Collectors.toList());
        dto.setPoints(points);

        // 填充可用批次列表（去重，用于前端批次下拉选择器）
        dto.setAvailableBatches(subs.stream()
                .map(SpcSubgroup::getBatchNo)
                .filter(Objects::nonNull)
                .filter(b -> !b.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList()));

        // 从 FAI 检验标准层解析规格限（仅在产品/物料上下文存在时）
        resolveSpecFromStandard(param, itemType, itemCode, plantCode, dto);

        // 计算过程能力指数 Cp/Cpk/Pp/Ppk（使用解析后的规格限）
        if (dto.getUpperSpecLimit() != null && dto.getLowerSpecLimit() != null) {
            calculateCapabilityIndex(dto, subs);
        }

        if (cl != null) {
            dto.setXbarUcl(cl.getXbarUcl());
            dto.setXbarCl(cl.getXbarCl());
            dto.setXbarLcl(cl.getXbarLcl());
            dto.setRUcl(cl.getRUcl());
            dto.setRCl(cl.getRCl());
            dto.setRLcl(cl.getRLcl());
            dto.setSUcl(cl.getSUcl());
            dto.setSCl(cl.getSCl());
            dto.setSLcl(cl.getSLcl());
        } else if (!subs.isEmpty()) {
            // 控制限记录缺失时的兜底：从子组数据实时计算临时控制限（仅展示，不持久化）
            computeTempControlLimits(dto, subs, param.getSubgroupSize(), chartType);
        }
        return dto;
    }

    /**
     * 控制限记录缺失时的兜底：从子组数据实时计算临时控制限（仅用于展示，不持久化）。
     * <p>当用户尚未点击「重新计算控制限」时，API 仍可返回基于现有子组数据的临时控制限，
     * 使控制图能展示基线，前端会同时引导用户生成持久化控制限。</p>
     */
    private void computeTempControlLimits(SpcChartDataDTO dto, List<SpcSubgroup> subs, int subgroupSize, String chartType) {
        if (subs.size() < 2) {
            log.info("子组数量不足（{}个），跳过临时控制限计算 paramId={}", subs.size(), dto.getParamId());
            return;
        }
        SpcCoefficient coef = coefficientMapper.selectById(subgroupSize);
        if (coef == null) {
            log.warn("无法计算临时控制限：SPC 系数表未初始化 n={}", subgroupSize);
            return;
        }
        boolean xbarS = CHART_TYPE_XBAR_S.equals(chartType);
        List<String> missing = new ArrayList<>();
        if (coef.getA2() == null) missing.add("A2");
        if (coef.getD4() == null) missing.add("D4");
        if (coef.getD3L() == null) missing.add("D3");
        if (xbarS) {
            if (coef.getA3() == null) missing.add("A3");
            if (coef.getB3() == null) missing.add("B3");
            if (coef.getB4() == null) missing.add("B4");
        }
        if (!missing.isEmpty()) {
            log.warn("无法计算临时控制限：SPC 系数缺失 {} (n={})", String.join(",", missing), subgroupSize);
            return;
        }

        BigDecimal xBarBar = mean(subs.stream().map(SpcSubgroup::getMeanValue).collect(Collectors.toList()));
        BigDecimal rBar = mean(subs.stream().map(SpcSubgroup::getRangeValue).collect(Collectors.toList()));
        BigDecimal sBar = mean(subs.stream().map(SpcSubgroup::getStdDev).collect(Collectors.toList()));

        if (xbarS) {
            dto.setXbarUcl(scale(xBarBar.add(scale(coef.getA3().multiply(sBar)))));
            dto.setXbarCl(xBarBar);
            dto.setXbarLcl(scale(xBarBar.subtract(scale(coef.getA3().multiply(sBar)))));
            dto.setSUcl(scale(coef.getB4().multiply(sBar)));
            dto.setSCl(sBar);
            dto.setSLcl(scale(coef.getB3().multiply(sBar)));
        } else {
            dto.setXbarUcl(scale(xBarBar.add(scale(coef.getA2().multiply(rBar)))));
            dto.setXbarCl(xBarBar);
            dto.setXbarLcl(scale(xBarBar.subtract(scale(coef.getA2().multiply(rBar)))));
            dto.setRUcl(scale(coef.getD4().multiply(rBar)));
            dto.setRCl(rBar);
            dto.setRLcl(scale(coef.getD3L().multiply(rBar)));
        }
        log.info("已从 {} 个子组实时计算临时控制限（paramId={}, chartType={}），此为兜底展示值",
                subs.size(), dto.getParamId(), chartType);
    }

    /**
     * 从 FAI 检验标准层解析规格限（USL/LSL/目标值/子组大小）。
     * <p>通过 SpcParameter.processId → SpcProcess.processCode 与
     * FaiInspectionStandard.processCode 对齐。</p>
     * <p>仅在 itemType + itemCode 均非空时查询，否则不设置规格限（未选产品时不展示规格线）。</p>
     */
    private void resolveSpecFromStandard(SpcParameter param, String itemType, String itemCode,
                                          String plantCode, SpcChartDataDTO dto) {
        if (itemType == null || itemType.isEmpty() || itemCode == null || itemCode.isEmpty()) {
            return;
        }
        // 1. 获取工序编码
        SpcProcess process = processMapper.selectById(param.getProcessId());
        if (process == null) return;

        // 2. 查询激活的 FAI 检验标准
        FaiInspectionStandard standard = standardMapper.selectActiveByCondition(
                itemType, itemCode, process.getProcessCode(), plantCode);
        if (standard == null) return;

        // 3. 查询标准项（按 standardId + spcParameterId 唯一匹配）
        FaiInspectionStandardItem item = standardItemMapper.selectOne(
                Wrappers.lambdaQuery(FaiInspectionStandardItem.class)
                        .eq(FaiInspectionStandardItem::getStandardId, standard.getId())
                        .eq(FaiInspectionStandardItem::getSpcParameterId, param.getId())
                        .eq(FaiInspectionStandardItem::getIsDeleted, 0)
                        .orderByDesc(FaiInspectionStandardItem::getUpdatedAt)
                        .last("LIMIT 1"));
        if (item == null) return;

        // 4. 回填规格限到 DTO
        dto.setUpperSpecLimit(item.getUpperLimit());
        dto.setLowerSpecLimit(item.getLowerLimit());
        dto.setTargetValue(item.getTargetValue());
        dto.setSubgroupSize(item.getSubgroupSize());
    }

    /**
     * 计算过程能力指数 Cp/Cpk/Pp/Ppk 并填入 DTO。
     * <p>需要足够数据（≥ 2 个子组）和 USL/LSL 定义（从 DTO 读取，即 FAI 标准层解析结果）。</p>
     */
    private void calculateCapabilityIndex(SpcChartDataDTO dto, List<SpcSubgroup> subs) {
        if (subs.size() < 2) return;
        BigDecimal usl = dto.getUpperSpecLimit();
        BigDecimal lsl = dto.getLowerSpecLimit();
        if (usl == null || lsl == null) return;

        int n = dto.getSubgroupSize() != null ? dto.getSubgroupSize() : 0;
        if (n < 2 || n > 10) return;

        SpcCoefficient coeff = coefficientMapper.selectById(n);
        if (coeff == null || coeff.getD2() == null) return;

        // 子组均值序列（过滤空值）
        List<BigDecimal> xbars = subs.stream()
                .map(SpcSubgroup::getMeanValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (xbars.isEmpty()) return;
        BigDecimal grandXbar = mean(xbars);

        // 子组内标准差 σ_within
        BigDecimal sigmaWithin;
        if (CHART_TYPE_XBAR_S.equals(dto.getChartType()) && coeff.getC4() != null) {
            // Xbar-s: σ_within = s̄ / c4
            List<BigDecimal> stdDevs = subs.stream()
                    .map(SpcSubgroup::getStdDev)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (stdDevs.isEmpty()) return;
            BigDecimal sbar = mean(stdDevs);
            sigmaWithin = sbar.divide(coeff.getC4(), SCALE, RoundingMode.HALF_UP);
        } else {
            // Xbar-R: σ_within = R̄ / d2
            List<BigDecimal> ranges = subs.stream()
                    .map(SpcSubgroup::getRangeValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (ranges.isEmpty()) return;
            BigDecimal rbar = mean(ranges);
            sigmaWithin = rbar.divide(coeff.getD2(), SCALE, RoundingMode.HALF_UP);
        }
        if (sigmaWithin.compareTo(BigDecimal.ZERO) <= 0) return;

        // Cp = (USL - LSL) / (6 * σ)
        BigDecimal sixSigma = sigmaWithin.multiply(BigDecimal.valueOf(6));
        double cp = usl.subtract(lsl).divide(sixSigma, 4, RoundingMode.HALF_UP).doubleValue();

        // Cpk = min(USL - X̄̄, X̄̄ - LSL) / (3 * σ)
        BigDecimal diffUpper = usl.subtract(grandXbar);
        BigDecimal diffLower = grandXbar.subtract(lsl);
        BigDecimal minDiff = diffUpper.compareTo(diffLower) < 0 ? diffUpper : diffLower;
        double cpk = minDiff.divide(sigmaWithin.multiply(BigDecimal.valueOf(3)), 4, RoundingMode.HALF_UP).doubleValue();

        // 整体标准差 σ_overall = stdev(所有子组的所有样本值)，从 spc_sample 表加载
        List<BigDecimal> allSamples = new ArrayList<>();
        for (SpcSubgroup sub : subs) {
            List<SpcSample> samples = sampleMapper.selectList(
                    Wrappers.lambdaQuery(SpcSample.class)
                            .eq(SpcSample::getSubgroupId, sub.getId())
                            .eq(SpcSample::getIsDeleted, 0));
            for (SpcSample s : samples) {
                if (s.getSampleValue() != null) allSamples.add(s.getSampleValue());
            }
        }
        BigDecimal sigmaOverall = stdev(allSamples);
        if (sigmaOverall != null && sigmaOverall.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal sixOverall = sigmaOverall.multiply(BigDecimal.valueOf(6));
            double pp = usl.subtract(lsl).divide(sixOverall, 4, RoundingMode.HALF_UP).doubleValue();

            BigDecimal meanAll = mean(allSamples);
            BigDecimal dU = usl.subtract(meanAll);
            BigDecimal dL = meanAll.subtract(lsl);
            double ppk = (dU.compareTo(dL) < 0 ? dU : dL)
                    .divide(sigmaOverall.multiply(BigDecimal.valueOf(3)), 4, RoundingMode.HALF_UP).doubleValue();

            dto.setPp(pp);
            dto.setPpk(ppk);
            dto.setSigmaOverall(sigmaOverall.doubleValue());
        }

        dto.setCp(cp);
        dto.setCpk(cpk);
        dto.setSigmaWithin(sigmaWithin.doubleValue());
    }

    private BigDecimal stdev(List<BigDecimal> values) {
        if (values == null || values.size() < 2) return null;
        BigDecimal mean = mean(values);
        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal d = v.subtract(mean);
            sumSq = sumSq.add(d.multiply(d));
        }
        BigDecimal variance = sumSq.divide(BigDecimal.valueOf(values.size() - 1), SCALE, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    private BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
