package com.kangli.qms.service.notification.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.domain.notification.entity.Notification;
import com.kangli.qms.domain.notification.mapper.NotificationMapper;
import com.kangli.qms.service.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知服务实现。
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /**
     * 实时推送模板。required=false：在缺少 WebSocket 消息代理的测试上下文里为 null，
     * 此时仅落库、不推送，不影响核心业务。
     */
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

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
        pushRealTime(dto.getUserId(), notification);
        return notification;
    }

    /**
     * 通过 STOMP 将通知实时推送给目标用户（D10）。
     * 失败仅记日志，不影响通知落库与返回。
     */
    private void pushRealTime(Long userId, Notification notification) {
        if (messagingTemplate == null || userId == null) {
            return;
        }
        try {
            // 路由到 /user/{userId}/queue/notifications，仅该用户在线会话可收
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId), "/queue/notifications", notification);
        } catch (Exception e) {
            log.warn("[通知实时推送] 推送失败 userId={}：{}", userId, e.getMessage());
        }
    }

    @Override
    public void markAllRead(Long userId, LocalDateTime readAt) {
        baseMapper.markAllRead(userId, readAt.toString());
    }
}

