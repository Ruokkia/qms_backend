package com.kangli.qms.service.incoming.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.domain.incoming.mapper.CriticalMaterialBindingMapper;
import com.kangli.qms.service.incoming.CriticalMaterialBindingService;
import com.kangli.qms.service.incoming.dto.CriticalMaterialBindingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M1-3 关键物料绑定 Service 实现。
 * <p>补全自然键 (workOrderNo, productBarcode, materialBarcode, processCode) 唯一性校验，
 * materialBarcode 为空时回退 materialCode 判定；统一返回响应 DTO（红线 #2）。</p>
 */
@Slf4j
@Service
public class CriticalMaterialBindingServiceImpl
        extends ServiceImpl<CriticalMaterialBindingMapper, CriticalMaterialBinding>
        implements CriticalMaterialBindingService {

    @Override
    public CriticalMaterialBindingResponse detail(Long id) {
        CriticalMaterialBinding record = getById(id);
        if (record == null || isDeleted(record)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return toResponse(record);
    }

    @Override
    public PageResult<CriticalMaterialBindingResponse> page(Page<CriticalMaterialBinding> page,
                                                             LambdaQueryWrapper<CriticalMaterialBinding> wrapper) {
        Page<CriticalMaterialBinding> result = super.page(page, wrapper);
        List<CriticalMaterialBindingResponse> list = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CriticalMaterialBindingResponse create(CriticalMaterialBinding record, LoginUser loginUser) {
        assertNaturalKeyUnique(record, null);
        if (!save(record)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "保存失败");
        }
        return toResponse(getById(record.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CriticalMaterialBindingResponse update(Long id, CriticalMaterialBinding record, LoginUser loginUser) {
        CriticalMaterialBinding old = getById(id);
        if (old == null || isDeleted(old)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        assertNaturalKeyUnique(record, id);
        record.setId(id);
        record.setVersion(old.getVersion());
        record.setPlantCode(old.getPlantCode());
        record.setPlantName(old.getPlantName());
        record.setCreatedBy(old.getCreatedBy());
        record.setCreatedAt(old.getCreatedAt());
        record.setIsDeleted(old.getIsDeleted());
        if (!updateById(record)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "更新失败");
        }
        return toResponse(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CriticalMaterialBinding old = getById(id);
        if (old == null || isDeleted(old)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (!removeById(id)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "删除失败");
        }
    }

    // ===== 自然键唯一性校验 =====
    /**
     * 自然键 (workOrderNo, productBarcode, materialBarcode, processCode) 唯一性校验。
     * <p>看板绑定场景（成品数据看板选中子项绑定）只提供条码，工单号/工序代码可为空，
     * 因此仅强制 产品SN + 物料条码/代码 必填；工单号、工序代码有值则等值匹配，
     * 无值则匹配 IS NULL，保持自然键语义完整。</p>
     */
    private void assertNaturalKeyUnique(CriticalMaterialBinding record, Long excludeId) {
        if (!StringUtils.hasText(record.getProductBarcode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品SN不能为空");
        }
        String materialKey = StringUtils.hasText(record.getMaterialBarcode())
                ? record.getMaterialBarcode() : record.getMaterialCode();
        if (!StringUtils.hasText(materialKey)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "物料条码与物料代码不能同时为空");
        }

        LambdaQueryWrapper<CriticalMaterialBinding> w = new LambdaQueryWrapper<>();
        w.eq(CriticalMaterialBinding::getProductBarcode, record.getProductBarcode());
        w.eq(CriticalMaterialBinding::getIsDeleted, 0);
        // 工单号可选：有值等值匹配，无值匹配 IS NULL
        if (StringUtils.hasText(record.getWorkOrderNo())) {
            w.eq(CriticalMaterialBinding::getWorkOrderNo, record.getWorkOrderNo());
        } else {
            w.isNull(CriticalMaterialBinding::getWorkOrderNo);
        }
        // 工序代码可选：有值等值匹配，无值匹配 IS NULL
        if (StringUtils.hasText(record.getProcessCode())) {
            w.eq(CriticalMaterialBinding::getProcessCode, record.getProcessCode());
        } else {
            w.isNull(CriticalMaterialBinding::getProcessCode);
        }
        if (StringUtils.hasText(record.getMaterialBarcode())) {
            w.eq(CriticalMaterialBinding::getMaterialBarcode, record.getMaterialBarcode());
        } else {
            w.eq(CriticalMaterialBinding::getMaterialCode, record.getMaterialCode());
        }
        if (excludeId != null) {
            w.ne(CriticalMaterialBinding::getId, excludeId);
        }
        if (count(w) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "同一产品下该物料已绑定，不可重复绑定");
        }
    }

    private boolean isDeleted(CriticalMaterialBinding e) {
        return e.getIsDeleted() != null && e.getIsDeleted() == 1;
    }

    private CriticalMaterialBindingResponse toResponse(CriticalMaterialBinding e) {
        CriticalMaterialBindingResponse r = new CriticalMaterialBindingResponse();
        BeanUtils.copyProperties(e, r);
        return r;
    }
}
