package com.kangli.qms.domain.fai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.kangli.qms.domain.fai.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * FAI 检验标准变更历史实体（P0：变更追溯）。
 * <p>记录标准的 创建/编辑/删除 操作，存储前后 JSON 快照以便差异对比。</p>
 */
@Data
@TableName(value = "fai_inspection_standard_history", schema = "qms", autoResultMap = true)
public class FaiInspectionStandardHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联标准 ID */
    private Long standardId;

    /** 变更类型：CREATE / UPDATE / DELETE */
    private String changeType;

    /** 操作人填写的变更原因 */
    private String changeReason;

    /** 变更前快照（JSON：{standard:{...}, items:[...]}），CREATE 时为 null */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode beforeSnapshot;

    /** 变更后快照（JSON：{standard:{...}, items:[...]}），DELETE 时为 null */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode afterSnapshot;

    /** 自动生成的差异摘要 */
    private String diffSummary;

    /** 操作人账号 */
    private String changedBy;

    /** 操作时间 */
    private LocalDateTime changedAt;

    /** 厂区编码 */
    private String plantCode;

    /** 厂区名称 */
    private String plantName;
}
