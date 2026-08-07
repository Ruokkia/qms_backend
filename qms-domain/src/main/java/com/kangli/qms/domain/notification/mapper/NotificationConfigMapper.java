package com.kangli.qms.domain.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.notification.entity.NotificationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationConfigMapper extends BaseMapper<NotificationConfig> {

    /**
     * 查询所有未删除的配置
     */
    @Select("SELECT * FROM notification_config WHERE is_deleted = 0 ORDER BY id")
    List<NotificationConfig> selectAllActive();

    /**
     * 按场景编码查询
     */
    @Select("SELECT * FROM notification_config WHERE scenario_code = #{scenarioCode} AND is_deleted = 0")
    NotificationConfig selectByScenarioCode(@Param("scenarioCode") String scenarioCode);
}
