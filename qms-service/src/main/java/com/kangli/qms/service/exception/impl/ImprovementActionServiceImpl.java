package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.mapper.ImprovementActionMapper;
import com.kangli.qms.service.exception.ImprovementActionService;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import com.kangli.qms.service.notification.helper.NotificationTemplateHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 改善措施 Service — 新增/更新时自动通知责任人。
 */
@Slf4j
@Service
public class ImprovementActionServiceImpl extends ServiceImpl<ImprovementActionMapper, ImprovementAction>
        implements ImprovementActionService {

    private final ExceptionOrderMapper exceptionOrderMapper;
    private final NotificationService notificationService;
    private final NotificationConfigService notificationConfigService;

    public ImprovementActionServiceImpl(ImprovementActionMapper improvementActionMapper,
                                        ExceptionOrderMapper exceptionOrderMapper,
                                        NotificationService notificationService,
                                        NotificationConfigService notificationConfigService) {
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.notificationService = notificationService;
        this.notificationConfigService = notificationConfigService;
    }

    @Override
    @Transactional
    public boolean save(ImprovementAction entity) {
        // 已闭环异常单禁止追加改善措施，避免闭环后数据污染
        if (entity.getExceptionId() != null) {
            ExceptionOrder order = exceptionOrderMapper.selectById(entity.getExceptionId());
            if (order != null && "已闭环".equals(order.getStatus())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "异常单已闭环，禁止追加改善措施");
            }
        }
        boolean result = super.save(entity);
        if (result && entity.getOwnerId() != null) {
            notifyActionOwner(entity, entity.getOwnerId(), entity.getOwnerName());
        }
        return result;
    }

    @Override
    @Transactional
    public boolean updateById(ImprovementAction entity) {
        ImprovementAction old = getById(entity.getId());
        boolean result = super.updateById(entity);

        // 仅当责任人变更时才发送通知
        if (result && entity.getOwnerId() != null
                && !entity.getOwnerId().equals(old != null ? old.getOwnerId() : null)) {
            notifyActionOwner(entity, entity.getOwnerId(), entity.getOwnerName());
        }
        return result;
    }

    /**
     * 通知改善措施责任人（点对点）+ 按配置抄送角色。
     */
    private void notifyActionOwner(ImprovementAction action, Long ownerId, String ownerName) {
        try {
            LoginUser loginUser = LoginUserHolder.get();
            if (loginUser == null) {
                return;
            }

            ExceptionOrder order = null;
            if (action.getExceptionId() != null) {
                order = exceptionOrderMapper.selectById(action.getExceptionId());
            }
            String exceptionNo = order != null ? order.getExceptionNo() : "N/A";

            String scenarioCode = NotificationTypeEnum.ACTION_OWNER_ASSIGNED.getCode();
            String level = "提醒";

            // 1. 点对点通知被指派人
            NotificationCreateDTO n = NotificationTemplateHelper.forImprovementAction(
                    ownerId, action.getPlantCode(), exceptionNo, action.getExceptionId(),
                    action.getActionType(), action.getContent(),
                    action.getDueDate() != null ? action.getDueDate().toString() : "",
                    loginUser.getRealName());
            n.setType(scenarioCode);
            n.setLevel(level);
            notificationService.createNotification(n);

            // 2. 按配置抄送额外角色
            List<String> ccRoles = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (!ccRoles.isEmpty() && action.getPlantCode() != null) {
                List<Long> ccUserIds = notificationConfigService.listUserIdsByRoleCodes(ccRoles, action.getPlantCode());
                for (Long ccUserId : ccUserIds) {
                    if (ccUserId.equals(ownerId)) {
                        continue;
                    }
                    NotificationCreateDTO cc = NotificationTemplateHelper.forCc(n, ccUserId, ownerName);
                    notificationService.createNotification(cc);
                }
            }
        } catch (Exception e) {
            log.warn("发送改善措施指派通知失败：actionId={}", action.getId(), e);
        }
    }
}
