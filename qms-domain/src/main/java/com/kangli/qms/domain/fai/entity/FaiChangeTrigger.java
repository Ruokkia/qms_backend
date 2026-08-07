package com.kangli.qms.domain.fai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首件检验变更触发记录实体 — 对应 qms.fai_change_trigger 表。
 * <p>换模具/升级系统/换批次/换设备/材料批次等触发首件检验，状态驱动生产拦截。</p>
 */
@Data
@TableName(value = "fai_change_trigger", schema = "qms")
public class FaiChangeTrigger implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 变更类型：换模具/升级系统/换批次/换设备/材料批次 */
    private String triggerType;
    /** 分类：PRODUCT(产品) / MATERIAL(物料)，二选一 */
    private String itemType;
    /** 产品/物料代码（随 itemType 取值） */
    private String itemCode;
    /** 产品/物料名称（随 itemType 取值） */
    private String itemName;
    /** 产品/物料条码（随 itemType 取值） */
    private String itemBarcode;
    /** 批次号 */
    private String batchNo;
    /** 物料代码（冗余兼容列，落库时与 itemCode 同步） */
    private String materialCode;
    /** 物料名称（冗余兼容列，落库时与 itemName 同步） */
    private String materialName;
    /** 工序：装配/焊接/检测（红线固定） */
    private String processName;
    private String processCode;
    /** 触发原因 */
    private String triggerReason;
    /** 状态：待检验/已检验/关闭/已作废 */
    private String status;
    /** 备注 */
    private String remark;
    /** 作废原因（仅在 status=已作废 时填写） */
    private String voidReason;

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
