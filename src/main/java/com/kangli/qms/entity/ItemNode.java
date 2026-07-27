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
 * 追溯引擎节点实体 — 对应 qms.item_node 表。
 * <p>父子 ID 递归结构，支撑全链路正向/反向穿透查询，层级上限 8 层。</p>
 */
@Data
@TableName(value = "item_node", schema = "qms")
public class ItemNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 节点类型：SN / 部件 / 关键物料 / 非关键物料 / 来料批次 / 生产批次 */
    private String nodeType;

    /** 节点编码（业务唯一，如 SN2026-SZ-0001、MC-001、B-SZ-2026-001） */
    private String nodeCode;

    /** 直接父级ID（NULL=根节点，CHECK(parent_id != id) 防自引用） */
    private Long parentId;

    /** 关联批次ID（来料批次或生产批次，关联 material_inspection.id） */
    private Long batchId;

    /** 该节点用量/配比 */
    private BigDecimal qtyUsed;

    /** 关联工单ID */
    private Long workOrderId;

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
