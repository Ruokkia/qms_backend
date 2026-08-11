package com.kangli.qms.service.notification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.domain.notification.entity.Notification;
import com.kangli.qms.domain.notification.mapper.NotificationMapper;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.pusher.NotificationPusher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知服务实现。
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /** 去重时间窗口（分钟），默认 60 分钟。同一 businessId + userId + businessType 组合在此窗口内只保留最新一条。 */
    @Value("${notification.dedup.window-minutes:60}")
    private int dedupWindowMinutes;

    private final NotificationPusher notificationPusher;

    public NotificationServiceImpl(NotificationPusher notificationPusher) {
        this.notificationPusher = notificationPusher;
    }

    /**
     * 创建通知（含去重：同一 businessId + userId + businessType 在时间窗口内只保留最新一条）。
     */
    @Override
    public Notification createNotification(NotificationCreateDTO dto) {
        // 去重：查询最近的同类通知
        Notification existing = findRecentDuplicate(dto);
        if (existing != null) {
            // 更新现有通知
            existing.setTitle(dto.getTitle());
            existing.setContent(dto.getContent());
            existing.setLevel(dto.getLevel() != null ? dto.getLevel() : "提醒");
            existing.setExtraData(dto.getExtraData());
            existing.setIsRead((short) 0);
            existing.setCreatedAt(LocalDateTime.now());
            existing.setUpdatedBy(dto.getCreatedBy());
            if (dto.getExpireAt() != null) {
                existing.setExpireAt(dto.getExpireAt());
            }
            baseMapper.updateById(existing);
            log.info("通知已去重更新：userId={}, type={}, businessId={}, existingId={}",
                    dto.getUserId(), dto.getType(), dto.getBusinessId(), existing.getId());
            notificationPusher.push(existing);
            return existing;
        }

        // 新建通知
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
        notificationPusher.push(notification);
        return notification;
    }

    /**
     * 查询去重窗口内的重复通知。
     */
    private Notification findRecentDuplicate(NotificationCreateDTO dto) {
        if (dto.getBusinessType() == null || dto.getBusinessId() == null || dto.getUserId() == null) {
            return null;
        }
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(dedupWindowMinutes);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getBusinessType, dto.getBusinessType())
                .eq(Notification::getBusinessId, dto.getBusinessId())
                .eq(Notification::getUserId, dto.getUserId())
                .ge(Notification::getCreatedAt, windowStart)
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT 1");
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public void markAllRead(Long userId, LocalDateTime readAt) {
        baseMapper.markAllRead(userId, readAt.toString());
    }

    @Override
    public int cleanupExpiredRead(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int count = baseMapper.batchDeleteReadExpired(cutoff);
        log.info("清理过期已读通知完成：删除 {} 条（{} 天前）", count, retentionDays);
        return count;
    }
}

