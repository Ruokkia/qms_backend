package com.kangli.qms.service.admin.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.service.admin.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 审计日志服务实现 — 自动从 LoginUserHolder 取操作人，序列化前后快照落 qms.audit_log。
 */
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    @Override
    public void record(String tableName, Long recordId, String operationType,
                       Object before, Object after, String reason) {
        LoginUser loginUser = LoginUserHolder.get();
        AuditLog log = new AuditLog();
        log.setTableName(tableName);
        log.setRecordId(recordId);
        log.setOperationType(operationType);
        log.setBeforeData(before == null ? null : JSONUtil.toJsonStr(before));
        log.setAfterData(after == null ? null : JSONUtil.toJsonStr(after));
        log.setOperatorId(loginUser != null ? loginUser.getUserId() : null);
        log.setOperatorName(loginUser != null ? loginUser.getRealName() : "系统");
        log.setPlantCode(loginUser != null && loginUser.getPlantCode() != null
                ? loginUser.getPlantCode().name() : null);
        log.setOperationTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        log.setReason(reason);
        log.setCreatedBy("AUDIT_SYSTEM");
        this.save(log);
    }
}
