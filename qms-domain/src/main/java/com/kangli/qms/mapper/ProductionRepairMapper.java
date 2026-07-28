package com.kangli.qms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.entity.ProductionRepair;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产维修记录 Mapper（基础 CRUD，由 MyBatis-Plus 提供）。
 */
@Mapper
public interface ProductionRepairMapper extends BaseMapper<ProductionRepair> {
}
