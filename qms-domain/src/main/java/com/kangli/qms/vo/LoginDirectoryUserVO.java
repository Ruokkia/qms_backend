package com.kangli.qms.vo;

import lombok.Data;

/** Safe development-only login directory item; it deliberately contains no credential data. */
@Data
public class LoginDirectoryUserVO {
    private String account;
    private String realName;
    private String roleCode;
    private String plantCode;
    private String plantName;
}
