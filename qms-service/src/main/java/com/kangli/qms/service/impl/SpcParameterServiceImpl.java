package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.dto.SpcParameterRequest;
import com.kangli.qms.dto.SpcParameterResponse;
import com.kangli.qms.entity.SpcParameter;
import com.kangli.qms.mapper.SpcParameterMapper;
import com.kangli.qms.service.SpcParameterService;
import com.kangli.qms.service.FaiInspectionService;
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

    public SpcParameterServiceImpl(SpcParameterMapper parameterMapper, FaiInspectionService faiInspectionService) {
        this.parameterMapper = parameterMapper;
        this.faiInspectionService = faiInspectionService;
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
        parameterMapper.deleteById(id);
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
