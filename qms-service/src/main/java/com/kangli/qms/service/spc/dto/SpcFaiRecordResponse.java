package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SPC 可联动的首件记录响应（仅合格且已签的首件）。
 */
@Data
public class SpcFaiRecordResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String faiNo;
    private String materialName;
    private String batchNo;
    private String processName;
    private LocalDateTime createdAt;
    private String plantCode;
    private String plantName;
}
