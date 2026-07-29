package com.kangli.qms.api.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.domain.notification.entity.Notification;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.domain.notification.vo.NotificationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M2-3 通知底座 Controller。
 * <p>路径：/api/v1/notifications</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@Api(tags = "M2-通知底座")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @ApiOperation(value = "查询当前用户通知列表")
    public R<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Long businessId) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<Notification> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, loginUser.getUserId());
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead.shortValue());
        }
        if (StringUtils.hasText(businessType)) {
            wrapper.eq(Notification::getBusinessType, businessType);
        }
        if (businessId != null) {
            wrapper.eq(Notification::getBusinessId, businessId);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);

        Page<Notification> result = notificationService.page(pageObj, wrapper);
        List<NotificationVO> voList = result.getRecords().stream().map(n -> {
            NotificationVO vo = new NotificationVO();
            BeanUtils.copyProperties(n, vo);
            return vo;
        }).collect(Collectors.toList());

        return R.ok(new PageResult<>(voList, result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @GetMapping("/unread-count")
    @ApiOperation(value = "未读通知数")
    public R<Map<String, Object>> unreadCount() {
        LoginUser loginUser = getCurrentLoginUser();
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, loginUser.getUserId())
                .eq(Notification::getIsRead, (short) 0);
        long total = notificationService.count(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        // 简化 byType：这里仅返回 total，如需按类型分组可后续扩展
        result.put("byType", new java.util.ArrayList<>());
        return R.ok(result);
    }

    @PostMapping("/{id}/read")
    @ApiOperation(value = "标记已读")
    public R<Void> markRead(@PathVariable Long id) {
        LoginUser loginUser = getCurrentLoginUser();
        Notification notification = notificationService.getById(id);
        if (notification == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        if (!loginUser.getUserId().equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能标记自己的通知为已读");
        }
        Notification update = new Notification();
        update.setId(id);
        update.setIsRead((short) 1);
        update.setReadAt(LocalDateTime.now());
        notificationService.updateById(update);
        return R.ok(null, "标记已读成功");
    }

    @PostMapping("/read-all")
    @ApiOperation(value = "全部已读")
    public R<Void> markAllRead() {
        LoginUser loginUser = getCurrentLoginUser();
        notificationService.markAllRead(loginUser.getUserId(), LocalDateTime.now());
        return R.ok(null, "全部已读成功");
    }


    @PostMapping
    @ApiOperation(value = "创建通知（内部/管理员）", notes = "内部调用，普通前端场景不建议直接调用")
    public R<Notification> create(@Valid @RequestBody NotificationCreateDTO dto) {
        LoginUser loginUser = getCurrentLoginUser();
        if (dto.getPlantCode() == null) {
            dto.setPlantCode(loginUser.getPlantCode().name());
        }
        if (dto.getCreatedBy() == null) {
            dto.setCreatedBy(loginUser.getRealName());
        }
        return R.ok(notificationService.createNotification(dto), "创建成功");
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
