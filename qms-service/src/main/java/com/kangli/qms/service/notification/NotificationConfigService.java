package com.kangli.qms.service.notification;

import com.kangli.qms.domain.notification.entity.NotificationConfig;

import java.util.List;

public interface NotificationConfigService {

    /**
     * 获取所有通知配置
     */
    List<NotificationConfig> listAll();

    /**
     * 按场景编码获取配置
     */
    NotificationConfig getByScenarioCode(String scenarioCode);

    /**
     * 更新场景配置（记录完整审计）
     *
     * @param id           配置ID
     * @param config       新配置
     * @param operatorName 操作人
     * @param ipAddress    IP地址
     * @param reason       修改原因
     * @return 更新后的配置
     */
    NotificationConfig update(Long id, NotificationConfig config, String operatorName, String ipAddress, String reason);

    /**
     * 按场景编码获取接收角色列表
     *
     * @param scenarioCode 场景编码
     * @return 角色编码列表
     */
    List<String> getReceivingRoleCodes(String scenarioCode);

    /**
     * 按场景编码获取严重等级追加角色列表（仅 EXCEPTION_CREATED）
     *
     * @param scenarioCode 场景编码
     * @return 严重等级追加角色编码列表
     */
    List<String> getSeverityExtraRoleCodes(String scenarioCode);

    /**
     * 根据角色编码列表，查询指定分公司下所有启用用户的 ID 列表（去重）。
     * 用于指派通知中的按角色抄送。
     *
     * @param roleCodes 角色编码列表
     * @param plantCode 分公司编码
     * @return 用户 ID 列表
     */
    List<Long> listUserIdsByRoleCodes(List<String> roleCodes, String plantCode);
}
