package com.kangli.qms.service.supplier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.supplier.entity.HighRiskMaterial;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.entity.SupplierHighRiskMaterial;
import com.kangli.qms.domain.supplier.mapper.HighRiskMaterialMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierHighRiskMaterialMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.domain.supplier.vo.HighRiskMaterialVO;
import com.kangli.qms.domain.supplier.vo.SupplierHighRiskMaterialVO;
import com.kangli.qms.service.supplier.SupplierHighRiskMaterialService;
import com.kangli.qms.service.supplier.dto.SupplierHighRiskMaterialCreateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商高风险物料关联业务实现。
 */
@Service
public class SupplierHighRiskMaterialServiceImpl implements SupplierHighRiskMaterialService {

    @Resource
    private SupplierHighRiskMaterialMapper linkMapper;
    @Resource
    private HighRiskMaterialMapper materialMapper;
    @Resource
    private SupplierMapper supplierMapper;

    @Override
    public List<HighRiskMaterialVO> listMaterials() {
        return materialMapper.selectList(new LambdaQueryWrapper<HighRiskMaterial>()
                .orderByAsc(HighRiskMaterial::getMaterialCode)).stream()
                .map(this::toMaterialVO).collect(Collectors.toList());
    }

    @Override
    public List<SupplierHighRiskMaterialVO> listBySupplier(Long supplierId) {
        if (supplierId == null) {
            return new ArrayList<>();
        }
        return linkMapper.selectList(new LambdaQueryWrapper<SupplierHighRiskMaterial>()
                .eq(SupplierHighRiskMaterial::getSupplierId, supplierId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierHighRiskMaterialVO add(SupplierHighRiskMaterialCreateDTO dto) {
        if (dto.getSupplierId() == null || dto.getMaterialId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "供应商与高风险物料均不能为空");
        }
        // 去重
        long exists = linkMapper.selectCount(new LambdaQueryWrapper<SupplierHighRiskMaterial>()
                .eq(SupplierHighRiskMaterial::getSupplierId, dto.getSupplierId())
                .eq(SupplierHighRiskMaterial::getMaterialId, dto.getMaterialId()));
        if (exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该供应商已关联此高风险物料");
        }
        Supplier s = supplierMapper.selectById(dto.getSupplierId());
        HighRiskMaterial m = materialMapper.selectById(dto.getMaterialId());
        if (s == null || m == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "供应商或高风险物料不存在");
        }
        LoginUser u = LoginUserHolder.get();
        SupplierHighRiskMaterial link = new SupplierHighRiskMaterial();
        link.setSupplierId(s.getId());
        link.setSupplierCode(s.getSupplierCode());
        link.setSupplierName(s.getSupplierName());
        link.setMaterialId(m.getId());
        link.setMaterialCode(m.getMaterialCode());
        link.setMaterialName(m.getMaterialName());
        link.setExtraRequirement(m.getExtraRequirement());
        link.setPlantCode(u != null && u.getPlantCode() != null ? u.getPlantCode().name() : null);
        link.setPlantName(u != null && u.getPlantCode() != null ? u.getPlantCode().getChineseName() : null);
        link.setCreatedBy(u != null ? (u.getRealName() != null ? u.getRealName() : u.getAccount()) : null);
        linkMapper.insert(link);
        return toVO(link);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        linkMapper.deleteById(id);
    }

    @Override
    public String getExtraRequirement(Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        List<SupplierHighRiskMaterial> links = linkMapper.selectList(
                new LambdaQueryWrapper<SupplierHighRiskMaterial>().eq(SupplierHighRiskMaterial::getSupplierId, supplierId));
        if (CollectionUtils.isEmpty(links)) {
            return null;
        }
        Set<String> reqs = new LinkedHashSet<>();
        for (SupplierHighRiskMaterial l : links) {
            if (l.getExtraRequirement() != null && !l.getExtraRequirement().isBlank()) {
                reqs.add("【" + l.getMaterialName() + "】" + l.getExtraRequirement());
            }
        }
        return reqs.isEmpty() ? null : String.join("\n", reqs);
    }

    private SupplierHighRiskMaterialVO toVO(SupplierHighRiskMaterial l) {
        SupplierHighRiskMaterialVO vo = new SupplierHighRiskMaterialVO();
        vo.setId(l.getId());
        vo.setSupplierId(l.getSupplierId());
        vo.setSupplierName(l.getSupplierName());
        vo.setMaterialId(l.getMaterialId());
        vo.setMaterialCode(l.getMaterialCode());
        vo.setMaterialName(l.getMaterialName());
        vo.setExtraRequirement(l.getExtraRequirement());
        return vo;
    }

    private HighRiskMaterialVO toMaterialVO(HighRiskMaterial m) {
        HighRiskMaterialVO vo = new HighRiskMaterialVO();
        vo.setId(m.getId());
        vo.setMaterialCode(m.getMaterialCode());
        vo.setMaterialName(m.getMaterialName());
        vo.setRiskLevel(m.getRiskLevel());
        vo.setExtraRequirement(m.getExtraRequirement());
        vo.setRemark(m.getRemark());
        return vo;
    }
}
