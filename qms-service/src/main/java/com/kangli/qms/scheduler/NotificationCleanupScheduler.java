package com.kangli.qms.scheduler;

import com.kangli.qms.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知清理定时任务 — 每天凌晨清理指定天数前的已读通知（逻辑删除）。
 * <p>可通过 {@code notification.cleanup.enabled=false} 关闭。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    /** 保留天数，默认 90 天 */
    @Value("${notification.cleanup.retention-days:90}")
    private int retentionDays;

    /**
     * 每天凌晨 3:00 执行清理。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredNotifications() {
        log.info("开始清理 {} 天前的已读通知...", retentionDays);
        try {
            int count = notificationService.cleanupExpiredRead(retentionDays);
            log.info("清理过期已读通知完成：共清理 {} 条", count);
        } catch (Exception e) {
            log.error("清理过期已读通知失败", e);
        }
    }
}
