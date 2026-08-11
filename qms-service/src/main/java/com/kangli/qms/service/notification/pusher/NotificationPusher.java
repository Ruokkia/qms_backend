package com.kangli.qms.service.notification.pusher;

import com.kangli.qms.domain.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知实时推送组件 — 将 WebSocket 推送逻辑从 NotificationServiceImpl 中独立出来。
 * <p>当前使用内存 SimpleBroker 单实例推送；多实例部署时需升级为外部 Broker（RabbitMQ / Redis）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPusher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 实时推送单个通知给指定用户。
     *
     * @param notification 已入库的通知实体
     */
    public void push(Notification notification) {
        if (notification == null || notification.getUserId() == null) {
            return;
        }
        try {
            String destination = "/queue/notification/" + notification.getUserId();
            messagingTemplate.convertAndSend(destination, notification);
            log.debug("实时推送通知：userId={}, notificationId={}", notification.getUserId(), notification.getId());
        } catch (Exception e) {
            // 推送失败不阻塞业务
            log.warn("WebSocket 推送通知失败：userId={}, notificationId={}, error={}",
                    notification.getUserId(), notification.getId(), e.getMessage());
        }
    }

    /**
     * 推送未读计数变更（通知前端刷新铃铛数字）。
     */
    public void pushUnreadCount(Long userId, long unreadCount) {
        if (userId == null) {
            return;
        }
        try {
            String destination = "/queue/notification/" + userId + "/unread";
            messagingTemplate.convertAndSend(destination, unreadCount);
        } catch (Exception e) {
            log.warn("WebSocket 推送未读计数失败：userId={}, error={}", userId, e.getMessage());
        }
    }
}
