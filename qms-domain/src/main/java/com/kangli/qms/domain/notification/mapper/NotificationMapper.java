package com.kangli.qms.domain.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;

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
}
