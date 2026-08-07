package com.kangli.qms.domain.exception.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.exception.entity.ExceptionApprovalConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 异常整改阶段级审批配置 Mapper。
 */
public interface ExceptionApprovalConfigMapper extends BaseMapper<ExceptionApprovalConfig> {

    /**
     * 查询指定流程+阶段+分公司的默认配置（仅未删除）。
     * 跳过租户拦截：配置本身按 plant_code 过滤，且 resolveConfig 需跨厂回退到 SZ。
     */
    @InterceptorIgnore(tenantLine = "true")
    ExceptionApprovalConfig selectDefault(@Param("processFlow") String processFlow,
                                          @Param("stage") String stage,
                                          @Param("plantCode") String plantCode);
}
