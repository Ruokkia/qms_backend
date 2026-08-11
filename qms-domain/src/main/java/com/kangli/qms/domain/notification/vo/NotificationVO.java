package com.kangli.qms.domain.notification.vo;

import com.kangli.qms.domain.notification.entity.Notification;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知列表 VO（继承实体，可扩展发送人/接收人姓名）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "通知 VO")
public class NotificationVO extends Notification {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "接收人姓名")
    private String userName;

    @ApiModelProperty(value = "扩展数据（已解析为 Map）")
    private java.util.Map<String, Object> extraDataMap;
}
