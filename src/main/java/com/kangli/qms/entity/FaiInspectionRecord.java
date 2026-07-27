package com.kangli.qms.entity;

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
 * 首件检验主记录实体 — 对应 qms.fai_inspection_record 表。
 * <p>从变更触发创建，含判定结果（待判定/合格/不合格）与电子签名状态（未签/已签）。</p>
 */
@Data
@TableName(value = "fai_inspection_record", schema = "qms")
public class FaiInspectionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 首件编号（唯一，规则 FAI-{plant_code}-{yyyyMMdd}-{4位流水}） */
    private String faiNo;
    /** 关联变更触发 fai_change_trigger.id */
    private Long changeTriggerId;
    /** 物料代码（从变更触发复制） */
    private String materialCode;
    /** 物料名称（从变更触发复制） */
    private String materialName;
    /** 批次号（从变更触发复制） */
    private String batchNo;
    /** 工序：装配/焊接/检测 */
    private String processName;
    /** 统一工序库编码 */
    private String processCode;
    /** 工单号 */
    private String workOrderNo;
    /** 判定结果：待判定/合格/不合格 */
    private String inspectionResult;
    /** 电子签名状态：未签/已签 */
    private String signatureStatus;
    /** 备注 */
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
