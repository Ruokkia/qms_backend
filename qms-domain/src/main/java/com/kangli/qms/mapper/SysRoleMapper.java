package com.kangli.qms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.entity.SysRole;
import org.apache.ibatis.annotations.Select;

/**
 * 角色 Mapper（MyBatis-Plus BaseMapper，暂无需自定义 SQL）。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(2026072901)) AS role_code_lock")
    Integer lockRoleCodeGeneration();

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(role_code FROM 2) AS INTEGER)), 7) " +
            "FROM qms.sys_role WHERE role_code ~ '^R[0-9]+$'")
    Integer selectMaxRoleNumberIncludingDeleted();
}
