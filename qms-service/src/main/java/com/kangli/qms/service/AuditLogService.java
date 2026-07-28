package com.kangli.qms.service;

/**
 * 审计日志服务 — 记录业务表 CUD 操作，满足 GMP 审计追溯要求。
 * <p>由业务 Service / Controller 在关键操作后显式调用 record()，统一落 qms.audit_log。</p>
 */
public interface AuditLogService {

    /**
     * 记录一次业务审计日志。
     *
     * @param tableName     被操作表名（如 improvement_action / verification_record / exception_order）
     * @param recordId      被操作行主键
     * @param operationType 操作类型：CREATE / UPDATE / DELETE
     * @param before        变更前对象快照（可为 null，如 CREATE）
     * @param after         变更后对象快照（可为 null，如 DELETE）
     * @param reason        操作原因 / 备注说明
     */
    void record(String tableName, Long recordId, String operationType,
                Object before, Object after, String reason);
}
