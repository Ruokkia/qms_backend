package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcCapabilityResultDTO;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.spc.entity.SpcCapability;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
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
    private final SpcProcessMapper processMapper;
    private final FaiInspectionStandardMapper standardMapper;
    private final FaiInspectionStandardItemMapper standardItemMapper;

    public SpcCapabilityServiceImpl(SpcSubgroupMapper subgroupMapper,
                                   SpcParameterMapper parameterMapper,
                                   SpcSampleMapper sampleMapper,
                                   SpcCapabilityMapper capabilityMapper,
                                   SpcCoefficientMapper coefficientMapper,
                                   SpcProcessMapper processMapper,
                                   FaiInspectionStandardMapper standardMapper,
                                   FaiInspectionStandardItemMapper standardItemMapper) {
        this.subgroupMapper = subgroupMapper;
        this.parameterMapper = parameterMapper;
        this.sampleMapper = sampleMapper;
        this.capabilityMapper = capabilityMapper;
        this.coefficientMapper = coefficientMapper;
        this.processMapper = processMapper;
        this.standardMapper = standardMapper;
        this.standardItemMapper = standardItemMapper;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public SpcCapabilityResultDTO recalcCapability(Long paramId, String plantCode,
                                                    String itemType, String itemCode, String batchNo) {
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param == null || param.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        // item 维度隔离（与 SpcChartServiceImpl.recalcControlLimits 一致）
        boolean hasItem = itemType != null && !itemType.isEmpty()
                && itemCode != null && !itemCode.isEmpty();
        var queryWrapper = Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0)
                .isNotNull(SpcSubgroup::getItemCode)
                .ne(SpcSubgroup::getItemCode, "");
        if (hasItem) {
            queryWrapper.eq(SpcSubgroup::getItemType, itemType)
                        .eq(SpcSubgroup::getItemCode, itemCode);
        }
        List<SpcSubgroup> subs = subgroupMapper.selectList(
                queryWrapper.orderByDesc(SpcSubgroup::getSampleTime, SpcSubgroup::getId));
        if (subs.size() < 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "子组数不足，建议至少 20 组");
        }

        // ─── 从 FAI 检验标准层解析规格限 ───
        SpecContext ctx = resolveSpecFromStandard(param, itemType, itemCode, plantCode);
        BigDecimal usl = ctx.usl;
        BigDecimal lsl = ctx.lsl;
        BigDecimal target = ctx.target;
        int n = ctx.subgroupSize;

        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        List<BigDecimal> allValues = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                        .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0))
                .stream().map(SpcSample::getSampleValue).collect(Collectors.toList());

        BigDecimal mu = mean(subs.stream().map(SpcSubgroup::getMeanValue).collect(Collectors.toList()));
        BigDecimal rBar = mean(subs.stream().map(SpcSubgroup::getRangeValue).collect(Collectors.toList()));
        BigDecimal sBar = mean(subs.stream().map(SpcSubgroup::getStdDev).collect(Collectors.toList()));
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
        if (usl == null || lsl == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "未选中产品/物料或 FAI 检验标准未配置规格上下限，请先在标准维护中设置");
        }

        // 短期标准差估计（chartType 从 FAI 标准层读取）
        String chartType = ctx.chartType != null ? ctx.chartType : param.getChartType();
        BigDecimal sigmaHat = "Xbar-s".equals(chartType)
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
        if (hasItem) {
            cap.setItemType(itemType);
            cap.setItemCode(itemCode);
        }
        // 按维度精确删除旧记录（不误删其他 item 的能力记录）
        var deleteWrapper = Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId)
                .eq(SpcCapability::getPlantCode, plantCode);
        if (hasItem) {
            deleteWrapper.eq(SpcCapability::getItemType, itemType)
                         .eq(SpcCapability::getItemCode, itemCode);
        }
        capabilityMapper.delete(deleteWrapper);
        capabilityMapper.insert(cap);

        SpcCapabilityResultDTO result = toResult(cap);
        result.setUpperSpecLimit(usl);
        result.setLowerSpecLimit(lsl);
        result.setTargetValue(target);
        result.setSubgroupSize(n);
        return result;
    }

    @Override
    public SpcCapabilityResultDTO getLatest(Long paramId, String plantCode,
                                             String itemType, String itemCode, String batchNo) {
        // item 维度隔离查询
        boolean hasItem = itemType != null && !itemType.isEmpty()
                && itemCode != null && !itemCode.isEmpty();
        var queryWrapper = Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId)
                .eq(SpcCapability::getPlantCode, plantCode)
                .eq(SpcCapability::getIsDeleted, 0);
        if (hasItem) {
            queryWrapper.eq(SpcCapability::getItemType, itemType)
                        .eq(SpcCapability::getItemCode, itemCode);
        }
        SpcCapability cap = capabilityMapper.selectOne(
                queryWrapper.orderByDesc(SpcCapability::getCreatedAt).last("LIMIT 1"));
        if (cap == null) return null;

        SpcCapabilityResultDTO result = toResult(cap);
        // 附带当前 FAI 标准层的规格限（SpcCapability 实体不存储规格限，
        // 每次从 FAI 标准层实时解析，确保与标准维护保持一致）
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param != null && param.getIsDeleted() == 0) {
            SpecContext ctx = resolveSpecFromStandard(param, itemType, itemCode, plantCode);
            result.setUpperSpecLimit(ctx.usl);
            result.setLowerSpecLimit(ctx.lsl);
            result.setTargetValue(ctx.target);
            result.setSubgroupSize(ctx.subgroupSize);
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public void clear(Long paramId, String plantCode, String itemType, String itemCode) {
        boolean hasItem = itemType != null && !itemType.isEmpty()
                && itemCode != null && !itemCode.isEmpty();
        var deleteWrapper = Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId)
                .eq(SpcCapability::getPlantCode, plantCode);
        if (hasItem) {
            deleteWrapper.eq(SpcCapability::getItemType, itemType)
                         .eq(SpcCapability::getItemCode, itemCode);
        }
        capabilityMapper.delete(deleteWrapper);
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

    // ─── FAI 标准层规格限解析 ───

    /**
     * 从 FAI 检验标准层解析规格限的内部上下文。
     * <p>仅在 itemType + itemCode 均非空时查询；未查询到时返回零值上下文。</p>
     */
    private static class SpecContext {
        BigDecimal usl, lsl, target;
        int subgroupSize = 5;
        String chartType;
    }

    /**
     * 从 FAI 检验标准层解析规格限（USL/LSL/目标值/子组大小/图表类型）。
     * <p>通过 SpcParameter.processId → SpcProcess.processCode 与
     * FaiInspectionStandard.processCode 对齐。</p>
     * <p>仅在 itemType + itemCode 均非空时查询，否则返回空上下文。</p>
     */
    private SpecContext resolveSpecFromStandard(SpcParameter param, String itemType,
                                                 String itemCode, String plantCode) {
        SpecContext ctx = new SpecContext();
        if (itemType == null || itemType.isEmpty() || itemCode == null || itemCode.isEmpty()) {
            return ctx;
        }
        // 1. 获取工序编码
        SpcProcess process = processMapper.selectById(param.getProcessId());
        if (process == null) return ctx;

        // 2. 查询激活的 FAI 检验标准
        FaiInspectionStandard standard = standardMapper.selectActiveByCondition(
                itemType, itemCode, process.getProcessCode(), plantCode);
        if (standard == null) return ctx;

        // 3. 查询标准项（按 standardId + spcParameterId 唯一匹配）
        FaiInspectionStandardItem item = standardItemMapper.selectOne(
                Wrappers.lambdaQuery(FaiInspectionStandardItem.class)
                        .eq(FaiInspectionStandardItem::getStandardId, standard.getId())
                        .eq(FaiInspectionStandardItem::getSpcParameterId, param.getId())
                        .eq(FaiInspectionStandardItem::getIsDeleted, 0)
                        .orderByDesc(FaiInspectionStandardItem::getUpdatedAt)
                        .last("LIMIT 1"));
        if (item == null) return ctx;

        ctx.usl = item.getUpperLimit();
        ctx.lsl = item.getLowerLimit();
        ctx.target = item.getTargetValue();
        if (item.getSubgroupSize() != null) {
            ctx.subgroupSize = item.getSubgroupSize();
        }
        ctx.chartType = item.getChartType();
        return ctx;
    }
}
