package com.kangli.qms.service.spc.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SpcPendingSampleAppendDTO {
    private List<BigDecimal> sampleValues;
}
