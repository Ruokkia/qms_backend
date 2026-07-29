package com.kangli.qms.domain.exception.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 闭环前置条件检查结果 VO。
 * <p>前端在验证阶段实时展示检查清单，全部 PASS 后「确认闭环」按钮才可点击。</p>
 */
@Data
@ApiModel(description = "闭环前置条件检查结果")
public class CloseReadinessVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("是否满足全部闭环条件")
    private boolean canClose;

    @ApiModelProperty("逐项检查清单")
    private List<CheckItem> checks;

    @Data
    @ApiModel(description = "单项检查")
    public static class CheckItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @ApiModelProperty("检查项名称：整改计划 / 改善措施 / 验证记录 / 8D报告")
        private String item;

        @ApiModelProperty("状态：PASS / FAIL / NA")
        private String status;

        @ApiModelProperty("详情描述，如 2/2 已完成 / 1/3 DONE，2条PENDING")
        private String detail;
    }
}
