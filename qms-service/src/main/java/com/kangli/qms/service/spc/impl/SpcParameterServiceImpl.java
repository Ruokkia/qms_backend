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
import com.kangli.qms.service.spc.SpcParameterService;
import com.kangli.qms.service.fai.FaiInspectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public SpcParameterServiceImpl(SpcParameterMapper parameterMapper,
                                   FaiInspectionService faiInspectionService,
                                   SpcSubgroupMapper subgroupMapper,
                                   SpcSampleMapper sampleMapper,
                                   SpcControlLimitMapper controlLimitMapper,
                                   SpcCapabilityMapper capabilityMapper) {
        this.parameterMapper = parameterMapper;
        this.faiInspectionService = faiInspectionService;
        this.subgroupMapper = subgroupMapper;
        this.sampleMapper = sampleMapper;
        this.controlLimitMapper = controlLimitMapper;
        this.capabilityMapper = capabilityMapper;
    }

    @Override
    public List<SpcParameterResponse> list(Long processId, String plantCode) {
        LambdaQueryWrapper<SpcParameter> qw = Wrappers.lambdaQuery(SpcParameter.class)
                .eq(SpcParameter::getPlantCode, plantCode)
                .eq(SpcParameter::getIsDeleted, 0)
                .eq(processId != null, SpcParameter::getProcessId, processId)
                .orderByAsc(SpcParameter::getId);
        return parameterMapper.selectList(qw).stream().map(this::toResponse).collect(Collectors.toList());
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
        SpcParameter e = new SpcParameter();
        e.setProcessId(request.getProcessId());
        e.setParamCode(request.getParamCode());
        e.setParamName(request.getParamName());
        e.setParamType(request.getParamType());
        e.setUnit(request.getUnit());
        e.setUpperSpecLimit(request.getUpperSpecLimit());
        e.setLowerSpecLimit(request.getLowerSpecLimit());
        e.setTargetValue(request.getTargetValue());
        e.setSubgroupSize(request.getSubgroupSize() == null ? 5 : request.getSubgroupSize());
        e.setChartType(request.getChartType() == null ? "Xbar-R" : request.getChartType());
        e.setIsActive(request.getIsActive() == null ? "是" : request.getIsActive());
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
        current.setProcessId(request.getProcessId());
        current.setParamCode(request.getParamCode());
        current.setParamName(request.getParamName());
        current.setParamType(request.getParamType());
        current.setUnit(request.getUnit());
        current.setUpperSpecLimit(request.getUpperSpecLimit());
        current.setLowerSpecLimit(request.getLowerSpecLimit());
        current.setTargetValue(request.getTargetValue());
        current.setSubgroupSize(request.getSubgroupSize() == null ? current.getSubgroupSize() : request.getSubgroupSize());
        current.setChartType(request.getChartType() == null ? current.getChartType() : request.getChartType());
        current.setIsActive(request.getIsActive() == null ? current.getIsActive() : request.getIsActive());
        current.setVersion(current.getVersion() + 1);
        current.setUpdatedBy(loginUser.getAccount());
        parameterMapper.updateById(current);
        faiInspectionService.syncUpdatedSpcParameter(current, loginUser);
        return toResponse(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SpcParameter current = parameterMapper.selectById(id);
        if (current == null || current.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        // 先清理该参数下的子表数据，避免子表残留孤节点
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
        r.setPlantCode(e.getPlantCode());
        r.setPlantName(e.getPlantName());
        return r;
    }
}
