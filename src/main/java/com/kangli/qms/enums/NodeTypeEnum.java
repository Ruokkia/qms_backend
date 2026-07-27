package com.kangli.qms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 追溯节点类型枚举（三端统一，对应 qms.item_node.node_type）。
 * <p>取值：SN / 部件 / 关键物料 / 非关键物料 / 来料批次 / 生产批次</p>
 */
@Getter
@AllArgsConstructor
public enum NodeTypeEnum {

    SN("SN", "整机序列号"),
    PART("部件", "部件"),
    CRITICAL_MATERIAL("关键物料", "关键物料"),
    NON_CRITICAL_MATERIAL("非关键物料", "非关键物料"),
    INCOMING_BATCH("来料批次", "来料批次"),
    PRODUCTION_BATCH("生产批次", "生产批次");

    private final String value;
    private final String description;

    /**
     * 根据数据库值解析枚举。
     */
    public static NodeTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (NodeTypeEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
