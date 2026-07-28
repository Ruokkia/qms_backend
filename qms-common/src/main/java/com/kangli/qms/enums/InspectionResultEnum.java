package com.kangli.qms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 检验结果枚举（三端统一）。
 * <p>取值：合格 / 不合格</p>
 */
@Getter
@AllArgsConstructor
public enum InspectionResultEnum {

    PASS("合格", "检验合格"),
    FAIL("不合格", "检验不合格");

    private final String value;
    private final String description;
}
