package com.kangli.qms.enums;

/**
 * 分公司编码枚举（固化红线：SZ=深圳 / MZ=梅州，禁止新增）。
 */
public enum PlantCode {

    /** 深圳 */
    SZ,
    /** 梅州 */
    MZ;

    /**
     * 安全转换：字符串转枚举，非法值返回 null。
     */
    public static PlantCode of(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        try {
            return PlantCode.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 获取中文名称。
     */
    public String getChineseName() {
        switch (this) {
            case SZ: return "深圳";
            case MZ: return "梅州";
            default: return name();
        }
    }
}
