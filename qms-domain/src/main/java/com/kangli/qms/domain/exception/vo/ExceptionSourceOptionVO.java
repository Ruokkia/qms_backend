package com.kangli.qms.domain.exception.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 异常单「选择源头记录」聚合接口返回 VO。
 * <p>按来源类型从不同的源头库中查询并统一精简为以下字段，供前端表单选择。</p>
 */
@Data
public class ExceptionSourceOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源头记录主键（落库到 exception_order.source_id） */
    private Long id;

    /** 来源类型：material / fai / finished（与 exception_order.source_type 取值对应） */
    private String sourceType;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 / 产品名称 */
    private String materialName;

    /** 批号 / 序列号 */
    private String batchNo;

    /** 供应商名称 */
    private String supplierName;

    /** 工单号 / 生产订单号（可选展示） */
    private String workOrderNo;
}
