package com.kangli.qms.domain.fai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.fai.entity.FaiStandardApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * FAI 检验标准审批 Mapper（P1：轻量级审批工作流）。
 */
@Mapper
public interface FaiStandardApprovalMapper extends BaseMapper<FaiStandardApproval> {
}
