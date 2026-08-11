package com.kangli.qms.service.exception;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.domain.exception.entity.ExceptionApprovalConfig;
import com.kangli.qms.domain.exception.vo.ExceptionApprovalConfigVO;
import com.kangli.qms.service.exception.dto.ExceptionApprovalConfigDTO;

import java.util.List;

/**
 * 异常整改阶段级审批配置服务（8D 与 CAPA 共用）。
 */
public interface ExceptionApprovalConfigService extends IService<ExceptionApprovalConfig> {

    /**
     * 查询某流程维度下的全部阶段审批配置（默认 + 当前分公司覆盖）。
     */
    List<ExceptionApprovalConfigVO> listByFlow(String processFlow, String plantCode);

    /**
     * 获取某流程+阶段+分公司的生效配置（用于推进时判断是否需审批）。
     */
    ExceptionApprovalConfig resolveConfig(String processFlow, String stage, String plantCode);

    /**
     * 保存或更新一条审批配置（系统管理模块 CRUD）。
     *
     * @param clientIp 客户端真实 IP，用于写入审计日志
     */
    void saveOrUpdateConfig(ExceptionApprovalConfigDTO dto, String clientIp);

    /**
     * 逻辑删除一条配置。
     *
     * @param clientIp 客户端真实 IP，用于写入审计日志
     */
    void deleteConfig(Long id, String clientIp);
}
