package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcParameterRequest;
import com.kangli.qms.service.spc.dto.SpcParameterResponse;
import com.kangli.qms.domain.spc.entity.SpcCapability;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import com.kangli.qms.domain.spc.mapper.SpcControlLimitMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.service.spc.SpcParameterService;
import com.kangli.qms.service.fai.FaiInspectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M4 SPC 关键参数管理实现。
 */
@Slf4j
@Service
public class SpcParameterServiceImpl implements SpcParameterService {

    private final SpcParameterMapper parameterMapper;
    private final FaiInspectionService faiInspectionService;
    private final SpcSubgroupMapper subgroupMapper;
    private final SpcSampleMapper sampleMapper;
    private final SpcControlLimitMapper controlLimitMapper;
    private final SpcCapabilityMapper capabilityMapper;
    private final FaiInspectionStandardItemMapper standardItemMapper;

    public SpcParameterServiceImpl(SpcParameterMapper parameterMapper,
                                   FaiInspectionService faiInspectionService,
                                   SpcSubgroupMapper subgroupMapper,
                                   SpcSampleMapper sampleMapper,
                                   SpcControlLimitMapper controlLimitMapper,
                                   SpcCapabilityMapper capabilityMapper,
                                   FaiInspectionStandardItemMapper standardItemMapper) {
        this.parameterMapper = parameterMapper;
        this.faiInspectionService = faiInspectionService;
        this.subgroupMapper = subgroupMapper;
        this.sampleMapper = sampleMapper;
        this.controlLimitMapper = controlLimitMapper;
        this.capabilityMapper = capabilityMapper;
        this.standardItemMapper = standardItemMapper;
    }

    @Override
    public List<SpcParameterResponse> list(Long processId, String plantCode) {
        LambdaQueryWrapper<SpcParameter> qw = Wrappers.lambdaQuery(SpcParameter.class)
                .eq(SpcParameter::getPlantCode, plantCode)
                .eq(SpcParameter::getIsDeleted, 0)
                .eq(processId != null, SpcParameter::getProcessId, processId)
                .orderByAsc(SpcParameter::getId);
        List<SpcParameter> params = parameterMapper.selectList(qw);
        if (params.isEmpty()) {
            return List.of();
        }
        // 批量统计各参数下的子组数量（一次查询，按 paramId 分组计数）
        List<Long> paramIds = params.stream().map(SpcParameter::getId).collect(Collectors.toList());
        Map<Long, Long> subgroupCounts = subgroupMapper.selectList(
                Wrappers.lambdaQuery(SpcSubgroup.class)
                        .in(SpcSubgroup::getParamId, paramIds)
                        .eq(SpcSubgroup::getIsDeleted, 0)
                        .select(SpcSubgroup::getParamId)
        ).stream().collect(Collectors.groupingBy(SpcSubgroup::getParamId, Collectors.counting()));
        // 批量统计各参数被 FAI 检验标准引用的次数（一次查询，按 spcParameterId 分组计数）
        Map<Long, Long> faiRefCounts = standardItemMapper.selectList(
                Wrappers.lambdaQuery(FaiInspectionStandardItem.class)
                        .in(FaiInspectionStandardItem::getSpcParameterId, paramIds)
                        .eq(FaiInspectionStandardItem::getIsDeleted, 0)
                        .select(FaiInspectionStandardItem::getSpcParameterId)
        ).stream().collect(Collectors.groupingBy(FaiInspectionStandardItem::getSpcParameterId, Collectors.counting()));
        return params.stream().map(p -> {
            SpcParameterResponse r = toResponse(p);
            r.setSubgroupCount(subgroupCounts.getOrDefault(p.getId(), 0L));
            r.setFaiReferenceCount(faiRefCounts.getOrDefault(p.getId(), 0L));
            return r;
        }).collect(Collectors.toList());
    }

    @Override
    public SpcParameterResponse detail(Long id) {
        SpcParameter e = parameterMapper.selectById(id);
        if (e == null || e.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        return toResponse(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcParameterResponse create(SpcParameterRequest request, LoginUser loginUser) {
        // 改进③：同工序下参数编码唯一性校验
        validateParamCodeUnique(request.getProcessId(), request.getParamCode(), null, loginUser.getPlantCode().name());

        SpcParameter e = new SpcParameter();
        e.setProcessId(request.getProcessId());
        e.setParamCode(request.getParamCode());
        e.setParamName(request.getParamName());
        e.setParamType(request.getParamType());
        e.setUnit(request.getUnit());
        e.setIsActive(request.getIsActive() == null ? "是" : request.getIsActive());
        e.setDecimalPlaces(request.getDecimalPlaces() == null ? 3 : request.getDecimalPlaces());
        e.setIsCritical(request.getIsCritical() == null ? "否" : request.getIsCritical());
        e.setChangeRemark(request.getChangeRemark());
        // 规格上限/下限/目标值/子组大小 n/控制图类型 均不在字典层填写；
        // 保留 spc_parameter 表已有列不变，留待【物料‑工序‑参数】标准层单独配置。
        e.setPlantCode(loginUser.getPlantCode().name());
        e.setPlantName(loginUser.getPlantCode().getChineseName());
        e.setCreatedBy(loginUser.getAccount());
        e.setUpdatedBy(loginUser.getAccount());
        parameterMapper.insert(e);
        return toResponse(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcParameterResponse update(Long id, SpcParameterRequest request, LoginUser loginUser) {
        SpcParameter current = parameterMapper.selectById(id);
        if (current == null || current.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        if (request.getVersion() != null && !request.getVersion().equals(current.getVersion())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "记录已被他人修改，请刷新后重试");
        }
        // 改进③：同工序下参数编码唯一性校验
        validateParamCodeUnique(request.getProcessId(), request.getParamCode(), id, current.getPlantCode());

        current.setProcessId(request.getProcessId());
        current.setParamCode(request.getParamCode());
        current.setParamName(request.getParamName());
        current.setParamType(request.getParamType());
        current.setUnit(request.getUnit());
        current.setIsActive(request.getIsActive() == null ? current.getIsActive() : request.getIsActive());
        current.setDecimalPlaces(request.getDecimalPlaces() == null ? current.getDecimalPlaces() : request.getDecimalPlaces());
        current.setIsCritical(request.getIsCritical() == null ? current.getIsCritical() : request.getIsCritical());
        current.setChangeRemark(request.getChangeRemark());
        // 规格上限/下限/目标值/子组大小 n/控制图类型 不在字典层维护，不通过 Request 覆写
        // 不再手动 setVersion：@Version 拦截器会以当前版本作 WHERE 并自动 +1，避免误判导致更新 0 行
        current.setUpdatedBy(loginUser.getAccount());
        int rows = parameterMapper.updateById(current);
        if (rows == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "记录已被他人修改，请刷新后重试");
        }
        faiInspectionService.syncUpdatedSpcParameter(current, loginUser);
        // refetch 同步 @Version：updateById 后 DB version 已 +1，内存对象 version 已过时
        current = parameterMapper.selectById(current.getId());
        return toResponse(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SpcParameter current = parameterMapper.selectById(id);
        if (current == null || current.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }

        // Guard 1: 子组数据检查 — 存在子组数据时拒绝删除
        long subgroupCount = subgroupMapper.selectCount(
                Wrappers.lambdaQuery(SpcSubgroup.class)
                        .eq(SpcSubgroup::getParamId, id)
                        .eq(SpcSubgroup::getIsDeleted, 0));
        if (subgroupCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该参数下存在子组数据，无法删除");
        }

        // Guard 2: FAI 检验标准引用检查 — 被检验标准引用时拒绝删除
        long refCount = standardItemMapper.selectCount(
                Wrappers.lambdaQuery(FaiInspectionStandardItem.class)
                        .eq(FaiInspectionStandardItem::getSpcParameterId, id)
                        .eq(FaiInspectionStandardItem::getIsDeleted, 0));
        if (refCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该参数已被检验标准引用，请先解除关联");
        }

        // 级联删除子组、样本、控制限和能力数据
        cascadeDeleteByParam(id);

        parameterMapper.deleteById(id);
    }

    /**
     * 逻辑删除某参数下的全部子表数据（子组→样本、控制限、能力指数），避免脏数据。
     *
     * @param paramId 关键参数 id
     */
    private void cascadeDeleteByParam(Long paramId) {
        List<Long> subgroupIds = subgroupMapper.selectList(
                        Wrappers.lambdaQuery(SpcSubgroup.class)
                                .eq(SpcSubgroup::getParamId, paramId)
                                .eq(SpcSubgroup::getIsDeleted, 0)
                                .select(SpcSubgroup::getId))
                .stream().map(SpcSubgroup::getId).collect(Collectors.toList());
        if (!subgroupIds.isEmpty()) {
            // 先清子组下的样本
            sampleMapper.delete(Wrappers.lambdaQuery(SpcSample.class)
                    .in(SpcSample::getSubgroupId, subgroupIds));
            // 再清子组
            subgroupMapper.delete(Wrappers.lambdaQuery(SpcSubgroup.class)
                    .in(SpcSubgroup::getId, subgroupIds));
        }
        // 控制限与能力指数
        controlLimitMapper.delete(Wrappers.lambdaQuery(SpcControlLimit.class)
                .eq(SpcControlLimit::getParamId, paramId));
        capabilityMapper.delete(Wrappers.lambdaQuery(SpcCapability.class)
                .eq(SpcCapability::getParamId, paramId));
    }

    /**
     * 改进③：校验同一工序下参数编码唯一性。
     *
     * @param processId 工序ID
     * @param paramCode 参数编码
     * @param excludeId 编辑时排除自身ID（新建传null）
     * @param plantCode 厂区编码
     */
    private void validateParamCodeUnique(Long processId, String paramCode, Long excludeId, String plantCode) {
        if (paramCode == null || paramCode.isBlank()) return;
        LambdaQueryWrapper<SpcParameter> dup = Wrappers.lambdaQuery(SpcParameter.class)
                .eq(SpcParameter::getProcessId, processId)
                .eq(SpcParameter::getParamCode, paramCode)
                .eq(SpcParameter::getPlantCode, plantCode)
                .eq(SpcParameter::getIsDeleted, 0);
        if (excludeId != null) {
            dup.ne(SpcParameter::getId, excludeId);
        }
        if (parameterMapper.selectCount(dup) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该工序下参数编码「" + paramCode + "」已存在，请使用不同编码");
        }
    }

    private SpcParameterResponse toResponse(SpcParameter e) {
        SpcParameterResponse r = new SpcParameterResponse();
        r.setId(e.getId());
        r.setProcessId(e.getProcessId());
        r.setParamCode(e.getParamCode());
        r.setParamName(e.getParamName());
        r.setParamType(e.getParamType());
        r.setUnit(e.getUnit());
        r.setUpperSpecLimit(e.getUpperSpecLimit());
        r.setLowerSpecLimit(e.getLowerSpecLimit());
        r.setTargetValue(e.getTargetValue());
        r.setSubgroupSize(e.getSubgroupSize());
        r.setChartType(e.getChartType());
        r.setIsActive(e.getIsActive());
        r.setDecimalPlaces(e.getDecimalPlaces() != null ? e.getDecimalPlaces() : 3);
        r.setIsCritical(e.getIsCritical());
        r.setChangeRemark(e.getChangeRemark());
        r.setPlantCode(e.getPlantCode());
        r.setPlantName(e.getPlantName());
        return r;
    }
}
