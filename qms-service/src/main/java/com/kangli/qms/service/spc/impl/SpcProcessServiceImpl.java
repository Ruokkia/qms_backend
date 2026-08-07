package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcProcessRequest;
import com.kangli.qms.service.spc.dto.SpcProcessResponse;
import com.kangli.qms.domain.spc.entity.SpcCapability;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import com.kangli.qms.domain.spc.mapper.SpcControlLimitMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.spc.SpcProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M4 SPC 工序管理实现。
 */
@Slf4j
@Service
public class SpcProcessServiceImpl implements SpcProcessService {

    private final SpcProcessMapper processMapper;

    private final SpcParameterMapper parameterMapper;

    private final SpcSubgroupMapper subgroupMapper;

    private final SpcSampleMapper sampleMapper;

    private final SpcControlLimitMapper controlLimitMapper;

    private final SpcCapabilityMapper capabilityMapper;

    public SpcProcessServiceImpl(SpcProcessMapper processMapper,
                                 SpcParameterMapper parameterMapper,
                                 SpcSubgroupMapper subgroupMapper,
                                 SpcSampleMapper sampleMapper,
                                 SpcControlLimitMapper controlLimitMapper,
                                 SpcCapabilityMapper capabilityMapper) {
        this.processMapper = processMapper;
        this.parameterMapper = parameterMapper;
        this.subgroupMapper = subgroupMapper;
        this.sampleMapper = sampleMapper;
        this.controlLimitMapper = controlLimitMapper;
        this.capabilityMapper = capabilityMapper;
    }

    @Override
    public List<SpcProcessResponse> list(String plantCode) {
        LambdaQueryWrapper<SpcProcess> qw = Wrappers.lambdaQuery(SpcProcess.class)
                .eq(SpcProcess::getPlantCode, plantCode)
                .eq(SpcProcess::getIsDeleted, 0)
                // 种子数据(sort_order>0)保持原顺序置顶，用户新增数据(sort_order=0)按创建时间正序排在种子之后
                .last("ORDER BY CASE WHEN sort_order > 0 THEN 0 ELSE 1 END ASC, "
                        + "CASE WHEN sort_order > 0 THEN sort_order END ASC, created_at ASC");
        return processMapper.selectList(qw).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcProcessResponse create(SpcProcessRequest request, LoginUser loginUser) {
        validateProcess(request);
        String plantCode = loginUser.getPlantCode().name();
        // 同厂区工序编码唯一校验，避免重复工序污染参数/控制图/追溯
        if (existsByPlantAndCode(plantCode, request.getProcessCode(), null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该厂区已存在相同工序编码：" + request.getProcessCode());
        }
        SpcProcess entity = new SpcProcess();
        entity.setProcessCode(request.getProcessCode());
        entity.setProcessName(request.getProcessName());
        entity.setDescription(request.getDescription());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setPlantCode(plantCode);
        entity.setPlantName(loginUser.getPlantCode().getChineseName());
        entity.setCreatedBy(loginUser.getAccount());
        entity.setUpdatedBy(loginUser.getAccount());
        processMapper.insert(entity);
        return toResponse(entity);
    }

    /**
     * 校验工序编码合法性（新增与更新共用）：
     * 仅约束编码为 2~5 位大写字母（与前端正则一致）。
     * 工序名称放开为可自由输入（前端模糊搜索 + 允许新建），后端不再做白名单兜底。
     */
    private void validateProcess(SpcProcessRequest request) {
        String code = request.getProcessCode();
        if (code == null || !code.matches("^[A-Z]{2,5}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "工序编码必须为 2~5 位大写字母（如 ASM / WDG / INS）");
        }
        if (request.getProcessName() == null || request.getProcessName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工序名称不能为空");
        }
    }

    /**
     * 判断同厂区是否已存在指定工序编码（非删除记录）。
     *
     * @param plantCode 厂区编码
     * @param processCode 工序编码
     * @param excludeId 排除的工序 id（更新自身时传入，避免与自身冲突）
     */
    private boolean existsByPlantAndCode(String plantCode, String processCode, Long excludeId) {
        LambdaQueryWrapper<SpcProcess> qw = Wrappers.lambdaQuery(SpcProcess.class)
                .eq(SpcProcess::getPlantCode, plantCode)
                .eq(SpcProcess::getProcessCode, processCode)
                .eq(SpcProcess::getIsDeleted, 0);
        if (excludeId != null) {
            qw.ne(SpcProcess::getId, excludeId);
        }
        return processMapper.selectCount(qw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcProcessResponse update(Long id, SpcProcessRequest request, LoginUser loginUser) {
        SpcProcess current = processMapper.selectById(id);
        if (current == null || current.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工序不存在");
        }
        if (request.getVersion() != null && !request.getVersion().equals(current.getVersion())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "记录已被他人修改，请刷新后重试");
        }
        validateProcess(request);
        if (existsByPlantAndCode(current.getPlantCode(), request.getProcessCode(), id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该厂区已存在相同工序编码：" + request.getProcessCode());
        }
        current.setProcessCode(request.getProcessCode());
        current.setProcessName(request.getProcessName());
        current.setDescription(request.getDescription());
        current.setSortOrder(request.getSortOrder() == null ? current.getSortOrder() : request.getSortOrder());
        // 不再手动 setVersion：@Version 拦截器会以当前版本作 WHERE 并自动 +1，避免误判导致更新 0 行
        current.setUpdatedBy(loginUser.getAccount());
        int rows = processMapper.updateById(current);
        if (rows == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "记录已被他人修改，请刷新后重试");
        }
        // refetch 同步 @Version：updateById 后 DB version 已 +1，内存对象 version 已过时
        current = processMapper.selectById(current.getId());
        return toResponse(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SpcProcess current = processMapper.selectById(id);
        if (current == null || current.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工序不存在");
        }
        // 收集该工序下所有未删除的关键参数，先逐个清理其子树，避免子表残留孤节点
        List<Long> paramIds = parameterMapper.selectList(
                        Wrappers.lambdaQuery(SpcParameter.class)
                                .eq(SpcParameter::getProcessId, id)
                                .eq(SpcParameter::getIsDeleted, 0)
                                .select(SpcParameter::getId))
                .stream().map(SpcParameter::getId).collect(Collectors.toList());

        for (Long paramId : paramIds) {
            cascadeDeleteByParam(paramId);
        }
        // 级联逻辑删除关键参数，避免参数成为孤节点
        parameterMapper.delete(Wrappers.lambdaQuery(SpcParameter.class)
                .eq(SpcParameter::getProcessId, id));
        // 工序本身逻辑删除
        processMapper.deleteById(id);
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

    private SpcProcessResponse toResponse(SpcProcess e) {
        SpcProcessResponse r = new SpcProcessResponse();
        r.setId(e.getId());
        r.setProcessCode(e.getProcessCode());
        r.setProcessName(e.getProcessName());
        r.setDescription(e.getDescription());
        r.setSortOrder(e.getSortOrder());
        r.setPlantCode(e.getPlantCode());
        r.setPlantName(e.getPlantName());
        return r;
    }
}
