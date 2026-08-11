package com.kangli.qms.service.exception.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.exception.entity.ExceptionApprovalConfig;
import com.kangli.qms.domain.exception.mapper.ExceptionApprovalConfigMapper;
import com.kangli.qms.domain.exception.vo.ExceptionApprovalConfigVO;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.service.exception.dto.ExceptionApprovalConfigDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 异常整改阶段级审批配置服务实现（8D 与 CAPA 共用）。
 */
@Service
public class ExceptionApprovalConfigServiceImpl
        extends ServiceImpl<ExceptionApprovalConfigMapper, ExceptionApprovalConfig>
        implements ExceptionApprovalConfigService {

    private static final String TABLE_NAME = "exception_approval_config";
    private static final String OP_CREATE = "新增审批配置";
    private static final String OP_UPDATE = "更新审批配置";
    private static final String OP_DELETE = "删除审批配置";

    private final AuditLogMapper auditLogMapper;
    private final SysRoleMapper roleMapper;

    public ExceptionApprovalConfigServiceImpl(AuditLogMapper auditLogMapper, SysRoleMapper roleMapper) {
        this.auditLogMapper = auditLogMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<ExceptionApprovalConfigVO> listByFlow(String processFlow, String plantCode) {
        LambdaQueryWrapper<ExceptionApprovalConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExceptionApprovalConfig::getProcessFlow, processFlow)
                .eq(ExceptionApprovalConfig::getIsDeleted, 0)
                .eq(StringUtils.hasText(plantCode), ExceptionApprovalConfig::getPlantCode, plantCode)
                .orderByAsc(ExceptionApprovalConfig::getStage);
        List<ExceptionApprovalConfig> list = list(wrapper);
        List<ExceptionApprovalConfigVO> vos = new ArrayList<>();
        for (ExceptionApprovalConfig e : list) {
            vos.add(toVO(e));
        }
        return vos;
    }

    @Override
    public ExceptionApprovalConfig resolveConfig(String processFlow, String stage, String plantCode) {
        // 优先取分公司级覆盖，其次默认（is_default=1）
        ExceptionApprovalConfig config = baseMapper.selectDefault(processFlow, stage, plantCode);
        if (config == null) {
            config = baseMapper.selectDefault(processFlow, stage, "SZ");
        }
        return config;
    }

    @Override
    @Transactional
    public void saveOrUpdateConfig(ExceptionApprovalConfigDTO dto, String clientIp) {
        String plantCode = currentPlantCode();
        if (dto.getId() != null) {
            ExceptionApprovalConfig existing = getById(dto.getId());
            if (existing == null || (existing.getIsDeleted() != null && existing.getIsDeleted() == 1)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "审批配置不存在：" + dto.getId());
            }
            // 变更前快照
            ExceptionApprovalConfig before = new ExceptionApprovalConfig();
            BeanUtils.copyProperties(existing, before);
            BeanUtils.copyProperties(dto, existing, "id", "plantCode", "plantName", "isDeleted", "version", "createdAt", "updatedAt");
            existing.setUpdatedAt(LocalDateTime.now());
            updateById(existing);
            writeAuditLog(OP_UPDATE, existing.getId(), before, existing, clientIp);
        } else {
            ExceptionApprovalConfig entity = new ExceptionApprovalConfig();
            BeanUtils.copyProperties(dto, entity);
            entity.setPlantCode(plantCode);
            entity.setPlantName("SZ".equals(plantCode) ? "深圳" : "梅州");
            entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 1);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            save(entity);
            writeAuditLog(OP_CREATE, entity.getId(), null, entity, clientIp);
        }
    }

    @Override
    @Transactional
    public void deleteConfig(Long id, String clientIp) {
        ExceptionApprovalConfig existing = getById(id);
        if (existing == null || (existing.getIsDeleted() != null && existing.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审批配置不存在：" + id);
        }
        removeById(id);
        writeAuditLog(OP_DELETE, existing.getId(), existing, null, clientIp);
    }

    /**
     * 写入审批配置审计日志（与通知配置风格一致，生成面向业务用户的变更摘要）。
     */
    private void writeAuditLog(String operationType, Long recordId,
                               ExceptionApprovalConfig before, ExceptionApprovalConfig after, String clientIp) {
        AuditLog log = new AuditLog();
        log.setTableName(TABLE_NAME);
        log.setRecordId(recordId);
        log.setOperationType(operationType);
        log.setBeforeData(before == null ? null : JSONUtil.toJsonStr(before));
        log.setAfterData(after == null ? null : JSONUtil.toJsonStr(after));
        log.setOperationContent(buildOperationContent(before, after));

        LoginUser currentUser = LoginUserHolder.get();
        log.setOperatorId(currentUser == null ? null : currentUser.getUserId());
        log.setOperatorName(currentUser == null ? "系统" : currentUser.getRealName());
        log.setPlantCode(currentUser != null && currentUser.getPlantCode() != null
                ? currentUser.getPlantCode().name() : "*");
        log.setOperationTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        log.setIpAddress(StringUtils.hasText(clientIp) ? clientIp : null);

        ExceptionApprovalConfig snapshot = after != null ? after : before;
        log.setReason(snapshot != null && snapshot.getProcessFlow() != null
                ? snapshot.getProcessFlow() + " 流程" : null);
        log.setCreatedBy(currentUser == null ? "AUDIT_SYSTEM" : currentUser.getRealName());
        auditLogMapper.insert(log);
    }

    /**
     * 生成可读的变更摘要，如：
     * 审批配置（D3 临时遏制）：需要审批由“不需要”调整为“需要”，审批角色调整为“质量工程师”
     */
    private String buildOperationContent(ExceptionApprovalConfig before, ExceptionApprovalConfig after) {
        Map<String, String> roleNames = roleMapper.selectList(new LambdaQueryWrapper<SysRole>())
                .stream().collect(Collectors.toMap(SysRole::getRoleCode, SysRole::getRoleName, (l, r) -> l));
        ExceptionApprovalConfig ref = after != null ? after : before;
        String stageLabel = ref.getStageName();
        if (stageLabel == null || stageLabel.isBlank()) {
            stageLabel = ref.getStage();
        }
        List<String> changes = new ArrayList<>();
        if (before == null) {
            // 新增
            String need = after.getNeedApproval() != null && after.getNeedApproval() == 1 ? "需要" : "不需要";
            changes.add("需审批：" + need);
            if (after.getNeedApproval() != null && after.getNeedApproval() == 1 && after.getApproverRole() != null) {
                changes.add("审批角色：" + roleNames.getOrDefault(after.getApproverRole(), after.getApproverRole()));
            }
            return "审批配置（" + stageLabel + "）：新增配置，" + String.join("，", changes);
        }
        if (after == null) {
            // 删除
            return "审批配置（" + stageLabel + "）：删除配置";
        }
        // 更新：比对待审批与审批角色
        if (!Objects.equals(before.getNeedApproval(), after.getNeedApproval())) {
            String b = before.getNeedApproval() != null && before.getNeedApproval() == 1 ? "需要" : "不需要";
            String a = after.getNeedApproval() != null && after.getNeedApproval() == 1 ? "需要" : "不需要";
            changes.add("需审批由“" + b + "”调整为“" + a + "”");
        }
        if (!Objects.equals(before.getApproverRole(), after.getApproverRole())) {
            String b = before.getApproverRole() == null ? "未配置" : roleNames.getOrDefault(before.getApproverRole(), before.getApproverRole());
            String a = after.getApproverRole() == null ? "未配置" : roleNames.getOrDefault(after.getApproverRole(), after.getApproverRole());
            changes.add("审批角色由“" + b + "”调整为“" + a + "”");
        }
        return "审批配置（" + stageLabel + "）：" + (changes.isEmpty() ? "未检测到配置变化" : String.join("，", changes));
    }

    private ExceptionApprovalConfigVO toVO(ExceptionApprovalConfig e) {
        ExceptionApprovalConfigVO vo = new ExceptionApprovalConfigVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }

    private String currentPlantCode() {
        try {
            return LoginUserHolder.get().getPlantCode().name();
        } catch (Exception e) {
            return "SZ";
        }
    }
}
