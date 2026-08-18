package com.kangli.qms.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.tooling.entity.ToolingMaintenanceRecord;
import com.kangli.qms.domain.tooling.mapper.ToolingMaintenanceRecordMapper;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 每天早上自动推送到期/逾期工装保养站内通知。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolingMaintenanceReminderScheduler {
    private final ToolingMaintenanceRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void pushDueMaintenanceReminders() {
        List<ToolingMaintenanceRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ToolingMaintenanceRecord>()
                .eq(ToolingMaintenanceRecord::getRecordType, "保养")
                .le(ToolingMaintenanceRecord::getDueDate, LocalDate.now())
                .in(ToolingMaintenanceRecord::getStatus, "待执行", "逾期"));
        for (ToolingMaintenanceRecord record : records) {
            List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPlantCode, record.getPlantCode()).eq(SysUser::getStatus, (short) 1)
                    .eq(record.getResponsibleName() != null && !record.getResponsibleName().trim().isEmpty(), SysUser::getRealName, record.getResponsibleName()));
            for (SysUser user : users) {
                NotificationCreateDTO dto = new NotificationCreateDTO();
                dto.setUserId(user.getId()); dto.setType("工装保养提醒"); dto.setLevel("警告"); dto.setBusinessType("TOOLING_MAINTENANCE"); dto.setBusinessId(record.getId()); dto.setPlantCode(record.getPlantCode()); dto.setCreatedBy("系统定时任务");
                dto.setTitle("工装保养到期提醒"); dto.setContent("工装保养计划到期：" + record.getPlanCycle() + "，到期日 " + record.getDueDate() + "，请及时执行并登记结果。");
                notificationService.createNotification(dto);
            }
            if (!"逾期".equals(record.getStatus())) { record.setStatus("逾期"); recordMapper.updateById(record); }
        }
        log.info("工装保养提醒推送完成，处理 {} 条到期记录", records.size());
    }
}
