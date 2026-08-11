package com.kangli.qms.service.notification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.domain.notification.entity.Notification;
import com.kangli.qms.domain.notification.mapper.NotificationMapper;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.pusher.NotificationPusher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationServiceImpl 单测：聚焦通知归属（plant 映射）、去重、已读/清理。
 */
class NotificationServiceImplTest {

    private final NotificationPusher pusher = mock(NotificationPusher.class);
    private final NotificationServiceImpl service = spy(new NotificationServiceImpl(pusher));

    /** ServiceImpl 的 baseMapper 由 Spring 注入，单测中通过反射注入 mock。 */
    private void injectBaseMapper(NotificationMapper mapper) {
        try {
            Field f = ServiceImpl.class.getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(service, mapper);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createNotification_insertsAndPushes() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        doAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return 1;
        }).when(mapper).insert(any(Notification.class));
        injectBaseMapper(mapper);

        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setUserId(7L);
        dto.setType("异常闭环");
        dto.setTitle("异常单 E-001 已闭环");
        dto.setContent("请确认");
        dto.setPlantCode("SZ");
        dto.setCreatedBy("admin");

        Notification result = service.createNotification(dto);

        assertNotNull(result.getId());
        assertEquals("深圳", result.getPlantName());
        assertEquals((short) 0, result.getIsRead());
        assertEquals("提醒", result.getLevel());
        verify(pusher, times(1)).push(any(Notification.class));
    }

    @Test
    void createNotification_mzPlant_mapsToMeizhou() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        doAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(101L);
            return 1;
        }).when(mapper).insert(any(Notification.class));
        injectBaseMapper(mapper);

        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setUserId(8L);
        dto.setType("升级");
        dto.setTitle("t");
        dto.setContent("c");
        dto.setPlantCode("MZ");

        Notification result = service.createNotification(dto);
        assertEquals("梅州", result.getPlantName());
    }

    @Test
    void createNotification_withinDedupWindow_updatesExisting() {
        Notification existing = new Notification();
        existing.setId(55L);
        existing.setUserId(7L);
        existing.setIsRead((short) 1);

        NotificationMapper mapper = mock(NotificationMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        doAnswer(inv -> 1).when(mapper).updateById(any(Notification.class));
        injectBaseMapper(mapper);

        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setUserId(7L);
        dto.setBusinessType("EXCEPTION");
        dto.setBusinessId(123L);
        dto.setType("异常闭环");
        dto.setTitle("更新后的标题");
        dto.setContent("更新后的内容");
        dto.setPlantCode("SZ");

        Notification result = service.createNotification(dto);

        // 复用已有通知而非新建，且重置为未读
        assertEquals(55L, result.getId());
        assertEquals((short) 0, result.getIsRead());
        assertEquals("更新后的标题", result.getTitle());
        verify(mapper, times(0)).insert(any(Notification.class));
        verify(pusher, times(1)).push(any(Notification.class));
    }

    @Test
    void createNotification_withoutBusinessKey_noDedup() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(200L);
            return 1;
        }).when(mapper).insert(any(Notification.class));
        injectBaseMapper(mapper);

        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setUserId(7L);
        dto.setType("系统");
        dto.setTitle("t");
        dto.setContent("c");
        dto.setPlantCode("SZ");
        // businessType/businessId 为 null

        Notification result = service.createNotification(dto);
        assertNotNull(result.getId());
        verify(mapper, times(1)).insert(any(Notification.class));
    }

    @Test
    void markAllRead_delegatesToMapper() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        injectBaseMapper(mapper);
        service.markAllRead(7L, LocalDateTime.now());
        verify(mapper, times(1)).markAllRead(any(Long.class), anyString());
    }

    @Test
    void cleanupExpiredRead_delegatesToMapper() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        when(mapper.batchDeleteReadExpired(any(LocalDateTime.class))).thenReturn(3);
        injectBaseMapper(mapper);
        int count = service.cleanupExpiredRead(30);
        assertEquals(3, count);
    }
}
