package com.kangli.qms.service.fai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检验标准中已维护的工序（去重），供变更触发工序下拉使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaiStandardProcessVO {
    private String processCode;
    private String processName;
}
