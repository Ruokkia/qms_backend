package com.kangli.qms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.entity.Exception8d;
import org.apache.ibatis.annotations.Param;

/**
 * 8D 报告 Mapper。
 */
public interface Exception8dMapper extends BaseMapper<Exception8d> {

    /**
     * 按异常单ID查询 8D 报告（仅未删除）。
     */
    Exception8d selectByExceptionId(@Param("exceptionId") Long exceptionId);

    /**
     * 按异常单ID查询 8D 报告（含已软删除记录，用于重置后恢复场景）。
     */
    Exception8d selectByExceptionIdIgnoreDeleted(@Param("exceptionId") Long exceptionId);

    /**
     * 穿透 @TableLogic 恢复已软删除记录（仅翻转 is_deleted 1→0）。
     * <p>必须先恢复标志位，后续 saveOrUpdate 的 updateById 才能正常匹配记录。</p>
     */
    int restoreDeletedById(@Param("id") Long id);
}
