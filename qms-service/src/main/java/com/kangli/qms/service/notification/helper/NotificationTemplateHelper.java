package com.kangli.qms.service.notification.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知模板帮助类 — 集中管理所有通知场景的 title / content / extraData 构造逻辑。
 * <p>调用方只需传入业务参数，无需关心字符串拼接和 extraData 序列化。</p>
 */
@Slf4j
public final class NotificationTemplateHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BIZ_TYPE_EXCEPTION = "EXCEPTION_ORDER";
    private static final String BIZ_TYPE_ESCALATION = "ESCALATION";

    private NotificationTemplateHelper() {
    }

    // ===== 异常单场景 =====

    /**
     * 异常单创建通知（含严重等级、判定规则、整改截止日期）。
     */
    public static NotificationCreateDTO forExceptionCreated(Long userId, String plantCode,
                                                             ExceptionOrder order, String createdBy) {
        String level = "严重".equals(order.getSeverity()) ? "严重" : "提醒";
        String prefix = "严重".equals(order.getSeverity()) ? "【严重】" : "";
        String title = prefix + "新异常单 " + order.getExceptionNo();

        String content = "判定：" + order.getRuleReason()
                + "；待质量部门发起整改"
                + "；整改截止：" + order.getDeadline();

        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("exceptionNo", order.getExceptionNo());
        extraData.put("supplierName", order.getSupplierName());
        extraData.put("severity", order.getSeverity());
        extraData.put("ruleReason", order.getRuleReason());
        extraData.put("deadline", order.getDeadline() != null ? order.getDeadline().toString() : null);

        return build(userId, NotificationTypeEnum.EXCEPTION_CREATED.getCode(), level,
                title, content, BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy, extraData);
    }

    /**
     * 异常单状态变更通知。
     */
    public static NotificationCreateDTO forExceptionStatusChanged(Long userId, String plantCode,
                                                                   ExceptionOrder order,
                                                                   String newStatus, String createdBy) {
        return build(userId, NotificationTypeEnum.EXCEPTION_STATUS_CHANGED.getCode(), "提醒",
                "异常单 " + order.getExceptionNo() + " 状态变更",
                "新状态：" + newStatus,
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 异常单闭环通知（给当前操作人）。
     */
    public static NotificationCreateDTO forExceptionClosed(Long userId, String plantCode,
                                                            ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EXCEPTION_STATUS_CHANGED.getCode(), "提醒",
                "异常单 " + order.getExceptionNo() + " 状态变更",
                "新状态：已闭环",
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 异常单闭环通知（给管理员/质量经理）。
     */
    public static NotificationCreateDTO forExceptionClosedToManager(Long userId, String plantCode,
                                                                      ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EXCEPTION_CLOSED.getCode(), "提醒",
                "异常单" + order.getExceptionNo() + " 已闭环",
                "异常单已完成闭环，操作人：" + createdBy,
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    // ===== 8D 场景 =====

    /**
     * D0 负责人指派通知。
     */
    public static NotificationCreateDTO for8DLeaderAssigned(Long userId, String plantCode,
                                                             ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EIGHT_D_LEADER_ASSIGNED.getCode(), "提醒",
                "整改任务指派：" + order.getExceptionNo(),
                "您被指定为整改负责人，请及时组建团队并推进整改。",
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 8D 团队待审核通知。
     */
    public static NotificationCreateDTO for8DTeamPendingReview(Long userId, String plantCode,
                                                                ExceptionOrder order, String stepDesc, String createdBy) {
        return build(userId, NotificationTypeEnum.EIGHT_D_TEAM_PENDING_REVIEW.getCode(), "提醒",
                "D1 团队待审核：" + order.getExceptionNo(),
                "步骤说明：" + stepDesc,
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 8D 团队审核通过通知。
     */
    public static NotificationCreateDTO for8DTeamApproved(Long userId, String plantCode,
                                                           ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EIGHT_D_TEAM_APPROVED.getCode(), "提醒",
                "D1 团队审核通过：" + order.getExceptionNo(),
                "团队审核已通过，请继续推进后续步骤。",
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 8D 团队审核未通过通知。
     */
    public static NotificationCreateDTO for8DTeamRejected(Long userId, String plantCode,
                                                            ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EIGHT_D_TEAM_PENDING_REVIEW.getCode(), "提醒",
                "D1 团队审核未通过：" + order.getExceptionNo(),
                "团队审核未通过，请重新组建团队。",
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 8D D4 根因分析提交通知（配置驱动接收方）。
     */
    public static NotificationCreateDTO for8DD4Submitted(Long userId, String plantCode,
                                                          ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EIGHT_D_D4_SUBMITTED.getCode(), "提醒",
                "D4 根因分析已提交：" + order.getExceptionNo(),
                "8D 根因分析已完成，请审批。",
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    /**
     * 8D D5 措施方案提交通知（配置驱动接收方）。
     */
    public static NotificationCreateDTO for8DD5Submitted(Long userId, String plantCode,
                                                          ExceptionOrder order, String createdBy) {
        return build(userId, NotificationTypeEnum.EIGHT_D_D5_SUBMITTED.getCode(), "提醒",
                "D5 措施方案已提交：" + order.getExceptionNo(),
                "8D 措施方案已完成，请审批。",
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, createdBy,
                buildExceptionExtraData(order));
    }

    // ===== CAPA 审批场景（配置驱动） =====

    public static NotificationCreateDTO forCapaRootCauseApproved(Long userId, String plantCode,
                                                                  ExceptionOrder order, String operatorName, String comment) {
        return build(userId, NotificationTypeEnum.CAPA_ROOT_CAUSE_APPROVED.getCode(), "提醒",
                "CAPA 根因分析审批通过",
                "异常单【" + order.getExceptionNo() + "】根因分析审批已通过。审批人："
                        + operatorName + "，意见：" + (comment != null ? comment : "无"),
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, operatorName,
                buildExceptionExtraData(order));
    }

    public static NotificationCreateDTO forCapaMeasuresApproved(Long userId, String plantCode,
                                                                 ExceptionOrder order, String operatorName, String comment) {
        return build(userId, NotificationTypeEnum.CAPA_MEASURES_APPROVED.getCode(), "提醒",
                "CAPA 措施审批通过",
                "异常单【" + order.getExceptionNo() + "】措施审批已通过。审批人："
                        + operatorName + "，意见：" + (comment != null ? comment : "无"),
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, operatorName,
                buildExceptionExtraData(order));
    }

    public static NotificationCreateDTO forCapaClosed(Long userId, String plantCode,
                                                       ExceptionOrder order, String operatorName) {
        return build(userId, NotificationTypeEnum.CAPA_CLOSED.getCode(), "提醒",
                "CAPA 闭环通知",
                "异常单【" + order.getExceptionNo() + "】已完成闭环。操作人：" + operatorName,
                BIZ_TYPE_EXCEPTION, order.getId(), plantCode, operatorName,
                buildExceptionExtraData(order));
    }

    // ===== 整改计划通知 =====

    public static NotificationCreateDTO forRectificationPlan(Long userId, String plantCode,
                                                              String exceptionNo, Long exceptionId,
                                                              String planName, String objective,
                                                              String planEndDate, String createdBy) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("exceptionNo", exceptionNo);
        extraData.put("planName", planName);
        extraData.put("objective", objective);

        return build(userId, NotificationTypeEnum.PLAN_OWNER_ASSIGNED.getCode(), "提醒",
                "整改计划指派：" + exceptionNo,
                "您被指定为整改计划负责人（计划：" + planName
                        + "；目标：" + objective
                        + "；截止日期：" + planEndDate + "），请及时推进。",
                BIZ_TYPE_EXCEPTION, exceptionId, plantCode, createdBy, extraData);
    }

    // ===== 改善措施通知 =====

    public static NotificationCreateDTO forImprovementAction(Long userId, String plantCode,
                                                              String exceptionNo, Long exceptionId,
                                                              String actionType, String content,
                                                              String dueDate, String createdBy) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("exceptionNo", exceptionNo);
        extraData.put("actionType", actionType);

        return build(userId, NotificationTypeEnum.ACTION_OWNER_ASSIGNED.getCode(), "提醒",
                "改善措施指派：" + exceptionNo,
                "您被指定为改善措施责任人（类型：" + actionType
                        + "；内容：" + content
                        + "；截止日期：" + dueDate + "），请及时完成。",
                BIZ_TYPE_EXCEPTION, exceptionId, plantCode, createdBy, extraData);
    }

    // ===== 供应商升级通知 =====

    public static NotificationCreateDTO forEscalationOwner(Long userId, String plantCode,
                                                            String escalationReason, Long escalationId,
                                                            String actionPlan, String dueDate, String createdBy) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("escalationReason", escalationReason);

        return build(userId, NotificationTypeEnum.ESCALATION_OWNER_ASSIGNED.getCode(), "提醒",
                "升级措施指派：" + escalationReason,
                "您被指定为升级措施责任人（措施：" + actionPlan
                        + "；截止日期：" + dueDate + "），请及时推进。",
                BIZ_TYPE_ESCALATION, escalationId, plantCode, createdBy, extraData);
    }

    // ===== 抄送通知（CC） =====

    /**
     * 生成抄送版本的 NotificationCreateDTO，在原通知基础上加【抄送】前缀和收件人备注。
     */
    public static NotificationCreateDTO forCc(NotificationCreateDTO original, Long ccUserId,
                                               String targetOwnerName) {
        NotificationCreateDTO cc = new NotificationCreateDTO();
        cc.setUserId(ccUserId);
        cc.setType(original.getType());
        cc.setLevel(original.getLevel());
        cc.setTitle("【抄送】" + original.getTitle());
        cc.setContent(original.getContent() + "（通知对象：" + targetOwnerName + "）");
        cc.setBusinessType(original.getBusinessType());
        cc.setBusinessId(original.getBusinessId());
        cc.setPlantCode(original.getPlantCode());
        cc.setCreatedBy(original.getCreatedBy());
        cc.setExtraData(original.getExtraData());
        cc.setExpireAt(original.getExpireAt());
        return cc;
    }

    // ===== 通用手动创建（管理员） =====

    public static NotificationCreateDTO forManual(Long userId, String plantCode,
                                                   String title, String content,
                                                   String businessType, Long businessId, String createdBy) {
        return build(userId, "MANUAL", "提醒", title, content, businessType, businessId, plantCode, createdBy, null);
    }

    // ===== 内部工具方法 =====

    private static NotificationCreateDTO build(Long userId, String type, String level,
                                                String title, String content,
                                                String businessType, Long businessId,
                                                String plantCode, String createdBy,
                                                Map<String, Object> extraData) {
        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setUserId(userId);
        dto.setType(type);
        dto.setLevel(level);
        dto.setTitle(title);
        dto.setContent(content);
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        dto.setPlantCode(plantCode);
        dto.setCreatedBy(createdBy);
        if (extraData != null && !extraData.isEmpty()) {
            try {
                dto.setExtraData(MAPPER.writeValueAsString(extraData));
            } catch (JsonProcessingException e) {
                log.warn("序列化 extraData 失败", e);
            }
        }
        return dto;
    }

    private static Map<String, Object> buildExceptionExtraData(ExceptionOrder order) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("exceptionNo", order.getExceptionNo());
        extraData.put("supplierName", order.getSupplierName());
        extraData.put("severity", order.getSeverity());
        extraData.put("status", order.getStatus());
        return extraData;
    }
}
