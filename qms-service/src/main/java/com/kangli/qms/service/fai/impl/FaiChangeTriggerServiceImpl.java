package com.kangli.qms.service.fai.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.fai.dto.CreateChangeTriggerRequest;
import com.kangli.qms.service.fai.dto.FaiChangeTriggerQuery;
import com.kangli.qms.service.fai.dto.FaiChangeTriggerResponse;
import com.kangli.qms.domain.fai.entity.FaiChangeTrigger;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.mapper.FaiChangeTriggerMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.fai.FaiChangeTriggerService;
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
    private final AuditLogService auditLogService;

    public FaiChangeTriggerServiceImpl(FaiChangeTriggerMapper changeTriggerMapper,
                                       FaiInspectionRecordMapper inspectionRecordMapper,
                                       AuditLogService auditLogService) {
        this.changeTriggerMapper = changeTriggerMapper;
        this.inspectionRecordMapper = inspectionRecordMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public FaiChangeTriggerResponse create(CreateChangeTriggerRequest request, LoginUser loginUser) {
        FaiChangeTrigger entity = new FaiChangeTrigger();
        BeanUtils.copyProperties(request, entity);
        // 冗余兼容列同步：旧报表按 materialCode/materialName 读取
        if (!StringUtils.hasText(entity.getMaterialCode())) {
            entity.setMaterialCode(entity.getItemCode());
        }
        if (!StringUtils.hasText(entity.getMaterialName())) {
            entity.setMaterialName(entity.getItemName());
        }
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
        if (StringUtils.hasText(query.getItemType())) {
            wrapper.eq(FaiChangeTrigger::getItemType, query.getItemType());
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

    @Override
    public void voidTrigger(Long id, String reason, LoginUser loginUser) {
        FaiChangeTrigger entity = changeTriggerMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变更触发记录不存在");
        }

        // 厂区隔离：仅允许操作当前用户所属厂区的记录
        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(entity.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他厂区的记录");
        }

        // 作废原因不能为空
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "作废原因不能为空");
        }

        // 场景2：已建单（已关联检验单）的记录完全不允许作废
        if (hasInspection(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已建单的变更触发记录不可作废，请通过「新建更正单」走更正流程");
        }

        // 已作废不重复作废
        if ("已作废".equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该记录已作废，无需重复操作");
        }

        // 保存变更前快照用于审计
        String beforeSnapshot = JSONUtil.toJsonStr(toResponse(entity, false));

        entity.setStatus("已作废");
        entity.setVoidReason(reason);
        entity.setUpdatedBy(loginUser.getRealName());

        // 乐观锁 updateById，并发时会抛 OptimisticLockException
        changeTriggerMapper.updateById(entity);

        // 记录审计日志
        auditLogService.record("fai_change_trigger", id, "VOID",
                beforeSnapshot, JSONUtil.toJsonStr(toResponse(entity, false)), reason);

        log.info("变更触发 id={} 已作废, 操作人={}, 原因={}", id, loginUser.getRealName(), reason);
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
