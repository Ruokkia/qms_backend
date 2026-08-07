package com.kangli.qms.domain.fai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * FAI 检验标准变更历史 Mapper（P0：变更追溯）。
 */
@Mapper
public interface FaiInspectionStandardHistoryMapper extends BaseMapper<FaiInspectionStandardHistory> {
}
