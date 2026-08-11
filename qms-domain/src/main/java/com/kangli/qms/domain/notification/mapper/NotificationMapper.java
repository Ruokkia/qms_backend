package com.kangli.qms.domain.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 通知 Mapper。
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 将指定用户的全部未读通知标记为已读。
     */
    int markAllRead(@Param("userId") Long userId, @Param("readAt") String readAt);

    /**
     * 统计未读通知总数。
     */
    Long selectUnreadCount(@Param("userId") Long userId);

    /**
     * 批量逻辑删除过期的已读通知。
     *
     * @param cutoff 截止时间（此时间之前的已读通知将被清理）
     * @return 清理条数
     */
    int batchDeleteReadExpired(@Param("cutoff") LocalDateTime cutoff);
}
