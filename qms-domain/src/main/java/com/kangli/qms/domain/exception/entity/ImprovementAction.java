package com.kangli.qms.domain.exception.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 改善措施实体 — 对应 qms.improvement_action 表。
 * <p>关联异常单的临时/纠正/预防措施，对应 8D D3/D4/D5 步骤。</p>
 */
@Data
@TableName(value = "improvement_action", schema = "qms")
public class ImprovementAction implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联异常单ID */
    private Long exceptionId;

    /** 措施类型：临时措施/纠正措施/预防措施 */
    private String actionType;

    /** 措施内容 */
    private String content;

    /** 责任人ID */
    private Long ownerId;

    /** 责任人姓名 */
    private String ownerName;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 状态：PENDING/DONE */
    private String status;

    /** 完成时间 */
    private LocalDateTime completedAt;

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
