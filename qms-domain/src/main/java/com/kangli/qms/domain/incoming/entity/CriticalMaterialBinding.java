package com.kangli.qms.domain.incoming.entity;

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
 * 关键物料绑定清单实体 — 对应 qms.critical_material_binding 表。
 * <p>工单与关键物料 SN 级绑定，建立来料→生产追溯链关键环节。</p>
 */
@Data
@TableName(value = "critical_material_binding", schema = "qms")
public class CriticalMaterialBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;
    /** 工单号 */
    private String workOrderNo;
    /** 产品条码/SN */
    private String productBarcode;
    private String productMaterialNo;
    private String productName;
    private BigDecimal workOrderQty;
    private String materialBarcode;
    /** 物料代码 */
    private String materialCode;
    private String materialName;
    /** 子项批号（详情展示用，不影响追溯链路） */
    private String sonLotNo;
    private String specModel;
    private String scanner;
    private LocalDateTime scanTime;
    private String processCode;
    /** 工序名称（仅限：装配/焊接/检测） */
    private String processName;
    /** 是否生效：是/否 */
    private String isActive;
    private String deactivateOperator;
    private LocalDateTime deactivateTime;
    private String remark;

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
