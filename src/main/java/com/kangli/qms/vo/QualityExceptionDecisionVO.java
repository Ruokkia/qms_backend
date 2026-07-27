package com.kangli.qms.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 来料不良自动判定结果。
 */
@Data
public class QualityExceptionDecisionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String severity;
    private String processType;
    private String notificationLevel;
    private Integer responseHours;
    private Integer deadlineDays;
    private BigDecimal defectRate;
    private Integer repeatCount30Days;
    private String ruleReason;
    private List<String> handlingMethods = new ArrayList<>();
    private List<String> requiredMeasures = new ArrayList<>();
}
