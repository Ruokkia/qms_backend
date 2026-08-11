package com.kangli.qms.domain.fai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.kangli.qms.domain.fai.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * FAI 检验标准审批实体（P1：轻量级审批工作流）。
 */
@Data
@TableName(value = "fai_standard_approval", schema = "qms", autoResultMap = true)
public class FaiStandardApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联标准 ID（编辑/删除时存在，新建时为 null） */
    private Long standardId;

    /** 审批类型：CREATE / UPDATE / DELETE */
    private String approvalType;

    /** 待审批的请求数据快照（JSON） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode requestData;

    /** 提交人 */
    private String requester;

    /** 提交时间 */
    private LocalDateTime requestedAt;

    /** 审批人 */
    private String approver;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 审批状态：PENDING / APPROVED / REJECTED */
    private String approvalStatus;

    /** 驳回原因 */
    private String rejectReason;

    /** 是否已执行 */
    private Boolean applied;

    /** 提交备注 */
    private String remark;

    /** 厂区编码 */
    private String plantCode;

    /** 厂区名称 */
    private String plantName;

    /** 乐观锁版本号（并发审批防重入） */
    @Version
    private Integer version;
}
