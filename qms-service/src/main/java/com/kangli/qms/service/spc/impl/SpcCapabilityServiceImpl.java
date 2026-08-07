package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcCapabilityResultDTO;
import com.kangli.qms.domain.spc.entity.SpcCapability;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.spc.SpcCapabilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * M4 SPC 过程能力指数实现（CP/CPK/PP/PPK 计算 + 判定）。
 */
@Slf4j
@Service
public class SpcCapabilityServiceImpl implements SpcCapabilityService {

    private static final int SCALE = 6;
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal SIX = new BigDecimal("6");

    private final SpcSubgroupMapper subgroupMapper;
    private final SpcParameterMapper parameterMapper;
    private final SpcSampleMapper sampleMapper;
    private final SpcCapabilityMapper capabilityMapper;
    private final SpcCoefficientMapper coefficientMapper;

    public SpcCapabilityServiceImpl(SpcSubgroupMapper subgroupMapper,
                                   SpcParameterMapper parameterMapper,
                                   SpcSampleMapper sampleMapper,
                                   SpcCapabilityMapper capabilityMapper,
                                   SpcCoefficientMapper coefficientMapper) {
        this.subgroupMapper = subgroupMapper;
        this.parameterMapper = parameterMapper;
        this.sampleMapper = sampleMapper;
        this.capabilityMapper = capabilityMapper;
        this.coefficientMapper = coefficientMapper;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public SpcCapabilityResultDTO recalcCapability(Long paramId, String plantCode) {
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
        if (subs.size() < 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "子组数不足，建议至少 20 组");
        }

        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        List<BigDecimal> allValues = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                        .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0))
                .stream().map(SpcSample::getSampleValue).collect(Collectors.toList());

        BigDecimal mu = mean(subs.stream().map(SpcSubgroup::getMeanValue).collect(Collectors.toList()));
        BigDecimal rBar = mean(subs.stream().map(SpcSubgroup::getRangeValue).collect(Collectors.toList()));
        BigDecimal sBar = mean(subs.stream().map(SpcSubgroup::getStdDev).collect(Collectors.toList()));
        int n = param.getSubgroupSize();
        SpcCoefficient coef = coefficientMapper.selectById(n);
        if (coef == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "SPC 系数表未初始化 n=" + n);
        }
        // 防御性校验：c4 / d2 为 NULL 或 0 会触发除零异常，必须提前拦截给出可读错误
        if (coef.getC4() == null || coef.getC4().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "SPC 系数 c4 缺失或为零，无法计算短期标准差 (n=" + n + ")，请执行系数修正迁移");
        }
        if (coef.getD2() == null || coef.getD2().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "SPC 系数 d2 缺失或为零 (n=" + n + ")，请执行系数修正迁移");
        }
        if (param.getUpperSpecLimit() == null || param.getLowerSpecLimit() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "参数未配置规格上下限，无法计算能力指数");
        }
        BigDecimal usl = param.getUpperSpecLimit();
        BigDecimal lsl = param.getLowerSpecLimit();

        // 短期标准差估计
        BigDecimal sigmaHat = "Xbar-s".equals(param.getChartType())
                ? sBar.divide(coef.getC4(), SCALE, RoundingMode.HALF_UP)
                : rBar.divide(coef.getD2(), SCALE, RoundingMode.HALF_UP);

        BigDecimal cp = scale((usl.subtract(lsl)).divide(SIX.multiply(sigmaHat), SCALE, RoundingMode.HALF_UP));
        BigDecimal cpu = scale((usl.subtract(mu)).divide(THREE.multiply(sigmaHat), SCALE, RoundingMode.HALF_UP));
        BigDecimal cpl = scale((mu.subtract(lsl)).divide(THREE.multiply(sigmaHat), SCALE, RoundingMode.HALF_UP));
        BigDecimal cpk = scale(cpu.min(cpl));

        // 长期标准差（全部样本）
        double muD = mu.doubleValue();
        double sumSq = allValues.stream().mapToDouble(v -> {
            double d = v.doubleValue() - muD;
            return d * d;
        }).sum();
        int N = allValues.size();
        double sigmaLongD = N > 1 ? Math.sqrt(sumSq / (N - 1)) : 0d;
        BigDecimal sigmaLong = BigDecimal.valueOf(sigmaLongD).setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal pp = scale((usl.subtract(lsl)).divide(SIX.multiply(sigmaLong), SCALE, RoundingMode.HALF_UP));
        BigDecimal ppu = scale((usl.subtract(mu)).divide(THREE.multiply(sigmaLong), SCALE, RoundingMode.HALF_UP));
        BigDecimal ppl = scale((mu.subtract(lsl)).divide(THREE.multiply(sigmaLong), SCALE, RoundingMode.HALF_UP));
        BigDecimal ppk = scale(ppu.min(ppl));

        String judgment = judge(cpk);

        LocalDateTime start = subs.stream().map(SpcSubgroup::getSampleTime).filter(t -> t != null)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime end = subs.stream().map(SpcSubgroup::getSampleTime).filter(t -> t != null)
                .max(Comparator.naturalOrder()).orElse(null);

        SpcCapability cap = new SpcCapability();
        cap.setParamId(paramId);
        cap.setCp(cp);
        cap.setCpk(cpk);
        cap.setPp(pp);
        cap.setPpk(ppk);
        cap.setCpu(cpu);
        cap.setCpl(cpl);
        cap.setSampleCount(N);
        cap.setSubgroupCount(subs.size());
        cap.setJudgment(judgment);
        cap.setStartTime(start);
        cap.setEndTime(end);
        cap.setPlantCode(plantCode);
        cap.setPlantName(plantCode.equals("SZ") ? "深圳" : "梅州");
        capabilityMapper.delete(Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId)
                .eq(SpcCapability::getPlantCode, plantCode));
        capabilityMapper.insert(cap);

        return toResult(cap);
    }

    @Override
    public SpcCapabilityResultDTO getLatest(Long paramId, String plantCode) {
        SpcCapability cap = capabilityMapper.selectOne(Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId)
                .eq(SpcCapability::getPlantCode, plantCode)
                .eq(SpcCapability::getIsDeleted, 0)
                .orderByDesc(SpcCapability::getCreatedAt).last("LIMIT 1"));
        return cap == null ? null : toResult(cap);
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public void clear(Long paramId, String plantCode) {
        capabilityMapper.delete(Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId)
                .eq(SpcCapability::getPlantCode, plantCode));
    }

    private String judge(BigDecimal cpk) {
        double v = cpk.doubleValue();
        if (v >= 1.33) return "充足";
        if (v >= 1.0) return "需改进";
        return "不足";
    }

    private BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private SpcCapabilityResultDTO toResult(SpcCapability c) {
        SpcCapabilityResultDTO r = new SpcCapabilityResultDTO();
        r.setParamId(c.getParamId());
        r.setCp(c.getCp());
        r.setCpk(c.getCpk());
        r.setPp(c.getPp());
        r.setPpk(c.getPpk());
        r.setCpu(c.getCpu());
        r.setCpl(c.getCpl());
        r.setSampleCount(c.getSampleCount());
        r.setSubgroupCount(c.getSubgroupCount());
        r.setJudgment(c.getJudgment());
        return r;
    }
}
