package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import org.springframework.util.StringUtils;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcBatchNoDTO;
import com.kangli.qms.service.spc.dto.SpcSubgroupResponse;
import com.kangli.qms.service.spc.dto.SpcSubgroupSaveDTO;
import com.kangli.qms.service.spc.dto.SpcPendingSampleAppendDTO;
import com.kangli.qms.service.spc.dto.SpcSampleResponse;
import com.kangli.qms.domain.fai.entity.FaiInspectionItem;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.fai.mapper.FaiInspectionItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.spc.SpcCapabilityService;
import com.kangli.qms.service.spc.SpcChartService;
import com.kangli.qms.service.spc.SpcSubgroupService;
import com.kangli.qms.service.fai.FaiStandardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M4 SPC 子组（数据采集）实现。
 * <p>核心：子组统计量计算、首件导入、保存后自动触发控制限/能力重算、删除后清理。</p>
 */
@Slf4j
@Service
public class SpcSubgroupServiceImpl implements SpcSubgroupService {

    private static final int SCALE = 6;
    private static final String LIMIT_ONE = "LIMIT 1";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectMapper objectMapper;
    private final SpcSubgroupMapper subgroupMapper;
    private final SpcSampleMapper sampleMapper;
    private final SpcParameterMapper parameterMapper;
    private final SpcProcessMapper processMapper;
    private final FaiInspectionRecordMapper faiRecordMapper;
    private final FaiInspectionItemMapper faiItemMapper;
    private final FaiInspectionStandardItemMapper faiStandardItemMapper;
    private final FaiInspectionStandardMapper faiStandardMapper;
    private final MaterialInspectionMapper matMapper;
    private final FinishedGoodsInspectionMapper fgMapper;
    private final SpcChartService chartService;
    private final SpcCapabilityService capabilityService;
    private final FaiStandardService faiStandardService;

    public SpcSubgroupServiceImpl(ObjectMapper objectMapper,
                                 SpcSubgroupMapper subgroupMapper,
                                 SpcSampleMapper sampleMapper,
                                 SpcParameterMapper parameterMapper,
                                 SpcProcessMapper processMapper,
                                 FaiInspectionRecordMapper faiRecordMapper,
                                 FaiInspectionItemMapper faiItemMapper,
                                 FaiInspectionStandardItemMapper faiStandardItemMapper,
                                 FaiInspectionStandardMapper faiStandardMapper,
                                 MaterialInspectionMapper matMapper,
                                 FinishedGoodsInspectionMapper fgMapper,
                                 SpcChartService chartService,
                                 SpcCapabilityService capabilityService,
                                 FaiStandardService faiStandardService) {
        this.objectMapper = objectMapper;
        this.subgroupMapper = subgroupMapper;
        this.sampleMapper = sampleMapper;
        this.parameterMapper = parameterMapper;
        this.processMapper = processMapper;
        this.faiRecordMapper = faiRecordMapper;
        this.faiItemMapper = faiItemMapper;
        this.faiStandardItemMapper = faiStandardItemMapper;
        this.faiStandardMapper = faiStandardMapper;
        this.matMapper = matMapper;
        this.fgMapper = fgMapper;
        this.chartService = chartService;
        this.capabilityService = capabilityService;
        this.faiStandardService = faiStandardService;
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
        // 批次号和条码必填校验
        if (dto.getBatchNo() == null || dto.getBatchNo().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "批次号不能为空");
        }
        if (dto.getBarcode() == null || dto.getBarcode().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "条码不能为空");
        }
        SpcSubgroupResponse result = saveInternal(param, values, "手动录入", null, null,
                dto.getItemType(), dto.getItemCode(), dto.getBatchNo(), dto.getBarcode(), dto.getMaterialName(), loginUser);
        // 手动录入子组后，用条码反查 FAI 记录并标记标准为已使用
        markStandardUsedByBarcode(dto.getBarcode(), loginUser.getPlantCode().name());
        return result;
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
                    .eq(SpcProcess::getIsDeleted, 0).last(LIMIT_ONE));
        }
        // 兼容迁移前的历史首件记录：仅在没有工序编码或编码未命中时才按名称回退匹配。
        if (process == null) {
            process = processMapper.selectOne(Wrappers.lambdaQuery(SpcProcess.class)
                    .eq(SpcProcess::getPlantCode, fai.getPlantCode())
                    .eq(SpcProcess::getProcessName, fai.getProcessName())
                    .eq(SpcProcess::getIsDeleted, 0).last(LIMIT_ONE));
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
                saveInternal(param, matched.stream().map(FaiInspectionItem::getActualValue).collect(Collectors.toList()), "首件自动导入", faiRecordId, fai, null, null, null, null, null, loginUser);
            }
            imported = true;
        }
        if (!imported) throw new BusinessException(ResultCode.BAD_REQUEST, "首件没有匹配的SPC参数，请检查参数编码");
    }

    private SpcSubgroupResponse saveInternal(SpcParameter param, List<BigDecimal> values,
                                             String sourceType, Long faiRecordId, FaiInspectionRecord fai,
                                             String itemType, String itemCode, String batchNo, String barcode, String materialName, LoginUser loginUser) {
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
        sub.setSampleTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        sub.setSourceType(sourceType);
        sub.setFaiRecordId(faiRecordId);
        sub.setWorkOrderNo(fai == null ? null : fai.getWorkOrderNo());
        sub.setBatchNo(batchNo != null ? batchNo : (fai == null ? null : fai.getBatchNo()));
        sub.setBarcode(barcode != null ? barcode : (fai == null ? null : fai.getItemBarcode()));
        // 首件自动导入时未显式传分类，此处从首件记录继承，避免 SPC 子组丢失产品/物料归属，
        // 导致控制图按分类筛选时查不到由首件带入的数据。
        String finalItemType = itemType != null ? itemType : (fai == null ? null : fai.getItemType());
        String finalItemCode = itemCode != null ? itemCode : (fai == null ? null : fai.getItemCode());
        sub.setItemType(finalItemType);
        sub.setItemCode(finalItemCode);
        // 冗余兼容列（旧报表按 materialCode/materialName 读取）：
        // 名称统一优先取首件的 itemName，产品场景才不会回落成物料名称。
        String faiName = fai == null ? null : (fai.getItemName() != null ? fai.getItemName() : fai.getMaterialName());
        if ("MATERIAL".equals(finalItemType)) {
            sub.setMaterialCode(finalItemCode != null ? finalItemCode : (fai == null ? null : fai.getMaterialCode()));
        } else {
            sub.setMaterialCode(fai == null ? null : fai.getMaterialCode());
        }
        sub.setMaterialName(materialName != null ? materialName : faiName);
        sub.setProcessCode(fai == null ? null : fai.getProcessCode());
        sub.setParamCode(param.getParamCode());
        sub.setUnit(param.getUnit());
        // 标准快照：无论首件导入还是手动录入，均从当时 SpcParameter 取公差副本写入，
        // 避免标准库后续改版导致历史子组/控制图随之变动。
        sub.setTargetValue(param.getTargetValue());
        sub.setUpperSpecLimit(param.getUpperSpecLimit());
        sub.setLowerSpecLimit(param.getLowerSpecLimit());
        // 版本隔离：首件导入沿用记录追溯链版本；手动录入从当前激活标准取版本，消除"版本孤岛"
        sub.setStandardVersion(fai != null
                ? resolveStandardVersion(fai)
                : resolveActiveStandardVersion(param, itemType, itemCode, plantCode));
        sub.setSubgroupStatus(values.size() == param.getSubgroupSize() ? "已完成" : "待补样本");
        sub.setPlantCode(plantCode);
        sub.setPlantName(loginUser.getPlantCode().getChineseName());
        sub.setCreatedBy(loginUser.getAccount());
        sub.setUpdatedBy(loginUser.getAccount());
        subgroupMapper.insert(sub);

        // 标记标准为已使用（usageStatus=1 表示已被引用），仅首次引用时标记（幂等）
        if (faiRecordId != null) {
            markStandardUsed(faiRecordId);
        }

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
            s.setBarcode(sub.getBarcode());
            samples.add(s);
        }
        for (SpcSample s : samples) {
            sampleMapper.insert(s);
        }

        long count = subgroupCount(param.getId(), plantCode, finalItemType, finalItemCode);
        try {
            if ("已完成".equals(sub.getSubgroupStatus()) && count >= 2) {
                chartService.recalcControlLimits(param.getId(), plantCode, finalItemType, finalItemCode);
            }
            if ("已完成".equals(sub.getSubgroupStatus()) && count >= 20) {
                capabilityService.recalcCapability(param.getId(), plantCode, sub.getItemType(), sub.getItemCode(), sub.getBatchNo());
            }
        } catch (Exception e) {
            // 重算失败不应回滚子组保存（recalc 已用 NESTED 事务隔离到保存点）
            log.error("SPC 重算失败（不影响子组保存）：paramId={}", param.getId(), e);
        }
        return detail(sub.getId());
    }

    @Override
    public List<SpcSubgroupResponse> list(Long paramId, String plantCode) {
        List<SpcSubgroup> subs = subgroupMapper.selectList(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getIsDeleted, 0)
                .orderByDesc(SpcSubgroup::getSampleTime, SpcSubgroup::getId));
        if (subs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        Map<Long, List<SpcSample>> bySub = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                        .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0))
                .stream().collect(Collectors.groupingBy(SpcSample::getSubgroupId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Long> paramProcessMap = buildParamProcessMap(subs);
        return subs.stream().map(s -> toResponse(s, bySub.get(s.getId()), paramProcessMap)).collect(Collectors.toList());
    }

    @Override
    public List<SpcSubgroupResponse> listByFai(Long faiRecordId, String plantCode) {
        List<SpcSubgroup> subs = subgroupMapper.selectList(Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getFaiRecordId, faiRecordId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getIsDeleted, 0)
                .orderByDesc(SpcSubgroup::getSampleTime, SpcSubgroup::getId));
        if (subs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = subs.stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        Map<Long, List<SpcSample>> bySub = sampleMapper.selectList(Wrappers.lambdaQuery(SpcSample.class)
                        .in(SpcSample::getSubgroupId, ids).eq(SpcSample::getIsDeleted, 0))
                .stream().collect(Collectors.groupingBy(SpcSample::getSubgroupId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Long> paramProcessMap = buildParamProcessMap(subs);
        return subs.stream().map(s -> toResponse(s, bySub.get(s.getId()), paramProcessMap)).collect(Collectors.toList());
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
        Map<Long, Long> paramProcessMap = Collections.singletonMap(
                sub.getParamId(), resolveProcessId(sub.getParamId()));
        return toResponse(sub, samples, paramProcessMap);
    }

    /** 批量构建 paramId -> processId 映射，避免列表场景 N+1 查询 */
    private Map<Long, Long> buildParamProcessMap(List<SpcSubgroup> subs) {
        Set<Long> paramIds = subs.stream().map(SpcSubgroup::getParamId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (paramIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return parameterMapper.selectList(Wrappers.lambdaQuery(SpcParameter.class)
                        .in(SpcParameter::getId, paramIds).eq(SpcParameter::getIsDeleted, 0))
                .stream().collect(Collectors.toMap(SpcParameter::getId, SpcParameter::getProcessId, (a, b) -> a));
    }

    private Long resolveProcessId(Long paramId) {
        if (paramId == null) return null;
        SpcParameter param = parameterMapper.selectById(paramId);
        return param == null ? null : param.getProcessId();
    }

    private String resolveParamName(Long paramId) {
        if (paramId == null) return null;
        SpcParameter param = parameterMapper.selectById(paramId);
        return param == null ? null : param.getParamName();
    }

    private String resolveProcessName(Long processId) {
        if (processId == null) return null;
        SpcProcess process = processMapper.selectById(processId);
        return process == null ? null : process.getProcessName();
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

        long count = subgroupCount(paramId, plantCode, sub.getItemType(), sub.getItemCode());
        try {
            chartService.recalcControlLimits(paramId, plantCode, sub.getItemType(), sub.getItemCode());
            if (count >= 20) {
                capabilityService.recalcCapability(paramId, plantCode, sub.getItemType(), sub.getItemCode(), sub.getBatchNo());
            } else {
                capabilityService.clear(paramId, plantCode, sub.getItemType(), sub.getItemCode());
            }
        } catch (Exception e) {
            // 重算失败不应回滚子组删除（recalc 已用 NESTED 事务隔离到保存点）
            log.error("SPC 重算失败（不影响子组删除）：paramId={}, subgroupId={}", paramId, id, e);
        }
    }

    // ===== 批次号来源（成品表 / 物料表反查） =====

    @Override
    public List<SpcBatchNoDTO> listBatchNos(String itemType, String itemCode, LoginUser loginUser) {
        if (!StringUtils.hasText(itemType) || !StringUtils.hasText(itemCode)) {
            return Collections.emptyList();
        }
        String plantCode = loginUser.getPlantCode().name();
        List<SpcBatchNoDTO> result = new ArrayList<>();
        if ("PRODUCT".equalsIgnoreCase(itemType)) {
            List<FinishedGoodsInspection> list = fgMapper.selectList(Wrappers.lambdaQuery(FinishedGoodsInspection.class)
                    .eq(FinishedGoodsInspection::getPlantCode, plantCode)
                    .eq(FinishedGoodsInspection::getMaterialCode, itemCode.trim())
                    .isNotNull(FinishedGoodsInspection::getProdBatchOrSn)
                    .select(FinishedGoodsInspection::getProdBatchOrSn, FinishedGoodsInspection::getProductName)
                    .orderByAsc(FinishedGoodsInspection::getProdBatchOrSn));
            for (FinishedGoodsInspection fg : list) {
                SpcBatchNoDTO dto = new SpcBatchNoDTO();
                dto.setBatchNo(fg.getProdBatchOrSn());
                dto.setItemName(fg.getProductName());
                result.add(dto);
            }
        } else if ("MATERIAL".equalsIgnoreCase(itemType)) {
            List<MaterialInspection> list = matMapper.selectList(Wrappers.lambdaQuery(MaterialInspection.class)
                    .eq(MaterialInspection::getPlantCode, plantCode)
                    .eq(MaterialInspection::getMaterialCode, itemCode.trim())
                    .isNotNull(MaterialInspection::getMaterialBatchNo)
                    .select(MaterialInspection::getMaterialBatchNo, MaterialInspection::getMaterialName)
                    .orderByAsc(MaterialInspection::getMaterialBatchNo));
            for (MaterialInspection mat : list) {
                SpcBatchNoDTO dto = new SpcBatchNoDTO();
                dto.setBatchNo(mat.getMaterialBatchNo());
                dto.setItemName(mat.getMaterialName());
                result.add(dto);
            }
        }
        // 去重（同一批号可能多条记录）
        Map<String, SpcBatchNoDTO> dedup = new LinkedHashMap<>();
        for (SpcBatchNoDTO d : result) {
            dedup.putIfAbsent(d.getBatchNo(), d);
        }
        return new ArrayList<>(dedup.values());
    }

    @Override
    public Map<String, Object> sourceDetail(String itemType, String itemCode, String batchNo, String barcode, String plantCode) {
        if (!StringUtils.hasText(itemType) || !StringUtils.hasText(itemCode)) {
            return null;
        }
        if (!StringUtils.hasText(batchNo) && !StringUtils.hasText(barcode)) {
            return null;
        }
        // 溯源反查按「子组自身厂区」过滤来源表（而非登录用户厂区），
        // 否则非本厂区账号（如 admin/SZ）查不到 MZ 等厂区的来源行。
        plantCode = StringUtils.hasText(plantCode) ? plantCode.trim() : null;
        itemCode = itemCode.trim();
        final String batchNoKey = StringUtils.hasText(batchNo) ? batchNo.trim() : null;
        final String barcodeKey = StringUtils.hasText(barcode) ? barcode.trim() : null;
        if ("PRODUCT".equalsIgnoreCase(itemType)) {
            FinishedGoodsInspection fg = fgMapper.selectOne(Wrappers.lambdaQuery(FinishedGoodsInspection.class)
                    .eq(FinishedGoodsInspection::getPlantCode, plantCode)
                    .eq(FinishedGoodsInspection::getMaterialCode, itemCode)
                    .eq(FinishedGoodsInspection::getProdBatchOrSn, batchNoKey).last(LIMIT_ONE));
            if (fg == null) return null;
            return toMap(fg);
        } else if ("MATERIAL".equalsIgnoreCase(itemType)) {
            // 批次号或条码任一命中即可（条码优先，MATERIAL 场景常以此作为追溯标识）
            MaterialInspection mat = matMapper.selectOne(Wrappers.lambdaQuery(MaterialInspection.class)
                    .eq(MaterialInspection::getPlantCode, plantCode)
                    .eq(MaterialInspection::getMaterialCode, itemCode)
                    .and(w -> w
                            .eq(StringUtils.hasText(batchNoKey), MaterialInspection::getMaterialBatchNo, batchNoKey)
                            .or()
                            .eq(StringUtils.hasText(barcodeKey), MaterialInspection::getMaterialBarcode, barcodeKey))
                    .last(LIMIT_ONE));
            if (mat == null) return null;
            return toMap(mat);
        }
        return null;
    }

    private Map<String, Object> toMap(Object entity) {
        if (entity == null) return new LinkedHashMap<>();
        // BeanUtils.copyProperties 无法写入无 setter 的 Map 目标，改用 Jackson 转换实体→Map
        return objectMapper.convertValue(entity, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
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

    private long subgroupCount(Long paramId, String plantCode, String itemType, String itemCode) {
        var q = Wrappers.lambdaQuery(SpcSubgroup.class)
                .eq(SpcSubgroup::getParamId, paramId)
                .eq(SpcSubgroup::getPlantCode, plantCode)
                .eq(SpcSubgroup::getSubgroupStatus, "已完成")
                .eq(SpcSubgroup::getIsDeleted, 0);
        // 按 item 维度隔离计数，与 recalc 使用的子组范围保持一致：
        // 带维度时只数同 item 的已完成子组；全局基线（item 为空）沿用全部。
        if (StringUtils.hasText(itemType) && StringUtils.hasText(itemCode)) {
            q.eq(SpcSubgroup::getItemType, itemType).eq(SpcSubgroup::getItemCode, itemCode);
        }
        return subgroupMapper.selectCount(q);
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
        if (param == null) throw new BusinessException(ResultCode.NOT_FOUND, "SPC参数不存在");
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
            long count = subgroupCount(param.getId(), subgroup.getPlantCode(), subgroup.getItemType(), subgroup.getItemCode());
            try {
                if (count >= 2) chartService.recalcControlLimits(param.getId(), subgroup.getPlantCode(), subgroup.getItemType(), subgroup.getItemCode());
                if (count >= 20) capabilityService.recalcCapability(param.getId(), subgroup.getPlantCode(), subgroup.getItemType(), subgroup.getItemCode(), subgroup.getBatchNo());
            } catch (Exception e) {
                // 重算失败不应回滚样本补录（recalc 已用 NESTED 事务隔离到保存点）
                log.error("SPC 重算失败（不影响样本补录）：paramId={}, subgroupId={}", param.getId(), subgroupId, e);
            }
        }
        return detail(subgroupId);
    }

    private Integer resolveStandardVersion(FaiInspectionRecord fai) {
        if (fai == null) return null;
        FaiInspectionItem item = faiItemMapper.selectOne(Wrappers.lambdaQuery(FaiInspectionItem.class)
                .eq(FaiInspectionItem::getFaiRecordId, fai.getId()).isNotNull(FaiInspectionItem::getStandardItemId)
                .eq(FaiInspectionItem::getIsDeleted, 0).last(LIMIT_ONE));
        if (item == null) return null;
        FaiInspectionStandardItem standardItem = faiStandardItemMapper.selectById(item.getStandardItemId());
        if (standardItem == null) return null;
        FaiInspectionStandard standard = faiStandardMapper.selectById(standardItem.getStandardId());
        return standard == null ? null : standard.getStdVersion();
    }

    /**
     * 手动录入子组解析标准版本：从当前激活标准（按 分类+代码+工序）取版本号写入 standard_version，
     * 使手动录入数据同样纳入版本隔离体系，消除"版本孤岛"。
     * 若对应 代码+工序 尚未建立激活标准，返回 null（与历史行为一致，不报错）。
     */
    private Integer resolveActiveStandardVersion(SpcParameter param, String itemType, String itemCode, String plantCode) {
        if (param == null || !StringUtils.hasText(itemCode)) {
            return null;
        }
        String processName = null;
        if (param.getProcessId() != null) {
            SpcProcess process = processMapper.selectById(param.getProcessId());
            processName = process != null ? process.getProcessName() : null;
        }
        if (!StringUtils.hasText(processName)) {
            return null;
        }
        FaiStandardResponse active = faiStandardService.latestActive(itemCode, itemType, processName, plantCode);
        return active != null ? active.getStdVersion() : null;
    }

    /**
     * 标记标准为已使用（usageStatus=1 表示已被引用），仅首次引用时标记（幂等）。
     * 通过 faiRecordId → 检验项 → 标准项 → 标准ID 追溯链路去重后条件更新。
     */
    private void markStandardUsed(Long faiRecordId) {
        List<FaiInspectionItem> items = faiItemMapper.selectList(
            Wrappers.lambdaQuery(FaiInspectionItem.class)
                .eq(FaiInspectionItem::getFaiRecordId, faiRecordId)
                .isNotNull(FaiInspectionItem::getStandardItemId)
                .eq(FaiInspectionItem::getIsDeleted, 0));
        if (items.isEmpty()) return;
        Set<Long> standardIds = new HashSet<>();
        for (FaiInspectionItem item : items) {
            FaiInspectionStandardItem stdItem = faiStandardItemMapper.selectById(item.getStandardItemId());
            if (stdItem != null && stdItem.getIsDeleted() == 0) {
                standardIds.add(stdItem.getStandardId());
            }
        }
        for (Long standardId : standardIds) {
            int rows = faiStandardMapper.update(null,
                Wrappers.lambdaUpdate(FaiInspectionStandard.class)
                    .set(FaiInspectionStandard::getUsageStatus, 1)
                    .set(FaiInspectionStandard::getLastUsedAt, LocalDateTime.now())
                    .eq(FaiInspectionStandard::getId, standardId));
            log.info("标记标准已使用 standardId={} faiRecordId={} rows={}", standardId, faiRecordId, rows);
        }
    }

    /**
     * 通过条码反查 FAI 检验记录，标记关联标准为已使用。
     * 用于手动录入子组的路径（此时 faiRecordId 为空，需反查）。
     */
    private void markStandardUsedByBarcode(String barcode, String plantCode) {
        if (barcode == null || barcode.trim().isEmpty()) return;
        List<FaiInspectionRecord> records = faiRecordMapper.selectList(
            Wrappers.lambdaQuery(FaiInspectionRecord.class)
                .eq(FaiInspectionRecord::getItemBarcode, barcode)
                .eq(FaiInspectionRecord::getPlantCode, plantCode)
                .eq(FaiInspectionRecord::getIsDeleted, 0));
        for (FaiInspectionRecord record : records) {
            markStandardUsed(record.getId());
        }
    }

    private String genSubgroupNo(SpcParameter param, String plantCode) {
        String ymd = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(YMD);
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
        return BigDecimal.valueOf(stdD).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private SpcSubgroupResponse toResponse(SpcSubgroup sub, List<SpcSample> samples, Map<Long, Long> paramProcessMap) {
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
        r.setBarcode(sub.getBarcode());
        r.setMaterialCode(sub.getMaterialCode());
        r.setMaterialName(sub.getMaterialName());
        r.setItemType(sub.getItemType());
        r.setItemCode(sub.getItemCode());
        // processId 由 paramId 反查填充（paramProcessMap 命中则直接用，未命中兜底单查）
        Long processId = paramProcessMap != null ? paramProcessMap.get(sub.getParamId()) : null;
        if (processId == null) {
            processId = resolveProcessId(sub.getParamId());
        }
        r.setProcessId(processId);
        r.setProcessCode(sub.getProcessCode());
        // 名称/编码/单位快照补充，供前端溯源抽屉主区展示
        r.setItemName(sub.getMaterialName());
        r.setParamCode(sub.getParamCode());
        r.setUnit(sub.getUnit());
        r.setParamName(resolveParamName(sub.getParamId()));
        r.setProcessName(resolveProcessName(processId));
        r.setTargetValue(sub.getTargetValue());
        r.setUpperSpecLimit(sub.getUpperSpecLimit());
        r.setLowerSpecLimit(sub.getLowerSpecLimit());
        r.setStandardVersion(sub.getStandardVersion());
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
                sr.setBarcode(s.getBarcode());
                return sr;
            }).collect(Collectors.toList()));
        }
        return r;
    }
}
