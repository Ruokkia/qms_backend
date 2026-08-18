package com.kangli.qms.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.qualitysystem.entity.*;
import com.kangli.qms.domain.qualitysystem.mapper.*;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 每天推送体系管理待办、逾期和法定报告期限提醒。 */
@Slf4j @Component @RequiredArgsConstructor
public class QualitySystemReminderScheduler {
    private final InternalAuditPlanMapper auditMapper; private final AuditNonconformityMapper ncMapper;
    private final AdverseEventMapper eventMapper; private final CustomerFeedbackMapper feedbackMapper;
    private final QualityObjectiveMapper objectiveMapper;
    private final SysUserMapper userMapper; private final NotificationService notificationService;

    @Scheduled(cron = "0 20 9 * * ?")
    public void pushReminders() {
        LocalDate limit = LocalDate.now().plusDays(7);
        List<QualityObjective> objectives = objectiveMapper.selectList(new LambdaQueryWrapper<QualityObjective>().isNotNull(QualityObjective::getActualValue).isNotNull(QualityObjective::getTargetValue));
        objectives.stream().filter(x -> x.getActualValue().compareTo(x.getTargetValue().multiply(new java.math.BigDecimal("0.9"))) < 0)
                .forEach(x -> notifyPlant(x.getPlantCode(), "质量目标未达标提醒", "QUALITY_OBJECTIVE", x.getId(), "质量目标“" + x.getObjectiveName() + "”当前值 " + x.getActualValue() + "，低于目标值 " + x.getTargetValue() + "。"));
        List<InternalAuditPlan> audits = auditMapper.selectList(new LambdaQueryWrapper<InternalAuditPlan>()
                .le(InternalAuditPlan::getPlannedDate, limit).notIn(InternalAuditPlan::getStatus, "已完成", "取消"));
        audits.forEach(x -> notifyPlant(x.getPlantCode(), "内审计划到期提醒", "QUALITY_AUDIT", x.getId(), "内审计划日期为 " + x.getPlannedDate() + "，范围：" + x.getAuditScope()));
        List<AuditNonconformity> ncs = ncMapper.selectList(new LambdaQueryWrapper<AuditNonconformity>()
                .le(AuditNonconformity::getDueDate, limit).ne(AuditNonconformity::getStatus, "已关闭"));
        ncs.forEach(x -> notifyPlant(x.getPlantCode(), "不符合项整改提醒", "QUALITY_NC", x.getId(), "不符合项整改期限为 " + x.getDueDate() + "：" + x.getDescription()));
        List<CustomerFeedback> feedback = feedbackMapper.selectList(new LambdaQueryWrapper<CustomerFeedback>()
                .le(CustomerFeedback::getDueDate, limit).ne(CustomerFeedback::getStatus, "已关闭"));
        feedback.forEach(x -> notifyPlant(x.getPlantCode(), "顾客反馈处理提醒", "QUALITY_FEEDBACK", x.getId(), "顾客反馈处理期限为 " + x.getDueDate() + "：" + x.getContent()));
        List<AdverseEvent> events = eventMapper.selectList(new LambdaQueryWrapper<AdverseEvent>()
                .le(AdverseEvent::getReportDeadline, limit).isNull(AdverseEvent::getReportDate));
        events.forEach(x -> notifyPlant(x.getPlantCode(), "不良事件上报提醒", "QUALITY_EVENT", x.getId(), "不良事件上报期限为 " + x.getReportDeadline() + "：" + x.getEventDescription()));
        log.info("体系提醒推送完成，目标 {}，内审 {}，不符合项 {}，顾客反馈 {}，不良事件 {}", objectives.size(), audits.size(), ncs.size(), feedback.size(), events.size());
    }

    private void notifyPlant(String plant, String title, String type, Long id, String content) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPlantCode, plant).eq(SysUser::getStatus, (short) 1));
        for (SysUser user : users) {
            NotificationCreateDTO dto = new NotificationCreateDTO(); dto.setUserId(user.getId()); dto.setType(title); dto.setTitle(title);
            dto.setContent(content); dto.setLevel("警告"); dto.setBusinessType(type); dto.setBusinessId(id); dto.setPlantCode(plant); dto.setCreatedBy("系统定时任务");
            notificationService.createNotification(dto);
        }
    }
}
