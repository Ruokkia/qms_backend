package com.kangli.qms.service.fai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSaveRequest;
import com.kangli.qms.service.fai.dto.FaiStandardItemRequest;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.service.fai.FaiStandardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M3 首件检验标准模板 Service 实现。
 */
@Slf4j
@Service
public class FaiStandardServiceImpl implements FaiStandardService {

    private final FaiInspectionStandardMapper standardMapper;
    private final FaiInspectionStandardItemMapper standardItemMapper;
    private final SpcParameterMapper spcParameterMapper;
    private final SpcProcessMapper spcProcessMapper;

    public FaiStandardServiceImpl(FaiInspectionStandardMapper standardMapper,
                                  FaiInspectionStandardItemMapper standardItemMapper,
                                  SpcParameterMapper spcParameterMapper,
                                  SpcProcessMapper spcProcessMapper) {
        this.standardMapper = standardMapper;
        this.standardItemMapper = standardItemMapper;
        this.spcParameterMapper = spcParameterMapper;
        this.spcProcessMapper = spcProcessMapper;
    }

    @Override
    public List<FaiStandardResponse> list(String plantCode) {
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .orderByDesc(FaiInspectionStandard::getCreatedAt);
        List<FaiInspectionStandard> standards = standardMapper.selectList(wrapper);
        return standards.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public FaiStandardResponse latestActive(String materialCode, String processName, String plantCode) {
        if (!StringUtils.hasText(materialCode) || !StringUtils.hasText(processName)) {
            return null;
        }
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getMaterialCode, materialCode)
                .eq(FaiInspectionStandard::getProcessName, processName)
                .eq(FaiInspectionStandard::getIsActive, "是")
                .orderByDesc(FaiInspectionStandard::getStdVersion)
                .last("LIMIT 1");
        FaiInspectionStandard standard = standardMapper.selectOne(wrapper);
        if (standard == null) {
            return null;
        }
        return toResponse(standard);
    }

    @Override
    public FaiStandardResponse getStandard(Long id) {
        FaiInspectionStandard standard = standardMapper.selectById(id);
        if (standard == null) {
            return null;
        }
        return toResponse(standard);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStandard(FaiStandardSaveRequest req, LoginUser loginUser) {
        String plantCode = loginUser.getPlantCode().name();
        validateAndResolveSpcItems(req, plantCode);
        String plantName = loginUser.getPlantCode().getChineseName();

        FaiInspectionStandard standard = new FaiInspectionStandard();
        BeanUtils.copyProperties(req, standard);
        standard.setId(null);
        standard.setPlantCode(plantCode);
        standard.setPlantName(plantName);
        standard.setCreatedBy(loginUser.getRealName());
        standard.setUpdatedBy(loginUser.getRealName());
        standard.setStdVersion(nextVersion(req.getMaterialCode(), req.getProcessName(), plantCode));
        if (!StringUtils.hasText(req.getIsActive())) {
            standard.setIsActive("是");
        }
        standardMapper.insert(standard);

        saveItems(standard.getId(), req.getItems(), plantCode, plantName, loginUser.getRealName());

        // 仅允许一个激活标准：新标准激活时关闭同 物料+工序 的其它激活标准
        if ("是".equals(standard.getIsActive())) {
            deactivateOthers(standard.getId(), req.getMaterialCode(), req.getProcessName(), plantCode, loginUser.getRealName());
        }
        return standard.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStandard(Long id, FaiStandardSaveRequest req, LoginUser loginUser) {
        FaiInspectionStandard standard = standardMapper.selectById(id);
        if (standard == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标准模板不存在");
        }
        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(standard.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改其它分公司的标准");
        }
        validateAndResolveSpcItems(req, plantCode);

        BeanUtils.copyProperties(req, standard, "id", "plantCode", "plantName", "stdVersion", "createdBy", "createdAt");
        standard.setUpdatedBy(loginUser.getRealName());
        standardMapper.updateById(standard);

        // 覆盖式更新参数项：先逻辑删除旧项，再插入新项
        LambdaUpdateWrapper<FaiInspectionStandardItem> delWrapper = new LambdaUpdateWrapper<>();
        delWrapper.eq(FaiInspectionStandardItem::getStandardId, id)
                .set(FaiInspectionStandardItem::getIsDeleted, (short) 1);
        standardItemMapper.update(null, delWrapper);

        saveItems(id, req.getItems(), plantCode, standard.getPlantName(), loginUser.getRealName());

        if ("是".equals(standard.getIsActive())) {
            deactivateOthers(id, req.getMaterialCode(), req.getProcessName(), plantCode, loginUser.getRealName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStandard(Long id, LoginUser loginUser) {
        FaiInspectionStandard standard = standardMapper.selectById(id);
        if (standard == null) {
            return;
        }
        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(standard.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除其它分公司的标准");
        }
        // 逻辑删除参数项
        LambdaUpdateWrapper<FaiInspectionStandardItem> delWrapper = new LambdaUpdateWrapper<>();
        delWrapper.eq(FaiInspectionStandardItem::getStandardId, id)
                .set(FaiInspectionStandardItem::getIsDeleted, (short) 1);
        standardItemMapper.update(null, delWrapper);
        // 逻辑删除主表
        LambdaUpdateWrapper<FaiInspectionStandard> stdWrapper = new LambdaUpdateWrapper<>();
        stdWrapper.eq(FaiInspectionStandard::getId, id)
                .set(FaiInspectionStandard::getIsDeleted, (short) 1)
                .set(FaiInspectionStandard::getUpdatedBy, loginUser.getRealName());
        standardMapper.update(null, stdWrapper);
    }

    private void saveItems(Long standardId, List<FaiStandardItemRequest> items, String plantCode, String plantName, String operator) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<FaiInspectionStandardItem> entities = items.stream().map(it -> {
            FaiInspectionStandardItem e = new FaiInspectionStandardItem();
            e.setId(null);
            e.setStandardId(standardId);
            e.setParamName(it.getParamName());
            e.setParamCode(it.getParamCode());
            e.setParamCategory(it.getParamCategory());
            e.setStandardValue(it.getStandardValue());
            e.setUpperLimit(it.getUpperLimit());
            e.setLowerLimit(it.getLowerLimit());
            e.setUnit(it.getUnit());
            e.setIsRequired(it.getIsRequired());
            e.setSortOrder(it.getSortOrder());
            e.setSpcEnabled(StringUtils.hasText(it.getSpcEnabled()) ? it.getSpcEnabled() : "否");
            e.setSpcParameterId(it.getSpcParameterId());
            e.setPlantCode(plantCode);
            e.setPlantName(plantName);
            e.setCreatedBy(operator);
            e.setUpdatedBy(operator);
            return e;
        }).collect(Collectors.toList());
        for (FaiInspectionStandardItem e : entities) {
            standardItemMapper.insert(e);
        }
    }

    private int nextVersion(String materialCode, String processName, String plantCode) {
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getMaterialCode, materialCode)
                .eq(FaiInspectionStandard::getProcessName, processName)
                .select(FaiInspectionStandard::getStdVersion)
                .orderByDesc(FaiInspectionStandard::getStdVersion)
                .last("LIMIT 1");
        FaiInspectionStandard latest = standardMapper.selectOne(wrapper);
        return latest == null ? 1 : (latest.getStdVersion() == null ? 1 : latest.getStdVersion() + 1);
    }

    private void deactivateOthers(Long selfId, String materialCode, String processName, String plantCode, String operator) {
        LambdaUpdateWrapper<FaiInspectionStandard> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getMaterialCode, materialCode)
                .eq(FaiInspectionStandard::getProcessName, processName)
                .eq(FaiInspectionStandard::getIsActive, "是")
                .ne(FaiInspectionStandard::getId, selfId)
                .set(FaiInspectionStandard::getIsActive, "否")
                .set(FaiInspectionStandard::getUpdatedBy, operator);
        standardMapper.update(null, wrapper);
    }

    private void validateAndResolveSpcItems(FaiStandardSaveRequest req, String plantCode) {
        if (!StringUtils.hasText(req.getMaterialCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "物料代码不能为空");
        }
        if (!StringUtils.hasText(req.getProcessName())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工序不能为空");
        }
        if (!StringUtils.hasText(req.getProcessCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工序编码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "标准至少包含一项参数");
        }
        for (FaiStandardItemRequest it : req.getItems()) {
            if (!StringUtils.hasText(it.getParamName())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "参数名称不能为空");
            }
            if (!StringUtils.hasText(it.getParamCategory())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "参数类别(AQL/关键尺寸/性能参数)不能为空");
            }
            if (!StringUtils.hasText(it.getIsRequired())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "是否必检不能为空");
            }
            if (!"是".equals(it.getSpcEnabled())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "首件检验项目必须绑定SPC参数");
            }
            if (it.getSpcParameterId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "纳入SPC的首件项目必须选择SPC参数");
            }
            SpcParameter param = spcParameterMapper.selectById(it.getSpcParameterId());
            if (param == null || param.getIsDeleted() == 1 || !plantCode.equals(param.getPlantCode())
                    || !"是".equals(param.getIsActive())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "选择的SPC参数不存在、未启用或不属于当前工厂");
            }
            SpcProcess process = spcProcessMapper.selectById(param.getProcessId());
            if (process == null || process.getIsDeleted() == 1
                    || !plantCode.equals(process.getPlantCode())
                    || !process.getProcessCode().equals(req.getProcessCode())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "选择的SPC参数不属于当前首件工序");
            }
            // 不信任前端传入的数值标准，强制从 SPC 参数复制。
            it.setParamName(param.getParamName());
            it.setParamCode(param.getParamCode());
            it.setUnit(param.getUnit());
            it.setStandardValue(param.getTargetValue() == null ? null
                    : param.getTargetValue().stripTrailingZeros().toPlainString());
            it.setUpperLimit(param.getUpperSpecLimit());
            it.setLowerLimit(param.getLowerSpecLimit());
        }
    }

    private FaiStandardResponse toResponse(FaiInspectionStandard standard) {
        FaiStandardResponse resp = new FaiStandardResponse();
        BeanUtils.copyProperties(standard, resp);
        LambdaQueryWrapper<FaiInspectionStandardItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(FaiInspectionStandardItem::getStandardId, standard.getId())
                .eq(FaiInspectionStandardItem::getIsDeleted, 0)
                .orderByAsc(FaiInspectionStandardItem::getSortOrder);
        resp.setItems(standardItemMapper.selectList(itemWrapper));
        return resp;
    }
}
