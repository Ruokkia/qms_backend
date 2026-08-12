package com.kangli.qms.service.admin.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
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
        // 优先取当前登录用户上下文的 plantCode；
        // 后台调度线程（如异常自动触发）通过 LoginUserHolder 注入「系统」账号，
        // 若上下文缺失或 plantCode 为空，则从被审计对象（异常单等自带 plantCode 的实体）回退，
        // 避免 audit_log.plant_code NOT NULL 约束导致建单事务整体回滚。
        String plantCode = loginUser != null && loginUser.getPlantCode() != null
                ? loginUser.getPlantCode().name() : null;
        if (plantCode == null && after instanceof ExceptionOrder) {
            plantCode = ((ExceptionOrder) after).getPlantCode();
        }
        log.setOperatorId(loginUser != null ? loginUser.getUserId() : null);
        log.setOperatorName(loginUser != null ? loginUser.getRealName() : "系统");
        log.setPlantCode(plantCode);
        log.setOperationTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        log.setReason(reason);
        log.setCreatedBy("AUDIT_SYSTEM");
        this.save(log);
    }
}
