package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.dto.SpcProcessRequest;
import com.kangli.qms.dto.SpcProcessResponse;
import com.kangli.qms.entity.SpcProcess;
import com.kangli.qms.mapper.SpcProcessMapper;
import com.kangli.qms.service.SpcProcessService;
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

    public SpcProcessServiceImpl(SpcProcessMapper processMapper) {
        this.processMapper = processMapper;
    }

    @Override
    public List<SpcProcessResponse> list(String plantCode) {
        LambdaQueryWrapper<SpcProcess> qw = Wrappers.lambdaQuery(SpcProcess.class)
                .eq(SpcProcess::getPlantCode, plantCode)
                .eq(SpcProcess::getIsDeleted, 0)
                .orderByAsc(SpcProcess::getSortOrder);
        return processMapper.selectList(qw).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcProcessResponse create(SpcProcessRequest request, LoginUser loginUser) {
        SpcProcess entity = new SpcProcess();
        entity.setProcessCode(request.getProcessCode());
        entity.setProcessName(request.getProcessName());
        entity.setDescription(request.getDescription());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setPlantCode(loginUser.getPlantCode().name());
        entity.setPlantName(loginUser.getPlantCode().getChineseName());
        entity.setCreatedBy(loginUser.getAccount());
        entity.setUpdatedBy(loginUser.getAccount());
        processMapper.insert(entity);
        return toResponse(entity);
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
        current.setProcessCode(request.getProcessCode());
        current.setProcessName(request.getProcessName());
        current.setDescription(request.getDescription());
        current.setSortOrder(request.getSortOrder() == null ? current.getSortOrder() : request.getSortOrder());
        current.setVersion(current.getVersion() + 1);
        current.setUpdatedBy(loginUser.getAccount());
        processMapper.updateById(current);
        return toResponse(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SpcProcess current = processMapper.selectById(id);
        if (current == null || current.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工序不存在");
        }
        processMapper.deleteById(id);
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
