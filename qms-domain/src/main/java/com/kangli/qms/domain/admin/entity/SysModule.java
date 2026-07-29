package com.kangli.qms.domain.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName(value = "sys_module", schema = "qms")
public class SysModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String moduleCode;
    private String moduleName;
    private Short status;
    @TableLogic
    private Short isDeleted;
}
