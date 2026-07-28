package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.dto.SpcChartDataDTO;
import com.kangli.qms.dto.SpcChartPoint;
import com.kangli.qms.entity.SpcCoefficient;
import com.kangli.qms.entity.SpcControlLimit;
import com.kangli.qms.entity.SpcParameter;
import com.kangli.qms.entity.SpcSample;
import com.kangli.qms.entity.SpcSubgroup;
import com.kangli.qms.mapper.SpcCoefficientMapper;
import com.kangli.qms.mapper.SpcControlLimitMapper;
import com.kangli.qms.mapper.SpcParameterMapper;
import com.kangli.qms.mapper.SpcSampleMapper;
import com.kangli.qms.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.SpcChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    @Transactional(rollbackFor = Exception.class)
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

        SpcControlLimit cl = new SpcControlLimit();
        cl.setParamId(paramId);
        cl.setChartType(param.getChartType());
        cl.setPlantCode(plantCode);
        cl.setPlantName(plantCode.equals("SZ") ? "深圳" : "梅州");
        cl.setSubgroupCount(subs.size());
        cl.setCalcDate(LocalDateTime.now());

        if ("Xbar-s".equals(param.getChartType())) {
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
    public SpcChartDataDTO getChartData(Long paramId, String chartType, String plantCode) {
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

        List<SpcSubgroup> subs = subgroupMapper.selectList(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0)
                .orderByAsc(SpcSubgroup::getSampleTime, SpcSubgroup::getId));
        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        Map<Long, List<BigDecimal>> samplesBySub = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                            .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0))
                    .forEach(s -> samplesBySub
                            .computeIfAbsent(s.getSubgroupId(), k -> new ArrayList<>())
                            .add(s.getSampleValue()));
        }

        SpcChartDataDTO dto = new SpcChartDataDTO();
        dto.setParamId(paramId);
        dto.setParamCode(param.getParamCode());
        dto.setParamName(param.getParamName());
        dto.setChartType(chartType);
        List<SpcChartPoint> points = subs.stream().map(s -> {
            SpcChartPoint p = new SpcChartPoint();
            p.setSubgroupNo(s.getSubgroupNo());
            p.setX(s.getMeanValue());
            if ("Xbar-s".equals(chartType)) {
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
