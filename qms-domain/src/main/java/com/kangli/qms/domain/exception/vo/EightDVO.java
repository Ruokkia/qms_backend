package com.kangli.qms.domain.exception.vo;

import com.kangli.qms.domain.exception.entity.Exception8d;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 8D 报告 VO（继承实体，无额外扩展字段时可直接复用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "8D 报告 VO")
public class EightDVO extends Exception8d implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "异常单号")
    private String exceptionNo;
}
