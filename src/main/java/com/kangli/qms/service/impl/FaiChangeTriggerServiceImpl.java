package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.dto.CreateChangeTriggerRequest;
import com.kangli.qms.dto.FaiChangeTriggerQuery;
import com.kangli.qms.dto.FaiChangeTriggerResponse;
import com.kangli.qms.entity.FaiChangeTrigger;
import com.kangli.qms.entity.FaiInspectionRecord;
import com.kangli.qms.mapper.FaiChangeTriggerMapper;
import com.kangli.qms.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.service.FaiChangeTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M3 首件检验变更触发 Service 实现。
 */
@Slf4j
@Service
public class FaiChangeTriggerServiceImpl implements FaiChangeTriggerService {

    private final FaiChangeTriggerMapper changeTriggerMapper;
    private final FaiInspectionRecordMapper inspectionRecordMapper;

    public FaiChangeTriggerServiceImpl(FaiChangeTriggerMapper changeTriggerMapper,
                                       FaiInspectionRecordMapper inspectionRecordMapper) {
        this.changeTriggerMapper = changeTriggerMapper;
        this.inspectionRecordMapper = inspectionRecordMapper;
    }

    @Override
    public FaiChangeTriggerResponse create(CreateChangeTriggerRequest request, LoginUser loginUser) {
        FaiChangeTrigger entity = new FaiChangeTrigger();
        BeanUtils.copyProperties(request, entity);
        entity.setStatus("待检验");
        entity.setPlantCode(loginUser.getPlantCode().name());
        entity.setPlantName(loginUser.getPlantCode().getChineseName());
        entity.setCreatedBy(loginUser.getRealName());
        entity.setUpdatedBy(loginUser.getRealName());
        changeTriggerMapper.insert(entity);
        return toResponse(entity, false);
    }

    @Override
    public PageResult<FaiChangeTriggerResponse> page(FaiChangeTriggerQuery query, String plantCode) {
        Page<FaiChangeTrigger> pageObj = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<FaiChangeTrigger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiChangeTrigger::getPlantCode, plantCode);
        if (StringUtils.hasText(query.getTriggerType())) {
            wrapper.eq(FaiChangeTrigger::getTriggerType, query.getTriggerType());
        }
        if (StringUtils.hasText(query.getMaterialCode())) {
            wrapper.like(FaiChangeTrigger::getMaterialCode, query.getMaterialCode());
        }
        if (StringUtils.hasText(query.getBatchNo())) {
            wrapper.eq(FaiChangeTrigger::getBatchNo, query.getBatchNo());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(FaiChangeTrigger::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(FaiChangeTrigger::getCreatedAt);
        Page<FaiChangeTrigger> page = changeTriggerMapper.selectPage(pageObj, wrapper);
        List<FaiChangeTriggerResponse> list = page.getRecords().stream()
                .map(e -> toResponse(e, hasInspection(e.getId())))
                .collect(Collectors.toList());
        return PageResult.of(new Page<FaiChangeTriggerResponse>() {{
            setRecords(list);
            setTotal(page.getTotal());
            setCurrent(page.getCurrent());
            setSize(page.getSize());
        }});
    }

    @Override
    public FaiChangeTriggerResponse detail(Long id) {
        FaiChangeTrigger entity = changeTriggerMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变更触发记录不存在");
        }
        return toResponse(entity, hasInspection(id));
    }

    private boolean hasInspection(Long changeTriggerId) {
        Long count = inspectionRecordMapper.selectCount(
                new LambdaQueryWrapper<FaiInspectionRecord>()
                        .eq(FaiInspectionRecord::getChangeTriggerId, changeTriggerId));
        return count != null && count > 0;
    }

    private FaiChangeTriggerResponse toResponse(FaiChangeTrigger entity, boolean hasInspection) {
        FaiChangeTriggerResponse resp = new FaiChangeTriggerResponse();
        BeanUtils.copyProperties(entity, resp);
        resp.setHasInspection(hasInspection);
        return resp;
    }
}
