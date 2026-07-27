package com.kangli.qms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * IQC 检验状态枚举（三端统一）。
 * <p>取值：待检 / 在检 / 已检 / 异常</p>
 */
@Getter
@AllArgsConstructor
public enum IqcStatusEnum {

    PENDING("待检", "待检验"),
    IN_PROGRESS("在检", "检验中"),
    DONE("已检", "已检验完成"),
    ABNORMAL("异常", "检验异常");

    private final String value;
    private final String description;
}
