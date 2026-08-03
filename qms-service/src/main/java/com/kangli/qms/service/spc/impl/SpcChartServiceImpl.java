package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcChartDataDTO;
import com.kangli.qms.service.spc.dto.SpcChartPoint;
import com.kangli.qms.service.spc.dto.SpcSampleResponse;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcControlLimitMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
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

    public SpcChartServiceImpl(SpcSubgroupMapper subgroupMapper,
                              SpcParameterMapper parameterMapper,
                              SpcControlLimitMapper controlLimitMapper,
                              SpcSampleMapper sampleMapper,
                              SpcCoefficientMapper coefficientMapper) {
        this.subgroupMapper = subgroupMapper;
        this.parameterMapper = parameterMapper;
        this.controlLimitMapper = controlLimitMapper;
        this.sampleMapper = sampleMapper;
        this.coefficientMapper = coefficientMapper;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public SpcControlLimit recalcControlLimits(Long paramId, String plantCode) {
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param == null || param.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        List<SpcSubgroup> subs = subgroupMapper.selectList(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0)
                .orderByAsc(SpcSubgroup::getSampleTime, SpcSubgroup::getId));

        // 子组数 < 2：清空控制限记录
        if (subs.size() < 2) {
            controlLimitMapper.delete(Wrappers.lambdaQuery(SpcControlLimit.class)
                    .eq(SpcControlLimit::getParamId, paramId)
                    .eq(SpcControlLimit::getPlantCode, plantCode)
                    .eq(SpcControlLimit::getChartType, param.getChartType()));
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

        controlLimitMapper.delete(Wrappers.lambdaQuery(SpcControlLimit.class)
                .eq(SpcControlLimit::getParamId, paramId)
                .eq(SpcControlLimit::getPlantCode, plantCode)
                .eq(SpcControlLimit::getChartType, param.getChartType()));
        controlLimitMapper.insert(cl);
        return cl;
    }

    @Override
    public SpcChartDataDTO getChartData(Long paramId, String chartType, String plantCode, String itemType, String itemCode) {
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param == null || param.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        SpcControlLimit cl = controlLimitMapper.selectOne(Wrappers.lambdaQuery(SpcControlLimit.class)
                .eq(SpcControlLimit::getParamId, paramId)
                .eq(SpcControlLimit::getPlantCode, plantCode)
                .eq(SpcControlLimit::getChartType, chartType)
                .eq(SpcControlLimit::getIsDeleted, 0)
                .orderByDesc(SpcControlLimit::getCreatedAt).last("LIMIT 1"));

        LambdaQueryWrapper<SpcSubgroup> subQuery = Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0);
        // 按产品/物料维度关联控制图点：分类与代码各自独立生效。
        // 仅选分类（未填代码）时也必须过滤，否则产品视图会混入物料子组，造成控制限失真。
        if (itemType != null && !itemType.isEmpty()) {
            subQuery.eq(SpcSubgroup::getItemType, itemType);
        }
        if (itemCode != null && !itemCode.isEmpty()) {
            // 支持产品/物料代码模糊搜索（LIKE %kw%）
            subQuery.like(SpcSubgroup::getItemCode, itemCode);
        }
        subQuery.orderByAsc(SpcSubgroup::getSampleTime, SpcSubgroup::getId);
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
            p.setSubgroupNo(s.getSubgroupNo());
            p.setItemType(s.getItemType());
            p.setItemCode(s.getItemCode());
            p.setBatchNo(s.getBatchNo());
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
        }
        return dto;
    }

    private BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
