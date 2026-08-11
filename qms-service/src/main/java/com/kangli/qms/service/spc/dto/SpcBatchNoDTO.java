package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * SPC 录入时下拉的真实批号项。
 * <p>批次号取自成品表(prod_batch_or_sn) / 物料表(material_batch_no)，按 itemType+itemCode 路由。</p>
 */
@Data
public class SpcBatchNoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 真实批次号 */
    private String batchNo;
    /** 产品/物料名称（冗余，便于下拉展示） */
    private String itemName;
}
