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
        // 改进①：规格三值逻辑校验
        validateSpecLimits(request.getUpperSpecLimit(), request.getLowerSpecLimit(), request.getTargetValue());
        // 改进③：同工序下参数编码唯一性校验
        validateParamCodeUnique(request.getProcessId(), request.getParamCode(), null, loginUser.getPlantCode().name());

        SpcParameter e = new SpcParameter();
        e.setProcessId(request.getProcessId());
        e.setParamCode(request.getParamCode());
        e.setParamName(request.getParamName());
        e.setParamType(request.getParamType());
        e.setUnit(request.getUnit());
        e.setUpperSpecLimit(request.getUpperSpecLimit());
        e.setLowerSpecLimit(request.getLowerSpecLimit());
        e.setTargetValue(request.getTargetValue());
        Integer subgroupSize = request.getSubgroupSize() == null ? 5 : request.getSubgroupSize();
        validateSubgroupSize(subgroupSize);
        e.setSubgroupSize(subgroupSize);
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
        // 改进①：规格三值逻辑校验（仅当 request 传了对应字段时才校验）
        if (request.getUpperSpecLimit() != null || request.getLowerSpecLimit() != null || request.getTargetValue() != null) {
            validateSpecLimits(request.getUpperSpecLimit(), request.getLowerSpecLimit(), request.getTargetValue());
        }
        // 改进③：同工序下参数编码唯一性校验
        validateParamCodeUnique(request.getProcessId(), request.getParamCode(), id, current.getPlantCode());

        current.setProcessId(request.getProcessId());
        current.setParamCode(request.getParamCode());
        current.setParamName(request.getParamName());
        current.setParamType(request.getParamType());
        current.setUnit(request.getUnit());
        current.setUpperSpecLimit(request.getUpperSpecLimit());
        current.setLowerSpecLimit(request.getLowerSpecLimit());
        current.setTargetValue(request.getTargetValue());
        Integer subgroupSize = request.getSubgroupSize() == null ? current.getSubgroupSize() : request.getSubgroupSize();
        validateSubgroupSize(subgroupSize);
        current.setSubgroupSize(subgroupSize);
        current.setChartType(request.getChartType() == null ? current.getChartType() : request.getChartType());
        current.setIsActive(request.getIsActive() == null ? current.getIsActive() : request.getIsActive());
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

    /**
     * 校验子组大小 n。
     * <p>SPC 系数表 spc_coefficient 仅固化了 n=2~12 的标准系数，
     * 超出范围会导致控制限/能力指数计算时取不到系数（c4 为 NULL 或除零）。
     * 提前在参数创建/更新处拦截，避免脏数据进入后续计算。</p>
     *
     * @param n 子组大小
     */
    private void validateSubgroupSize(Integer n) {
        if (n == null || n < 2 || n > 12) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "子组大小 n 必须为 2~12 的整数");
        }
    }

    /**
     * 改进①：校验 USL / LSL / 目标值 逻辑一致性。
     * <p>USL 必须 > LSL，目标值必须落在 [LSL, USL] 区间内。
     * 仅当相关字段非 null 时才执行比较。</p>
     */
    private void validateSpecLimits(java.math.BigDecimal usl, java.math.BigDecimal lsl, java.math.BigDecimal target) {
        if (usl != null && lsl != null && usl.compareTo(lsl) <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "规格上限（USL）必须大于规格下限（LSL）");
        }
        if (target != null) {
            if (lsl != null && target.compareTo(lsl) < 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "目标值不能低于规格下限（LSL）");
            }
            if (usl != null && target.compareTo(usl) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "目标值不能高于规格上限（USL）");
            }
        }
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
        r.setPlantCode(e.getPlantCode());
        r.setPlantName(e.getPlantName());
        return r;
    }
}
