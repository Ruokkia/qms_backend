package com.kangli.qms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.dto.NotificationCreateDTO;
import com.kangli.qms.entity.Notification;

import java.time.LocalDateTime;

/**
 * 通知服务。
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 创建通知（内部调用）。
     */
    Notification createNotification(NotificationCreateDTO dto);

    /**
     * 将当前用户的全部未读通知标记为已读。
     */
    void markAllRead(Long userId, LocalDateTime readAt);
}

