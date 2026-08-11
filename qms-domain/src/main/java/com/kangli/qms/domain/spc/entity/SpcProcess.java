package com.kangli.qms.domain.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SPC 工序定义实体 — 对应 qms.spc_process 表。
 * <p>装配 / 焊接 / 检测 等关键工序定义，作为参数的归属维度。</p>
 */
@Data
@TableName(value = "spc_process", schema = "qms")
public class SpcProcess implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工序编码（ASM 装配 / WDG 焊接 / INS 检测） */
    private String processCode;
    /** 工序名称（装配/焊接/检测） */
    private String processName;
    /** 工序描述 */
    private String description;
    /** 排序 */
    private Integer sortOrder;

    /** 是否启用：是/否 */
    private String isActive;

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
