package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * SPC 子组保存请求（首件导入）。
 */
@Data
public class SpcSubgroupFromFaiDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 首件检验记录 id */
    private Long faiRecordId;
    /** 目标参数 id */
    private Long paramId;
}
