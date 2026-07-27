package com.kangli.qms.security;

import lombok.Value;

@Value
public class PermissionRequirement {
    String moduleCode;
    PermissionAction action;
}
