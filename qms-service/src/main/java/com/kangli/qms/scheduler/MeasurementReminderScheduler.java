package com.kangli.qms.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.measurement.entity.InstrumentLendingRecord;
import com.kangli.qms.domain.measurement.entity.InstrumentCalibrationTask;
import com.kangli.qms.domain.measurement.entity.MeasuringInstrument;
import com.kangli.qms.domain.measurement.mapper.InstrumentLendingRecordMapper;
import com.kangli.qms.domain.measurement.mapper.InstrumentCalibrationTaskMapper;
import com.kangli.qms.domain.measurement.mapper.MeasuringInstrumentMapper;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 每天推送计量器具校准到期和借用逾期提醒。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeasurementReminderScheduler {
    private final MeasuringInstrumentMapper instrumentMapper;
    private final InstrumentLendingRecordMapper lendingMapper;
    private final InstrumentCalibrationTaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 10 9 * * ?")
    public void pushReminders() {
        LocalDate today = LocalDate.now();
        List<MeasuringInstrument> due = instrumentMapper.selectList(new LambdaQueryWrapper<MeasuringInstrument>()
                .le(MeasuringInstrument::getNextCalibrationDate, today.plusDays(30))
                .ne(MeasuringInstrument::getStatus, "已报废"));
        for (MeasuringInstrument instrument : due) {
            Long openTasks = taskMapper.selectCount(new LambdaQueryWrapper<InstrumentCalibrationTask>()
                    .eq(InstrumentCalibrationTask::getInstrumentId, instrument.getId()).in(InstrumentCalibrationTask::getStatus, "待执行", "逾期"));
            if (openTasks == 0) {
                InstrumentCalibrationTask task = new InstrumentCalibrationTask(); task.setInstrumentId(instrument.getId()); task.setPlanDate(instrument.getNextCalibrationDate());
                task.setAgency(instrument.getCalibrationAgency()); task.setResponsibleName(instrument.getKeeperName()); task.setStatus(instrument.getNextCalibrationDate()!=null&&instrument.getNextCalibrationDate().isBefore(today)?"逾期":"待执行");
                task.setPlantCode(instrument.getPlantCode()); task.setPlantName(instrument.getPlantName()); task.setCreatedBy("系统定时任务"); task.setUpdatedBy("系统定时任务"); taskMapper.insert(task);
            }
            notifyUsers(instrument.getPlantCode(), instrument.getKeeperName(), "计量校准提醒", "警告",
                    "MEASUREMENT_CALIBRATION", instrument.getId(), "器具 " + instrument.getInstrumentCode() + "（" + instrument.getInstrumentName()
                            + "）校准有效期至 " + instrument.getNextCalibrationDate() + "，请及时安排校准。");
            if (instrument.getNextCalibrationDate() != null && instrument.getNextCalibrationDate().isBefore(today)
                    && !"超期".equals(instrument.getStatus())) {
                instrument.setStatus("超期");
                instrumentMapper.updateById(instrument);
            }
        }
        List<InstrumentLendingRecord> overdue = lendingMapper.selectList(new LambdaQueryWrapper<InstrumentLendingRecord>()
                .isNull(InstrumentLendingRecord::getReturnedAt)
                .lt(InstrumentLendingRecord::getExpectedReturnDate, today));
        for (InstrumentLendingRecord lending : overdue) {
            notifyUsers(lending.getPlantCode(), lending.getBorrowerName(), "计量借用逾期", "警告",
                    "MEASUREMENT_LENDING", lending.getId(), "计量器具借用已逾期，预计归还日期 " + lending.getExpectedReturnDate() + "，请尽快归还。");
        }
        log.info("计量提醒推送完成，校准到期 {} 条，借用逾期 {} 条", due.size(), overdue.size());
    }

    private void notifyUsers(String plant, String realName, String title, String level, String type, Long id, String content) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlantCode, plant).eq(SysUser::getStatus, (short) 1)
                .eq(realName != null && !realName.trim().isEmpty(), SysUser::getRealName, realName));
        for (SysUser user : users) {
            NotificationCreateDTO dto = new NotificationCreateDTO();
            dto.setUserId(user.getId()); dto.setType(title); dto.setTitle(title); dto.setContent(content); dto.setLevel(level);
            dto.setBusinessType(type); dto.setBusinessId(id); dto.setPlantCode(plant); dto.setCreatedBy("系统定时任务");
            notificationService.createNotification(dto);
        }
    }
}
