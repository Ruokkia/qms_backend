package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 异常整改阶段级审批配置服务实现（8D 与 CAPA 共用）。
 */
@Service
public class ExceptionApprovalConfigServiceImpl
        extends ServiceImpl<ExceptionApprovalConfigMapper, ExceptionApprovalConfig>
        implements ExceptionApprovalConfigService {

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
    public void saveOrUpdateConfig(ExceptionApprovalConfigDTO dto) {
        String plantCode = currentPlantCode();
        if (dto.getId() != null) {
            ExceptionApprovalConfig existing = getById(dto.getId());
            if (existing == null || (existing.getIsDeleted() != null && existing.getIsDeleted() == 1)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "审批配置不存在：" + dto.getId());
            }
            BeanUtils.copyProperties(dto, existing, "id", "plantCode", "plantName", "isDeleted", "version", "createdAt", "updatedAt");
            existing.setUpdatedAt(LocalDateTime.now());
            updateById(existing);
        } else {
            ExceptionApprovalConfig entity = new ExceptionApprovalConfig();
            BeanUtils.copyProperties(dto, entity);
            entity.setPlantCode(plantCode);
            entity.setPlantName("SZ".equals(plantCode) ? "深圳" : "梅州");
            entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 1);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            save(entity);
        }
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        ExceptionApprovalConfig existing = getById(id);
        if (existing == null || (existing.getIsDeleted() != null && existing.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审批配置不存在：" + id);
        }
        removeById(id);
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
