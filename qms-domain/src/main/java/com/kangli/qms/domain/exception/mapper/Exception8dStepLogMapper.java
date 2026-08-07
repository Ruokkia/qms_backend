package com.kangli.qms.domain.exception.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.exception.entity.Exception8dStepLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 8D 步骤留痕 Mapper。
 */
public interface Exception8dStepLogMapper extends BaseMapper<Exception8dStepLog> {

    /**
     * 按异常单ID查询步骤留痕（按操作时间升序，还原流程顺序）。
     */
    List<Exception8dStepLog> selectByExceptionId(@Param("exceptionId") Long exceptionId);

    /**
     * 将该异常单最近一条指定阶段的 NEXT_STEP 留痕标记为待审。
     */
    void markPendingApproval(@Param("exceptionId") Long exceptionId, @Param("stage") String stage);
}
