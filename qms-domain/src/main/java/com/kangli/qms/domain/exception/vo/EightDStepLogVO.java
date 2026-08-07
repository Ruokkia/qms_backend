package com.kangli.qms.domain.exception.vo;

import com.kangli.qms.domain.exception.entity.Exception8dStepLog;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 8D 步骤留痕 VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "8D 步骤留痕 VO")
public class EightDStepLogVO extends Exception8dStepLog {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "操作类型中文描述（SAVE=保存内容 / NEXT_STEP=推进到下一步）")
    private String operationDesc;
}
