package com.kangli.qms.domain.exception.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * BOTH 模式 CAPA 相位审批就绪检查结果 VO。
 * <p>前端在审批面板中实时展示当前相位状态，确认当前用户是否有审批权限。</p>
 */
@Data
@ApiModel(description = "CAPA 相位审批就绪检查结果")
public class CapaPhaseApprovalReadinessVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("当前 CAPA 相位：INITIATE / ROOT_CAUSE_APPROVED / MEASURES_APPROVED / CLOSED")
    private String capaPhase;

    @ApiModelProperty("相位中文描述，如「待根因审批」「待措施审批」")
    private String phaseLabel;

    @ApiModelProperty("当前用户是否可以审批（发起人=质量审核人 R04/R06）")
    private boolean canApprove;

    @ApiModelProperty("是否可以审批的详细说明")
    private String detail;

    @ApiModelProperty("异常单 ID")
    private Long exceptionId;

    public static CapaPhaseApprovalReadinessVO cannotApprove(String capaPhase, String reason) {
        CapaPhaseApprovalReadinessVO vo = new CapaPhaseApprovalReadinessVO();
        vo.setCapaPhase(capaPhase);
        vo.setCanApprove(false);
        vo.setDetail(reason);
        return vo;
    }

    public static CapaPhaseApprovalReadinessVO canApprove(String capaPhase, String phaseLabel) {
        CapaPhaseApprovalReadinessVO vo = new CapaPhaseApprovalReadinessVO();
        vo.setCapaPhase(capaPhase);
        vo.setPhaseLabel(phaseLabel);
        vo.setCanApprove(true);
        return vo;
    }
}
