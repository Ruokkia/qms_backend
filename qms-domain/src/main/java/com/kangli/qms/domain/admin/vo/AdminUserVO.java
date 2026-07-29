package com.kangli.qms.domain.admin.vo;

import lombok.Data;

@Data
public class AdminUserVO {
    private Long id;
    private String account;
    private String realName;
    private String roleCode;
    private String plantCode;
    private String plantName;
    private Short status;
    private Integer authVersion;
    private String lastLoginAt;
}
