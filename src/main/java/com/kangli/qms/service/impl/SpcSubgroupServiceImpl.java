package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.dto.SpcSubgroupResponse;
import com.kangli.qms.dto.SpcSubgroupSaveDTO;
import com.kangli.qms.dto.SpcPendingSampleAppendDTO;
import com.kangli.qms.dto.SpcSampleResponse;
import com.kangli.qms.entity.FaiInspectionItem;
import com.kangli.qms.entity.FaiInspectionRecord;
import com.kangli.qms.entity.FaiInspectionStandard;
import com.kangli.qms.entity.FaiInspectionStandardItem;
import com.kangli.qms.entity.SpcParameter;
import com.kangli.qms.entity.SpcProcess;
import com.kangli.qms.entity.SpcSample;
import com.kangli.qms.entity.SpcSubgroup;
import com.kangli.qms.mapper.FaiInspectionItemMapper;
import com.kangli.qms.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.mapper.SpcParameterMapper;
import com.kangli.qms.mapper.SpcProcessMapper;
import com.kangli.qms.mapper.SpcSampleMapper;
import com.kangli.qms.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.SpcCapabilityService;
import com.kangli.qms.service.SpcChartService;
import com.kangli.qms.service.SpcSubgroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * M4 SPC 子组（数据采集）实现。
 * <p>核心：子组统计量计算、首件导入、保存后自动触发控制限/能力重算、删除后清理。</p>
 */
@Slf4j
@Service
public class SpcSubgroupServiceImpl implements SpcSubgroupService {

    private static final int SCALE = 6;
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SpcSubgroupMapper subgroupMapper;
    private final SpcSampleMapper sampleMapper;
    private final SpcParameterMapper parameterMapper;
    private final SpcProcessMapper processMapper;
    private final FaiInspectionRecordMapper faiRecordMapper;
    private final FaiInspectionItemMapper faiItemMapper;
    private final FaiInspectionStandardItemMapper faiStandardItemMapper;
    private final FaiInspectionStandardMapper faiStandardMapper;
    private final SpcChartService chartService;
    private final SpcCapabilityService capabilityService;

    public SpcSubgroupServiceImpl(SpcSubgroupMapper subgroupMapper,
                                 SpcSampleMapper sampleMapper,
                                 SpcParameterMapper parameterMapper,
                                 SpcProcessMapper processMapper,
                                 FaiInspectionRecordMapper faiRecordMapper,
                                 FaiInspectionItemMapper faiItemMapper,
                                 FaiInspectionStandardItemMapper faiStandardItemMapper,
                                 FaiInspectionStandardMapper faiStandardMapper,
                                 SpcChartService chartService,
                                 SpcCapabilityService capabilityService) {
        this.subgroupMapper = subgroupMapper;
        this.sampleMapper = sampleMapper;
        this.parameterMapper = parameterMapper;
        this.processMapper = processMapper;
        this.faiRecordMapper = faiRecordMapper;
        this.faiItemMapper = faiItemMapper;
        this.faiStandardItemMapper = faiStandardItemMapper;
        this.faiStandardMapper = faiStandardMapper;
        this.chartService = chartService;
        this.capabilityService = capabilityService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcSubgroupResponse save(SpcSubgroupSaveDTO dto, LoginUser loginUser) {
        SpcParameter param = requireParam(dto.getParamId(), loginUser);
        List<BigDecimal> values = dto.getSampleValues();
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "样本值不能为空");
        }
        if (values.size() != param.getSubgroupSize()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "样本数量必须等于子组大小 n=" + param.getSubgroupSize());
        }
        return saveInternal(param, values, "手动录入", null, null, loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoImportFromSignedFai(Long faiRecordId, LoginUser loginUser) {
        FaiInspectionRecord fai = faiRecordMapper.selectById(faiRecordId);
        if (fai == null || fai.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件记录不存在");
        }
        if (!"合格".equals(fai.getInspectionResult()) || !"已签".equals(fai.getSignatureStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅可导入合格且已签的首件记录");
        }
        if (!loginUser.getPlantCode().name().equals(fai.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能导入其他分公司的首件记录");
        }
        SpcProcess process = null;
        if (fai.getProcessCode() != null && !fai.getProcessCode().isBlank()) {
            process = processMapper.selectOne(Wrappers.lambdaQuery(SpcProcess.class)
                    .eq(SpcProcess::getPlantCode, fai.getPlantCode())
                    .eq(SpcProcess::getProcessCode, fai.getProcessCode())
                    .eq(SpcProcess::getIsDeleted, 0).last("LIMIT 1"));
        }
        // 兼容迁移前的历史首件记录：仅在没有工序编码或编码未命中时才按名称回退匹配。
        if (process == null) {
            process = processMapper.selectOne(Wrappers.lambdaQuery(SpcProcess.class)
                    .eq(SpcProcess::getPlantCode, fai.getPlantCode())
                    .eq(SpcProcess::getProcessName, fai.getProcessName())
                    .eq(SpcProcess::getIsDeleted, 0).last("LIMIT 1"));
        }
        if (process == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "首件工序未配置到SPC工序库：" + fai.getProcessName());
        }
        fai.setProcessCode(process.getProcessCode());
        faiRecordMapper.updateById(fai);
        List<FaiInspectionItem> items = faiItemMapper.selectList(Wrappers.lambdaQuery(FaiInspectionItem.class)
                .eq(FaiInspectionItem::getFaiRecordId, faiRecordId)
                .eq(FaiInspectionItem::getIsDeleted, 0));
        List<FaiInspectionItem> eligibleItems = items.stream()
                .filter(i -> "是".equals(i.getSpcEnabled()) && i.getActualValue() != null)
                .collect(Collectors.toList());
        Map<Long, List<FaiInspectionItem>> byParameterId = eligibleItems.stream()
                .filter(i -> i.getSpcParameterId() != null)
                .collect(Collectors.groupingBy(FaiInspectionItem::getSpcParameterId, LinkedHashMap::new, Collectors.toList()));
        // 仅兼容迁移前没有参数 ID 的历史首件，新的首件记录必须按参数 ID 联动。
        Map<String, List<FaiInspectionItem>> legacyByCode = eligibleItems.stream()
                .filter(i -> i.getSpcParameterId() == null && i.getParamCode() != null)
                .collect(Collectors.groupingBy(FaiInspectionItem::getParamCode, LinkedHashMap::new, Collectors.toList()));
        List<SpcParameter> params = parameterMapper.selectList(Wrappers.lambdaQuery(SpcParameter.class)
                .eq(SpcParameter::getProcessId, process.getId())
                .eq(SpcParameter::getPlantCode, fai.getPlantCode())
                .eq(SpcParameter::getIsDeleted, 0)
                .eq(SpcParameter::getIsActive, "是"));
        boolean imported = false;
        for (SpcParameter param : params) {
            List<FaiInspectionItem> matched = byParameterId.get(param.getId());
            if (matched == null) {
                matched = legacyByCode.get(param.getParamCode());
            }
            if (matched == null) continue;
            if (matched.stream().anyMatch(i -> !Objects.equals(param.getUnit(), i.getUnit()))) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "首件与SPC参数单位不一致：" + param.getParamCode());
            }
            Long existing = subgroupMapper.selectCount(Wrappers.lambdaQuery(SpcSubgroup.class)
                    .eq(SpcSubgroup::getFaiRecordId, faiRecordId).eq(SpcSubgroup::getParamId, param.getId())
                    .eq(SpcSubgroup::getIsDeleted, 0));
            if (existing == 0) {
                saveInternal(param, matched.stream().map(FaiInspectionItem::getActualValue).collect(Collectors.toList()), "首件自动导入", faiRecordId, fai, loginUser);
            }
            imported = true;
        }
        if (!imported) throw new BusinessException(ResultCode.BAD_REQUEST, "首件没有匹配的SPC参数，请检查参数编码");
    }

    private SpcSubgroupResponse saveInternal(SpcParameter param, List<BigDecimal> values,
                                             String sourceType, Long faiRecordId, FaiInspectionRecord fai, LoginUser loginUser) {
        BigDecimal mean = mean(values);
        BigDecimal range = range(values);
        BigDecimal std = stdDev(values, mean);
        String plantCode = loginUser.getPlantCode().name();

        SpcSubgroup sub = new SpcSubgroup();
        sub.setParamId(param.getId());
        sub.setSubgroupNo(genSubgroupNo(param, plantCode));
        sub.setSampleCount(values.size());
        sub.setMeanValue(mean);
        sub.setRangeValue(range);
        sub.setStdDev(std);
        sub.setSampleTime(LocalDateTime.now());
        sub.setSourceType(sourceType);
        sub.setFaiRecordId(faiRecordId);
        sub.setWorkOrderNo(fai == null ? null : fai.getWorkOrderNo());
        sub.setBatchNo(fai == null ? null : fai.getBatchNo());
        sub.setMaterialCode(fai == null ? null : fai.getMaterialCode());
        sub.setMaterialName(fai == null ? null : fai.getMaterialName());
        sub.setProcessCode(fai == null ? null : fai.getProcessCode());
        sub.setParamCode(param.getParamCode());
        sub.setUnit(param.getUnit());
        sub.setStandardVersion(resolveStandardVersion(fai));
        sub.setSubgroupStatus(values.size() == param.getSubgroupSize() ? "已完成" : "待补样本");
        sub.setPlantCode(plantCode);
        sub.setPlantName(loginUser.getPlantCode().getChineseName());
        sub.setCreatedBy(loginUser.getAccount());
        sub.setUpdatedBy(loginUser.getAccount());
        subgroupMapper.insert(sub);

        List<SpcSample> samples = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            SpcSample s = new SpcSample();
            s.setSubgroupId(sub.getId());
            s.setSampleNo(i + 1);
            s.setSampleValue(values.get(i));
            s.setPlantCode(plantCode);
            s.setPlantName(loginUser.getPlantCode().getChineseName());
            s.setCreatedBy(loginUser.getAccount());
            s.setUpdatedBy(loginUser.getAccount());
            samples.add(s);
        }
        for (SpcSample s : samples) {
            sampleMapper.insert(s);
        }

        long count = subgroupCount(param.getId(), plantCode);
        if ("已完成".equals(sub.getSubgroupStatus()) && count >= 2) {
            chartService.recalcControlLimits(param.getId(), plantCode);
        }
        if ("已完成".equals(sub.getSubgroupStatus()) && count >= 20) {
            capabilityService.recalcCapability(param.getId(), plantCode);
        }
        return detail(sub.getId());
    }

    @Override
    public List<SpcSubgroupResponse> list(Long paramId, String plantCode) {
        List<SpcSubgroup> subs = subgroupMapper.selectList(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getIsDeleted, 0)
                .orderByAsc(SpcSubgroup::getSampleTime, SpcSubgroup::getId));
        if (subs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        Map<Long, List<SpcSample>> bySub = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                        .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0))
                .stream().collect(Collectors.groupingBy(SpcSample::getSubgroupId, LinkedHashMap::new, Collectors.toList()));
        return subs.stream().map(s -> toResponse(s, bySub.get(s.getId()))).collect(Collectors.toList());
    }

    @Override
    public SpcSubgroupResponse detail(Long id) {
        SpcSubgroup sub = subgroupMapper.selectById(id);
        if (sub == null || sub.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "子组不存在");
        }
        List<SpcSample> samples = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                .eq(SpcSample::getSubgroupId, id).eq(SpcSample::getIsDeleted, 0)
                .orderByAsc(SpcSample::getSampleNo));
        return toResponse(sub, samples);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SpcSubgroup sub = subgroupMapper.selectById(id);
        if (sub == null || sub.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "子组不存在");
        }
        String plantCode = sub.getPlantCode();
        Long paramId = sub.getParamId();
        sampleMapper.delete(Wrappers.lambdaQuery(SpcSample.class).eq(SpcSample::getSubgroupId, id));
        subgroupMapper.deleteById(id);

        long count = subgroupCount(paramId, plantCode);
        chartService.recalcControlLimits(paramId, plantCode);
        if (count >= 20) {
            capabilityService.recalcCapability(paramId, plantCode);
        } else {
            capabilityService.clear(paramId, plantCode);
        }
    }

    // ===== 内部工具 =====

    private SpcParameter requireParam(Long paramId, LoginUser loginUser) {
        SpcParameter param = parameterMapper.selectById(paramId);
        if (param == null || param.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        if (!loginUser.getPlantCode().name().equals(param.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能操作其他分公司的参数");
        }
        return param;
    }

    private long subgroupCount(Long paramId, String plantCode) {
        return subgroupMapper.selectCount(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcSubgroupResponse appendPendingSamples(Long subgroupId, SpcPendingSampleAppendDTO dto, LoginUser loginUser) {
        SpcSubgroup subgroup = subgroupMapper.selectById(subgroupId);
        if (subgroup == null || subgroup.getIsDeleted() == 1) throw new BusinessException(ResultCode.NOT_FOUND, "SPC子组不存在");
        if (!loginUser.getPlantCode().name().equals(subgroup.getPlantCode())) throw new BusinessException(ResultCode.FORBIDDEN, "无权补录其他工厂的子组");
        if (!"待补样本".equals(subgroup.getSubgroupStatus()) || !"首件自动导入".equals(subgroup.getSourceType()))
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅可补录首件创建的待补样本子组");
        SpcParameter param = parameterMapper.selectById(subgroup.getParamId());
        List<BigDecimal> values = dto == null ? null : dto.getSampleValues();
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull))
            throw new BusinessException(ResultCode.BAD_REQUEST, "请录入待补的样本值");
        List<SpcSample> existing = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                .eq(SpcSample::getSubgroupId, subgroupId).eq(SpcSample::getIsDeleted, 0).orderByAsc(SpcSample::getSampleNo));
        if (existing.size() + values.size() > param.getSubgroupSize())
            throw new BusinessException(ResultCode.BAD_REQUEST, "补录后样本数不能超过子组大小 n=" + param.getSubgroupSize());
        int nextNo = existing.size() + 1;
        for (BigDecimal value : values) {
            SpcSample sample = new SpcSample();
            sample.setSubgroupId(subgroupId); sample.setSampleNo(nextNo++); sample.setSampleValue(value);
            sample.setPlantCode(subgroup.getPlantCode()); sample.setPlantName(subgroup.getPlantName());
            sample.setCreatedBy(loginUser.getAccount()); sample.setUpdatedBy(loginUser.getAccount()); sampleMapper.insert(sample);
        }
        List<BigDecimal> allValues = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                .eq(SpcSample::getSubgroupId, subgroupId).eq(SpcSample::getIsDeleted, 0).orderByAsc(SpcSample::getSampleNo))
                .stream().map(SpcSample::getSampleValue).collect(Collectors.toList());
        subgroup.setSampleCount(allValues.size());
        if (allValues.size() == param.getSubgroupSize()) {
            BigDecimal avg = mean(allValues); subgroup.setMeanValue(avg); subgroup.setRangeValue(range(allValues)); subgroup.setStdDev(stdDev(allValues, avg));
            subgroup.setSubgroupStatus("已完成");
        }
        subgroup.setUpdatedBy(loginUser.getAccount()); subgroupMapper.updateById(subgroup);
        if ("已完成".equals(subgroup.getSubgroupStatus())) {
            long count = subgroupCount(param.getId(), subgroup.getPlantCode());
            if (count >= 2) chartService.recalcControlLimits(param.getId(), subgroup.getPlantCode());
            if (count >= 20) capabilityService.recalcCapability(param.getId(), subgroup.getPlantCode());
        }
        return detail(subgroupId);
    }

    private Integer resolveStandardVersion(FaiInspectionRecord fai) {
        if (fai == null) return null;
        FaiInspectionItem item = faiItemMapper.selectOne(Wrappers.lambdaQuery(FaiInspectionItem.class)
                .eq(FaiInspectionItem::getFaiRecordId, fai.getId()).isNotNull(FaiInspectionItem::getStandardItemId)
                .eq(FaiInspectionItem::getIsDeleted, 0).last("LIMIT 1"));
        if (item == null) return null;
        FaiInspectionStandardItem standardItem = faiStandardItemMapper.selectById(item.getStandardItemId());
        if (standardItem == null) return null;
        FaiInspectionStandard standard = faiStandardMapper.selectById(standardItem.getStandardId());
        return standard == null ? null : standard.getStdVersion();
    }

    private String genSubgroupNo(SpcParameter param, String plantCode) {
        String ymd = LocalDate.now().format(YMD);
        long today = subgroupMapper.selectCount(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, param.getId())
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getIsDeleted, 0)
                .apply("to_char(created_at,'yyyyMMdd') = {0}", ymd));
        return String.format("SG-%s-%s-%s-%04d", plantCode, param.getParamCode(), ymd, today + 1);
    }

    private BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal range(List<BigDecimal> values) {
        BigDecimal max = values.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal min = values.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return max.subtract(min).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal stdDev(List<BigDecimal> values, BigDecimal mean) {
        int n = values.size();
        if (n < 2) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        double meanD = mean.doubleValue();
        double sumSq = values.stream().mapToDouble(v -> {
            double d = v.doubleValue() - meanD;
            return d * d;
        }).sum();
        double stdD = Math.sqrt(sumSq / (n - 1));
        return new BigDecimal(stdD).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private SpcSubgroupResponse toResponse(SpcSubgroup sub, List<SpcSample> samples) {
        SpcSubgroupResponse r = new SpcSubgroupResponse();
        r.setId(sub.getId());
        r.setParamId(sub.getParamId());
        r.setSubgroupNo(sub.getSubgroupNo());
        r.setSampleCount(sub.getSampleCount());
        r.setMeanValue(sub.getMeanValue());
        r.setRangeValue(sub.getRangeValue());
        r.setStdDev(sub.getStdDev());
        r.setSampleTime(sub.getSampleTime());
        r.setSourceType(sub.getSourceType());
        r.setFaiRecordId(sub.getFaiRecordId());
        r.setWorkOrderNo(sub.getWorkOrderNo());
        r.setBatchNo(sub.getBatchNo());
        r.setMaterialCode(sub.getMaterialCode());
        r.setMaterialName(sub.getMaterialName());
        r.setProcessCode(sub.getProcessCode());
        r.setSubgroupStatus(sub.getSubgroupStatus());
        r.setPlantCode(sub.getPlantCode());
        r.setPlantName(sub.getPlantName());
        if (samples != null) {
            r.setSamples(samples.stream().map(s -> {
                SpcSampleResponse sr = new SpcSampleResponse();
                sr.setId(s.getId());
                sr.setSubgroupId(s.getSubgroupId());
                sr.setSampleNo(s.getSampleNo());
                sr.setSampleValue(s.getSampleValue());
                return sr;
            }).collect(Collectors.toList()));
        }
        return r;
    }
}
