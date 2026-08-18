package com.kangli.qms.domain.supplier.vo;

import com.kangli.qms.domain.supplier.entity.SupplierAuditFinding;
import com.kangli.qms.domain.supplier.entity.SupplierAuditRecord;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商现场审核报告 VO（聚合审核记录 + 不符合项清单，可追溯审核人/时间/问题）。
 */
@Data
@ApiModel(description = "供应商现场审核报告")
public class SupplierAuditReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "审核记录主体")
    private SupplierAuditRecord record;

    @ApiModelProperty(value = "不符合项清单（含整改状态）")
    private List<FindingWithRectification> findings;

    @Data
    @ApiModel(description = "不符合项及整改状态")
    public static class FindingWithRectification implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "不符合项")
        private SupplierAuditFinding finding;

        @ApiModelProperty(value = "整改措施")
        private String measure;

        @ApiModelProperty(value = "整改责任人")
        private String owner;

        @ApiModelProperty(value = "验证结果：通过/不通过")
        private String verifyResult;

        @ApiModelProperty(value = "是否闭环")
        private Boolean closed;
    }
}
