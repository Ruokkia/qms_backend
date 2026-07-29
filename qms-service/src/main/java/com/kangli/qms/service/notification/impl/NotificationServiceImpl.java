package com.kangli.qms.service.notification.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.domain.notification.entity.Notification;
import com.kangli.qms.domain.notification.mapper.NotificationMapper;
import com.kangli.qms.service.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知服务实现。
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Override
    public Notification createNotification(NotificationCreateDTO dto) {
        Notification notification = new Notification();
        BeanUtils.copyProperties(dto, notification);
        notification.setIsRead((short) 0);
        if (notification.getLevel() == null) {
            notification.setLevel("提醒");
        }
        notification.setPlantName(
                "SZ".equals(dto.getPlantCode()) ? "深圳" : ("MZ".equals(dto.getPlantCode()) ? "梅州" : ""));
        baseMapper.insert(notification);
        log.info("通知已创建：userId={}, type={}, businessId={}", dto.getUserId(), dto.getType(), dto.getBusinessId());
        return notification;
    }

    @Override
    public void markAllRead(Long userId, LocalDateTime readAt) {
        baseMapper.markAllRead(userId, readAt.toString());
    }
}

