package com.kangli.qms.vo;

import lombok.Data;

/** Human-readable permission metadata for the administration UI. */
@Data
public class PermissionDisplayVO {
    private String code;
    private String name;
    private String description;
}
