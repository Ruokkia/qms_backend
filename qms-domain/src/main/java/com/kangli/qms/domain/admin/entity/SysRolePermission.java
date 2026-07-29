package com.kangli.qms.domain.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName(value = "sys_role_permission", schema = "qms")
public class SysRolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String moduleCode;
    private String actionCode;
    @TableLogic
    private Short isDeleted;
}
