package com.kangli.qms.domain.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 关键参数定义实体 — 对应 qms.spc_parameter 表。
 * <p>定义每个参数的规格上下限、目标值、子组大小与控制图类型。</p>
 */
@Data
@TableName(value = "spc_parameter", schema = "qms")
public class SpcParameter implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工序 spc_process.id */
    private Long processId;
    /** 参数编码 */
    private String paramCode;
    /** 参数名称 */
    private String paramName;
    /** 参数类型：尺寸/温度/压力/扭矩/电压 */
    private String paramType;
    /** 单位（mm/°C/MPa/N·m/V） */
    private String unit;
    /** 规格上限 USL */
    private BigDecimal upperSpecLimit;
    /** 规格下限 LSL */
    private BigDecimal lowerSpecLimit;
    /** 目标值 */
    private BigDecimal targetValue;
    /** 子组大小 n（2~10） */
    private Integer subgroupSize;
    /** 控制图类型：Xbar-R / Xbar-s */
    private String chartType;
    /** 是否启用：是/否 */
    private String isActive;

    /** 小数位数 */
    private Integer decimalPlaces;

    /** 是否关键特性：是/否 */
    private String isCritical;

    /** 变更备注 */
    private String changeRemark;

    // ---- 系统扩展列 ----
    private String plantCode;
    private String plantName;
    private String createdBy;
    private String updatedBy;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
