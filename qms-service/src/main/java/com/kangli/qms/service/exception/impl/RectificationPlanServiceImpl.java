package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.RectificationPlan;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.mapper.RectificationPlanMapper;
import com.kangli.qms.service.exception.RectificationPlanService;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 整改计划 Service — 新增/更新时自动通知负责人。
 */
@Slf4j
@Service
public class RectificationPlanServiceImpl extends ServiceImpl<RectificationPlanMapper, RectificationPlan>
        implements RectificationPlanService {

    private final ExceptionOrderMapper exceptionOrderMapper;
    private final NotificationService notificationService;
    private final NotificationConfigService notificationConfigService;

    public RectificationPlanServiceImpl(RectificationPlanMapper rectificationPlanMapper,
                                        ExceptionOrderMapper exceptionOrderMapper,
                                        NotificationService notificationService,
                                        NotificationConfigService notificationConfigService) {
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.notificationService = notificationService;
        this.notificationConfigService = notificationConfigService;
    }

    @Override
    @Transactional
    public boolean save(RectificationPlan entity) {
        boolean result = super.save(entity);
        if (result && entity.getOwnerId() != null) {
            notifyPlanOwner(entity, entity.getOwnerId(), entity.getOwnerName());
        }
        return result;
    }

    @Override
    @Transactional
    public boolean updateById(RectificationPlan entity) {
        RectificationPlan old = getById(entity.getId());
        boolean result = super.updateById(entity);

        // 仅当责任人变更时才发送通知
        if (result && entity.getOwnerId() != null
                && !entity.getOwnerId().equals(old != null ? old.getOwnerId() : null)) {
            notifyPlanOwner(entity, entity.getOwnerId(), entity.getOwnerName());
        }
        return result;
    }

    /**
     * 通知整改计划负责人（点对点）+ 按配置抄送角色。
     */
    private void notifyPlanOwner(RectificationPlan plan, Long ownerId, String ownerName) {
        try {
            LoginUser loginUser = LoginUserHolder.get();
            if (loginUser == null) {
                return;
            }

            ExceptionOrder order = null;
            if (plan.getExceptionId() != null) {
                order = exceptionOrderMapper.selectById(plan.getExceptionId());
            }
            String exceptionNo = order != null ? order.getExceptionNo() : "N/A";

            String scenarioCode = NotificationTypeEnum.PLAN_OWNER_ASSIGNED.getCode();
            String level = "提醒";
            String title = "整改计划指派：" + exceptionNo;
            String content = "您被指定为整改计划负责人（计划：" + plan.getPlanName()
                    + "；目标：" + plan.getObjective()
                    + "；截止日期：" + plan.getPlanEndDate() + "），请及时推进。";

            // 1. 点对点通知被指派人
            NotificationCreateDTO n = new NotificationCreateDTO();
            n.setUserId(ownerId);
            n.setType(scenarioCode);
            n.setLevel(level);
            n.setTitle(title);
            n.setContent(content);
            n.setBusinessType("EXCEPTION_ORDER");
            n.setBusinessId(plan.getExceptionId());
            n.setPlantCode(plan.getPlantCode());
            n.setCreatedBy(loginUser.getRealName());
            notificationService.createNotification(n);

            // 2. 按配置抄送额外角色
            List<String> ccRoles = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (!ccRoles.isEmpty() && plan.getPlantCode() != null) {
                List<Long> ccUserIds = notificationConfigService.listUserIdsByRoleCodes(ccRoles, plan.getPlantCode());
                for (Long ccUserId : ccUserIds) {
                    if (ccUserId.equals(ownerId)) {
                        continue;
                    }
                    NotificationCreateDTO cc = new NotificationCreateDTO();
                    cc.setUserId(ccUserId);
                    cc.setType(scenarioCode);
                    cc.setLevel(level);
                    cc.setTitle("【抄送】" + title);
                    cc.setContent(content + "（通知对象：" + ownerName + "）");
                    cc.setBusinessType("EXCEPTION_ORDER");
                    cc.setBusinessId(plan.getExceptionId());
                    cc.setPlantCode(plan.getPlantCode());
                    cc.setCreatedBy(loginUser.getRealName());
                    notificationService.createNotification(cc);
                }
            }
        } catch (Exception e) {
            log.warn("发送整改计划指派通知失败：planId={}", plan.getId(), e);
        }
    }
}
