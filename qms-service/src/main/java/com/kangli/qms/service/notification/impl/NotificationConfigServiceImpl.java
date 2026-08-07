package com.kangli.qms.service.notification.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.notification.entity.NotificationConfig;
import com.kangli.qms.domain.notification.mapper.NotificationConfigMapper;
import com.kangli.qms.service.notification.NotificationConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NotificationConfigServiceImpl implements NotificationConfigService {

    private static final String TABLE_NAME = "notification_config";
    private static final String OP_UPDATE = "UPDATE_NOTIFICATION_CONFIG";

    private final NotificationConfigMapper notificationConfigMapper;
    private final AuditLogMapper auditLogMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper sysUserMapper;

    public NotificationConfigServiceImpl(NotificationConfigMapper notificationConfigMapper,
                                         AuditLogMapper auditLogMapper,
                                         SysRoleMapper roleMapper,
                                         SysUserMapper sysUserMapper) {
        this.notificationConfigMapper = notificationConfigMapper;
        this.auditLogMapper = auditLogMapper;
        this.roleMapper = roleMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<NotificationConfig> listAll() {
        return notificationConfigMapper.selectAllActive();
    }

    @Override
    public NotificationConfig getByScenarioCode(String scenarioCode) {
        return notificationConfigMapper.selectByScenarioCode(scenarioCode);
    }

    @Override
    @Transactional
    public NotificationConfig update(Long id, NotificationConfig config,
                                     String operatorName, String ipAddress, String reason) {
        NotificationConfig existing = notificationConfigMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知配置不存在");
        }

        // 变更前快照
        String beforeSnapshot = JSONUtil.toJsonStr(existing);

        existing.setRoleCodes(config.getRoleCodes());
        existing.setSeverityExtraRoles(config.getSeverityExtraRoles());
        existing.setEnabled(config.getEnabled());
        existing.setUpdatedBy(operatorName);
        existing.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        notificationConfigMapper.updateById(existing);

        // 写入审计日志
        writeAuditLog(existing, beforeSnapshot, operatorName, ipAddress, reason);

        // 刷新后返回最新
        return notificationConfigMapper.selectById(id);
    }

    @Override
    public List<String> getReceivingRoleCodes(String scenarioCode) {
        NotificationConfig config = notificationConfigMapper.selectByScenarioCode(scenarioCode);
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            return Collections.emptyList();
        }
        return parseRoleCodes(config.getRoleCodes());
    }

    @Override
    public List<String> getSeverityExtraRoleCodes(String scenarioCode) {
        NotificationConfig config = notificationConfigMapper.selectByScenarioCode(scenarioCode);
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            return Collections.emptyList();
        }
        return parseRoleCodes(config.getSeverityExtraRoles());
    }

    @Override
    public List<Long> listUserIdsByRoleCodes(List<String> roleCodes, String plantCode) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlantCode, plantCode)
                .eq(SysUser::getStatus, (short) 1)
                .in(SysUser::getRoleCode, roleCodes))
                .stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 解析 JSON 数组为角色编码列表
     */
    private List<String> parseRoleCodes(String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank() || "[]".equals(jsonArray.trim())) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(jsonArray, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void writeAuditLog(NotificationConfig after, String beforeSnapshot,
                                String operatorName, String ipAddress, String reason) {
        AuditLog log = new AuditLog();
        log.setTableName(TABLE_NAME);
        log.setRecordId(after.getId());
        log.setOperationType(OP_UPDATE);
        log.setBeforeData(beforeSnapshot);
        log.setAfterData(JSONUtil.toJsonStr(after));
        log.setOperationContent(buildOperationContent(JSONUtil.toBean(beforeSnapshot, NotificationConfig.class), after));
        LoginUser currentUser = LoginUserHolder.get();
        log.setOperatorId(currentUser == null ? null : currentUser.getUserId());
        log.setOperatorName(operatorName);
        log.setPlantCode(currentUser != null && currentUser.getPlantCode() != null
                ? currentUser.getPlantCode().name() : "*");
        log.setIpAddress(ipAddress);
        log.setReason(reason);
        log.setCreatedBy(operatorName);
        log.setOperationTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        auditLogMapper.insert(log);
    }

    private String buildOperationContent(NotificationConfig before, NotificationConfig after) {
        Map<String, String> roleNames = roleMapper.selectList(new LambdaQueryWrapper<SysRole>())
                .stream().collect(Collectors.toMap(SysRole::getRoleCode, SysRole::getRoleName, (left, right) -> left));
        List<String> changes = new java.util.ArrayList<>();
        appendRoleChange(changes, "接收角色", before.getRoleCodes(), after.getRoleCodes(), roleNames);
        appendRoleChange(changes, "严重追加角色", before.getSeverityExtraRoles(), after.getSeverityExtraRoles(), roleNames);
        if (!java.util.Objects.equals(before.getEnabled(), after.getEnabled())) {
            changes.add("状态由“" + enabledName(before.getEnabled()) + "”调整为“" + enabledName(after.getEnabled()) + "”");
        }
        String scenarioName = after.getScenarioName() == null || after.getScenarioName().isBlank()
                ? "通知场景" : after.getScenarioName();
        return "通知配置（" + scenarioName + "）：" + (changes.isEmpty() ? "未检测到配置变化" : String.join("；", changes));
    }

    private void appendRoleChange(List<String> changes, String label, String before, String after,
                                  Map<String, String> roleNames) {
        List<String> beforeCodes = parseRoleCodes(before);
        List<String> afterCodes = parseRoleCodes(after);
        if (!beforeCodes.equals(afterCodes)) {
            changes.add(label + "由“" + roleListName(beforeCodes, roleNames) + "”调整为“"
                    + roleListName(afterCodes, roleNames) + "”");
        }
    }

    private String roleListName(List<String> roleCodes, Map<String, String> roleNames) {
        if (roleCodes.isEmpty()) return "未配置";
        return roleCodes.stream().map(code -> roleNames.getOrDefault(code, "已删除角色"))
                .collect(Collectors.joining("、"));
    }

    private String enabledName(Integer enabled) {
        return enabled != null && enabled == 1 ? "启用" : "停用";
    }
}
