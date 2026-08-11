package com.kangli.qms.service.fai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.fai.dto.FaiStandardProcessVO;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSaveRequest;
import com.kangli.qms.service.fai.dto.FaiStandardItemRequest;
import com.kangli.qms.service.fai.dto.FaiStandardHistoryResponse;
import com.kangli.qms.service.fai.dto.FaiStandardApprovalResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSpcParamVO;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardHistory;
import com.kangli.qms.domain.fai.entity.FaiStandardApproval;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardHistoryMapper;
import com.kangli.qms.domain.fai.mapper.FaiStandardApprovalMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.service.fai.FaiStandardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M3 首件检验标准模板 Service 实现。
 */
@Slf4j
@Service
public class FaiStandardServiceImpl implements FaiStandardService {

    private final FaiInspectionStandardMapper standardMapper;
    private final FaiInspectionStandardItemMapper standardItemMapper;
    private final FaiInspectionStandardHistoryMapper historyMapper;
    private final FaiStandardApprovalMapper approvalMapper;
    private final SpcParameterMapper spcParameterMapper;
    private final SpcProcessMapper spcProcessMapper;
    private final ObjectMapper objectMapper;

    public FaiStandardServiceImpl(FaiInspectionStandardMapper standardMapper,
                                  FaiInspectionStandardItemMapper standardItemMapper,
                                  FaiInspectionStandardHistoryMapper historyMapper,
                                  FaiStandardApprovalMapper approvalMapper,
                                  SpcParameterMapper spcParameterMapper,
                                  SpcProcessMapper spcProcessMapper) {
        this.standardMapper = standardMapper;
        this.standardItemMapper = standardItemMapper;
        this.historyMapper = historyMapper;
        this.approvalMapper = approvalMapper;
        this.spcParameterMapper = spcParameterMapper;
        this.spcProcessMapper = spcProcessMapper;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
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
    public List<FaiStandardResponse> listByItemType(String plantCode, String itemType) {
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(StringUtils.hasText(itemType), FaiInspectionStandard::getItemType, itemType)
                .orderByDesc(FaiInspectionStandard::getCreatedAt);
        List<FaiInspectionStandard> standards = standardMapper.selectList(wrapper);
        return standards.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public FaiStandardResponse latestActive(String materialCode, String processName, String plantCode) {
        return latestActive(materialCode, null, processName, plantCode);
    }

    @Override
    public FaiStandardResponse latestActive(String itemCode, String itemType, String processName, String plantCode) {
        if (!StringUtils.hasText(itemCode) || !StringUtils.hasText(processName)) {
            return null;
        }
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getItemCode, itemCode)
                .eq(StringUtils.hasText(itemType), FaiInspectionStandard::getItemType, itemType)
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
    public FaiStandardResponse getStandard(Long id, String plantCode) {
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getId, id)
                .eq(FaiInspectionStandard::getPlantCode, plantCode);
        FaiInspectionStandard standard = standardMapper.selectOne(wrapper);
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
        // BeanUtils 不支持 String → LocalDate 转换，手动处理
        if (StringUtils.hasText(req.getEffectiveDate())) {
            standard.setEffectiveDate(java.time.LocalDate.parse(req.getEffectiveDate()));
        }
        standard.setId(null);
        standard.setPlantCode(plantCode);
        standard.setPlantName(plantName);
        standard.setCreatedBy(loginUser.getRealName());
        standard.setUpdatedBy(loginUser.getRealName());
        standard.setStdVersion(nextVersion(req.getMaterialCode(), req.getItemType(), req.getItemCode(), req.getProcessName(), plantCode));
        if (!StringUtils.hasText(req.getIsActive())) {
            standard.setIsActive("是");
        }
        standardMapper.insert(standard);

        saveItems(standard.getId(), req.getItems(), plantCode, plantName, loginUser.getRealName());

        // 仅允许一个激活标准：新标准激活时关闭同 物料+工序+分类 的其它激活标准
        if ("是".equals(standard.getIsActive())) {
            deactivateOthers(standard.getId(), req.getMaterialCode(), req.getItemType(), req.getItemCode(), req.getProcessName(), plantCode, loginUser.getRealName());
        }

        // P0: 记录创建历史
        String afterSnapshot = buildSnapshot(standard.getId());
        recordHistory(standard.getId(), "CREATE", null, afterSnapshot, null, loginUser);

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

        // P0: 保存变更前快照
        String beforeSnapshot = buildSnapshot(id);

        validateAndResolveSpcItems(req, plantCode);

        BeanUtils.copyProperties(req, standard, "id", "plantCode", "plantName", "stdVersion", "createdBy", "createdAt");
        // BeanUtils 不支持 String → LocalDate 转换，手动处理
        if (StringUtils.hasText(req.getEffectiveDate())) {
            standard.setEffectiveDate(java.time.LocalDate.parse(req.getEffectiveDate()));
        } else {
            standard.setEffectiveDate(null);
        }
        standard.setUpdatedBy(loginUser.getRealName());
        standardMapper.updateById(standard);

        // 覆盖式更新参数项：先逻辑删除旧项，再插入新项
        LambdaUpdateWrapper<FaiInspectionStandardItem> delWrapper = new LambdaUpdateWrapper<>();
        delWrapper.eq(FaiInspectionStandardItem::getStandardId, id)
                .set(FaiInspectionStandardItem::getIsDeleted, (short) 1);
        standardItemMapper.update(null, delWrapper);

        saveItems(id, req.getItems(), plantCode, standard.getPlantName(), loginUser.getRealName());

        if ("是".equals(standard.getIsActive())) {
            deactivateOthers(id, req.getMaterialCode(), req.getItemType(), req.getItemCode(), req.getProcessName(), plantCode, loginUser.getRealName());
        }

        // P0: 记录编辑历史
        String afterSnapshot = buildSnapshot(id);
        String reason = StringUtils.hasText(req.getRemark()) ? req.getRemark() : null;
        recordHistory(id, "UPDATE", beforeSnapshot, afterSnapshot, reason, loginUser);
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

        // P0: 保存删除前快照
        String beforeSnapshot = buildSnapshot(id);

        // 逻辑删除参数项（@TableLogic 自动转 DELETE 为 UPDATE SET is_deleted=1）
        LambdaQueryWrapper<FaiInspectionStandardItem> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.eq(FaiInspectionStandardItem::getStandardId, id);
        standardItemMapper.delete(itemQuery);

        // 逻辑删除主表（@TableLogic 自动转 DELETE 为 UPDATE SET is_deleted=1, version=version+1）
        standardMapper.deleteById(id);

        // P0: 记录删除历史
        recordHistory(id, "DELETE", beforeSnapshot, null, null, loginUser);
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
            e.setTargetValue(it.getTargetValue());
            e.setSubgroupSize(it.getSubgroupSize());
            e.setChartType(it.getChartType());
            e.setUnit(it.getUnit());
            e.setIsRequired(it.getIsRequired());
            e.setSortOrder(it.getSortOrder());
            // spcEnabled 自动计算：有 spcParameterId 即 '是'，否则 '否'
            e.setSpcEnabled(it.getSpcParameterId() != null ? "是" : "否");
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

    private int nextVersion(String materialCode, String itemType, String itemCode, String processName, String plantCode) {
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(itemType), FaiInspectionStandard::getItemType, itemType)
                .eq(StringUtils.hasText(itemCode), FaiInspectionStandard::getItemCode, itemCode)
                .eq(FaiInspectionStandard::getProcessName, processName)
                .select(FaiInspectionStandard::getStdVersion)
                .orderByDesc(FaiInspectionStandard::getStdVersion)
                .last("LIMIT 1");
        FaiInspectionStandard latest = standardMapper.selectOne(wrapper);
        return latest == null ? 1 : (latest.getStdVersion() == null ? 1 : latest.getStdVersion() + 1);
    }

    private void deactivateOthers(Long selfId, String materialCode, String itemType, String itemCode, String processName, String plantCode, String operator) {
        LambdaUpdateWrapper<FaiInspectionStandard> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(itemType), FaiInspectionStandard::getItemType, itemType)
                .eq(StringUtils.hasText(itemCode), FaiInspectionStandard::getItemCode, itemCode)
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
            // paramName 会由 SPC 参数选择时自动回填，不在此处校验空值
            if (!StringUtils.hasText(it.getIsRequired())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "是否必检不能为空");
            }
            if (it.getSpcParameterId() != null) {
                // SPC 绑定模式：必须选择有效的 SPC 参数
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
                // 仅从 SPC 参数字典回填编码/名称/单位（只读回显）。
                // 目标值/USL/LSL/子组n/控制图类型属于物料-工序专属标准，
                // 由前端在本页面表格行编辑人工填写，不从参数字典级联。
                it.setParamName(param.getParamName());
                it.setParamCode(param.getParamCode());
                it.setUnit(param.getUnit());
            }
            // 不强制 paramCategory：前端表格已不包含该列，可选填
        }
    }

    private FaiStandardResponse toResponse(FaiInspectionStandard standard) {
        FaiStandardResponse resp = new FaiStandardResponse();
        BeanUtils.copyProperties(standard, resp);
        // P3：补充复审和统计字段
        resp.setLastReviewedAt(standard.getLastReviewedAt());
        resp.setReviewIntervalDays(standard.getReviewIntervalDays());
        resp.setUsageCount(standard.getUsageCount());
        resp.setLastUsedAt(standard.getLastUsedAt());
        LambdaQueryWrapper<FaiInspectionStandardItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(FaiInspectionStandardItem::getStandardId, standard.getId())
                .eq(FaiInspectionStandardItem::getIsDeleted, 0)
                .orderByAsc(FaiInspectionStandardItem::getSortOrder);
        resp.setItems(standardItemMapper.selectList(itemWrapper));
        return resp;
    }

    // ========== P0: 变更历史追溯 ==========

    @Override
    public List<FaiStandardHistoryResponse> getHistory(Long standardId, String plantCode) {
        LambdaQueryWrapper<FaiInspectionStandardHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandardHistory::getStandardId, standardId)
                .eq(FaiInspectionStandardHistory::getPlantCode, plantCode)
                .orderByDesc(FaiInspectionStandardHistory::getChangedAt);
        List<FaiInspectionStandardHistory> list = historyMapper.selectList(wrapper);
        return list.stream().map(h -> {
            FaiStandardHistoryResponse resp = new FaiStandardHistoryResponse();
            BeanUtils.copyProperties(h, resp, "beforeSnapshot", "afterSnapshot");
            // 将 JsonNode 转为 String
            if (h.getBeforeSnapshot() != null) resp.setBeforeSnapshot(h.getBeforeSnapshot().toString());
            if (h.getAfterSnapshot() != null) resp.setAfterSnapshot(h.getAfterSnapshot().toString());
            return resp;
        }).collect(Collectors.toList());
    }

    /**
     * 构建标准的完整 JSON 快照（含主表 + 当前有效参数项列表）。
     */
    private String buildSnapshot(Long standardId) {
        try {
            FaiInspectionStandard standard = standardMapper.selectById(standardId);
            if (standard == null) return null;
            LambdaQueryWrapper<FaiInspectionStandardItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(FaiInspectionStandardItem::getStandardId, standardId)
                    .eq(FaiInspectionStandardItem::getIsDeleted, 0)
                    .orderByAsc(FaiInspectionStandardItem::getSortOrder);
            List<FaiInspectionStandardItem> items = standardItemMapper.selectList(itemWrapper);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("standard", standard);
            snapshot.put("items", items);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("buildSnapshot failed for standardId={}: {}", standardId, e.getMessage());
            return null;
        }
    }

    /**
     * 写入历史记录并生成差异摘要。
     */
    private void recordHistory(Long standardId, String changeType,
                               String beforeSnapshot, String afterSnapshot,
                               String reason, LoginUser loginUser) {
        try {
            FaiInspectionStandardHistory history = new FaiInspectionStandardHistory();
            history.setStandardId(standardId);
            history.setChangeType(changeType);
            history.setChangeReason(reason);
            // 将 JSON 字符串转为 JsonNode 存储
            if (beforeSnapshot != null) {
                history.setBeforeSnapshot(objectMapper.readTree(beforeSnapshot));
            }
            if (afterSnapshot != null) {
                history.setAfterSnapshot(objectMapper.readTree(afterSnapshot));
            }
            history.setDiffSummary(generateDiffSummary(changeType, beforeSnapshot, afterSnapshot));
            history.setChangedBy(loginUser.getRealName());
            history.setChangedAt(LocalDateTime.now());
            history.setPlantCode(loginUser.getPlantCode().name());
            history.setPlantName(loginUser.getPlantCode().getChineseName());
            historyMapper.insert(history);
        } catch (Exception e) {
            log.error("recordHistory failed for standardId={}, changeType={}: {}", standardId, changeType, e.getMessage(), e);
        }
    }

    /**
     * 自动生成差异摘要文本。
     */
    @SuppressWarnings("unchecked")
    private String generateDiffSummary(String changeType, String beforeJson, String afterJson) {
        if (beforeJson == null && afterJson == null) return "无变更内容";
        try {
            if ("CREATE".equals(changeType)) {
                Map<String, Object> after = objectMapper.readValue(afterJson, Map.class);
                Map<String, Object> std = (Map<String, Object>) after.get("standard");
                List<?> items = (List<?>) after.get("items");
                return String.format("新建检验标准「%s」- 工序：%s，版本 v%s，共 %d 项参数",
                        std.getOrDefault("materialName", std.getOrDefault("materialCode", "-")),
                        std.getOrDefault("processName", "-"),
                        std.getOrDefault("stdVersion", "1"),
                        items != null ? items.size() : 0);
            }
            if ("DELETE".equals(changeType)) {
                Map<String, Object> before = objectMapper.readValue(beforeJson, Map.class);
                Map<String, Object> std = (Map<String, Object>) before.get("standard");
                List<?> items = (List<?>) before.get("items");
                return String.format("删除检验标准「%s」- 工序：%s，版本 v%s（原有 %d 项参数）",
                        std.getOrDefault("materialName", std.getOrDefault("materialCode", "-")),
                        std.getOrDefault("processName", "-"),
                        std.getOrDefault("stdVersion", "1"),
                        items != null ? items.size() : 0);
            }
            // UPDATE —— 逐字段对比
            Map<String, Object> before = objectMapper.readValue(beforeJson, Map.class);
            Map<String, Object> after = objectMapper.readValue(afterJson, Map.class);
            Map<String, Object> stdBefore = (Map<String, Object>) before.get("standard");
            Map<String, Object> stdAfter = (Map<String, Object>) after.get("standard");
            List<Map<String, Object>> itemsBefore = (List<Map<String, Object>>) before.get("items");
            List<Map<String, Object>> itemsAfter = (List<Map<String, Object>>) after.get("items");

            StringBuilder sb = new StringBuilder("编辑检验标准");

            // 主表字段对比
            String[][] stdFields = {
                    {"materialCode", "物料编码"}, {"materialName", "物料名称"},
                    {"itemType", "分类"}, {"itemCode", "产品/物料代码"}, {"itemName", "产品/物料名称"},
                    {"processName", "工序"}, {"processCode", "工序编码"},
                    {"isActive", "激活状态"}, {"remark", "备注"}
            };
            List<String> stdChanges = new ArrayList<>();
            for (String[] f : stdFields) {
                Object vBefore = stdBefore.get(f[0]);
                Object vAfter = stdAfter.get(f[0]);
                if (!Objects.equals(vBefore, vAfter)) {
                    stdChanges.add(String.format("%s 从「%s」变更为「%s」", f[1], vBefore, vAfter));
                }
            }
            if (!stdChanges.isEmpty()) {
                sb.append("：").append(String.join("；", stdChanges));
            }

            // 参数项对比
            int sizeBefore = itemsBefore != null ? itemsBefore.size() : 0;
            int sizeAfter = itemsAfter != null ? itemsAfter.size() : 0;
            if (sizeBefore != sizeAfter) {
                sb.append("；参数项从 ").append(sizeBefore).append(" 项 变更为 ").append(sizeAfter).append(" 项");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("generateDiffSummary failed: {}", e.getMessage());
            return changeType + " 操作";
        }
    }

    // ========== P1：轻量级审批工作流 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitForApproval(FaiStandardSaveRequest req, LoginUser loginUser) {
        String plantCode = loginUser.getPlantCode().name();
        String plantName = loginUser.getPlantCode().getChineseName();

        // 确定审批类型
        String approvalType;
        if (req.getId() == null) {
            approvalType = "CREATE";
        } else {
            FaiInspectionStandard existing = standardMapper.selectById(req.getId());
            if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "标准模板不存在");
            if (!plantCode.equals(existing.getPlantCode()))
                throw new BusinessException(ResultCode.FORBIDDEN, "无权跨厂区操作");
            approvalType = "UPDATE";
        }

        // 序列化请求数据为 JSON 快照
        com.fasterxml.jackson.databind.JsonNode requestNode;
        try {
            requestNode = objectMapper.readTree(objectMapper.writeValueAsString(req));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "序列化审批数据失败");
        }

        FaiStandardApproval approval = new FaiStandardApproval();
        approval.setStandardId(req.getId());
        approval.setApprovalType(approvalType);
        approval.setRequestData(requestNode);
        approval.setRequester(loginUser.getRealName());
        approval.setRequestedAt(LocalDateTime.now());
        approval.setApprovalStatus("PENDING");
        approval.setApplied(false);
        approval.setRemark(req.getRemark());
        approval.setPlantCode(plantCode);
        approval.setPlantName(plantName);
        approvalMapper.insert(approval);

        log.info("审批已提交: id={}, type={}, requester={}", approval.getId(), approvalType, loginUser.getRealName());
        return approval.getId();
    }

    @Override
    public List<FaiStandardApprovalResponse> getPendingApprovals(LoginUser loginUser) {
        String plantCode = loginUser.getPlantCode().name();
        LambdaQueryWrapper<FaiStandardApproval> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiStandardApproval::getApprovalStatus, "PENDING")
                .eq(FaiStandardApproval::getPlantCode, plantCode)
                .orderByDesc(FaiStandardApproval::getRequestedAt);
        List<FaiStandardApproval> list = approvalMapper.selectList(wrapper);
        return list.stream().map(a -> {
            FaiStandardApprovalResponse resp = new FaiStandardApprovalResponse();
            BeanUtils.copyProperties(a, resp, "requestData");
            if (a.getRequestData() != null) resp.setRequestData(a.getRequestData().toString());
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveStandard(Long approvalId, LoginUser loginUser) {
        FaiStandardApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) throw new BusinessException(ResultCode.NOT_FOUND, "审批记录不存在");
        if (!"PENDING".equals(approval.getApprovalStatus()))
            throw new BusinessException(ResultCode.BAD_REQUEST, "该审批已处理");

        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(approval.getPlantCode()))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权审批其他分公司的申请");
        // four-eyes 原则：禁止审批自己提交的申请
        if (loginUser.getRealName().equals(approval.getRequester()))
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能审批自己提交的申请");

        // 解析请求数据并执行实际变更
        try {
            String json = approval.getRequestData().toString();
            FaiStandardSaveRequest req = objectMapper.readValue(json, FaiStandardSaveRequest.class);

            if ("CREATE".equals(approval.getApprovalType())) {
                req.setId(null);
                createStandard(req, loginUser);
            } else if ("UPDATE".equals(approval.getApprovalType())) {
                updateStandard(approval.getStandardId(), req, loginUser);
            } else {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的审批类型: " + approval.getApprovalType());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("执行审批变更失败: approvalId={}", approvalId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "执行审批变更失败: " + e.getMessage());
        }

        // 更新审批记录（@Version 乐观锁，并发时后者 affected rows = 0）
        approval.setApprovalStatus("APPROVED");
        approval.setApprover(loginUser.getRealName());
        approval.setApprovedAt(LocalDateTime.now());
        approval.setApplied(true);
        if (approvalMapper.updateById(approval) == 0)
            throw new BusinessException(ResultCode.VERSION_CONFLICT, "审批已被其他审批人处理，请刷新后重试");

        log.info("审批已通过: id={}, approver={}", approvalId, loginUser.getRealName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectStandard(Long approvalId, String rejectReason, LoginUser loginUser) {
        FaiStandardApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) throw new BusinessException(ResultCode.NOT_FOUND, "审批记录不存在");
        if (!"PENDING".equals(approval.getApprovalStatus()))
            throw new BusinessException(ResultCode.BAD_REQUEST, "该审批已处理");

        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(approval.getPlantCode()))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权审批其他分公司的申请");
        // four-eyes 原则：禁止审批自己提交的申请
        if (loginUser.getRealName().equals(approval.getRequester()))
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能审批自己提交的申请");

        approval.setApprovalStatus("REJECTED");
        approval.setRejectReason(rejectReason);
        approval.setApprover(loginUser.getRealName());
        approval.setApprovedAt(LocalDateTime.now());
        approval.setApplied(false);
        if (approvalMapper.updateById(approval) == 0)
            throw new BusinessException(ResultCode.VERSION_CONFLICT, "审批已被其他审批人处理，请刷新后重试");

        log.info("审批已驳回: id={}, reason={}", approvalId, rejectReason);
    }

    // ========== P2：版本管理规范化 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNewVersion(Long standardId, FaiStandardSaveRequest req, LoginUser loginUser) {
        FaiInspectionStandard oldStandard = standardMapper.selectById(standardId);
        if (oldStandard == null) throw new BusinessException(ResultCode.NOT_FOUND, "标准模板不存在");

        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(oldStandard.getPlantCode()))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他厂区的标准");

        validateAndResolveSpcItems(req, plantCode);
        String plantName = loginUser.getPlantCode().getChineseName();

        // P0: 变更前快照
        String beforeSnapshot = buildSnapshot(standardId);

        // 旧版本停用
        oldStandard.setIsActive("否");
        oldStandard.setUpdatedBy(loginUser.getRealName());
        standardMapper.updateById(oldStandard);

        // 创建新版本
        int newVersion = (oldStandard.getStdVersion() != null ? oldStandard.getStdVersion() : 0) + 1;
        FaiInspectionStandard newStandard = new FaiInspectionStandard();
        BeanUtils.copyProperties(req, newStandard);
        newStandard.setId(null); // 新行
        newStandard.setPlantCode(plantCode);
        newStandard.setPlantName(plantName);
        newStandard.setCreatedBy(loginUser.getRealName());
        newStandard.setUpdatedBy(loginUser.getRealName());
        newStandard.setStdVersion(newVersion);
        newStandard.setIsActive("是");
        standardMapper.insert(newStandard);

        // 保存参数项
        saveItems(newStandard.getId(), req.getItems(), plantCode, plantName, loginUser.getRealName());

        // 确保同物料+工序下最多只有一个激活版本
        deactivateOthers(newStandard.getId(), req.getMaterialCode(), req.getItemType(),
                req.getItemCode(), req.getProcessName(), plantCode, loginUser.getRealName());

        // P0: 记录创建新版本历史
        String afterSnapshot = buildSnapshot(newStandard.getId());
        String reason = "版本升级 v" + oldStandard.getStdVersion() + " → v" + newVersion
                + (StringUtils.hasText(req.getRemark()) ? "：" + req.getRemark() : "");
        recordHistory(newStandard.getId(), "UPDATE", beforeSnapshot, afterSnapshot, reason, loginUser);

        log.info("创建新版本: oldId={} v{} → newId={} v{}", standardId,
                oldStandard.getStdVersion(), newStandard.getId(), newVersion);
        return newStandard.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long rollbackToVersion(Long standardId, Long historyId, LoginUser loginUser) {
        FaiInspectionStandard current = standardMapper.selectById(standardId);
        if (current == null) throw new BusinessException(ResultCode.NOT_FOUND, "标准模板不存在");

        FaiInspectionStandardHistory history = historyMapper.selectById(historyId);
        if (history == null) throw new BusinessException(ResultCode.NOT_FOUND, "历史记录不存在");
        if (!standardId.equals(history.getStandardId()))
            throw new BusinessException(ResultCode.BAD_REQUEST, "历史记录不属于该标准");

        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(current.getPlantCode()))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他厂区的标准");

        // 从快照恢复标准数据
        com.fasterxml.jackson.databind.JsonNode snapshot = history.getAfterSnapshot();
        if (snapshot == null || !snapshot.has("standard"))
            throw new BusinessException(ResultCode.BAD_REQUEST, "历史快照不完整，无法回滚");

        try {
            // 解析快照中的标准主表
            String stdJson = snapshot.get("standard").toString();
            FaiInspectionStandard snapshotStandard = objectMapper.readValue(stdJson, FaiInspectionStandard.class);

            // 当前版本停用
            current.setIsActive("否");
            current.setUpdatedBy(loginUser.getRealName());
            standardMapper.updateById(current);

            // 从快照创建新版本
            int rollbackVersion = (current.getStdVersion() != null ? current.getStdVersion() : 0) + 1;
            FaiInspectionStandard rolledBack = new FaiInspectionStandard();
            BeanUtils.copyProperties(snapshotStandard, rolledBack, "id", "plantCode", "plantName",
                    "createdBy", "updatedBy", "createdAt", "updatedAt", "version", "isDeleted");
            rolledBack.setId(null);
            rolledBack.setPlantCode(plantCode);
            rolledBack.setPlantName(loginUser.getPlantCode().getChineseName());
            rolledBack.setCreatedBy(loginUser.getRealName());
            rolledBack.setUpdatedBy(loginUser.getRealName());
            rolledBack.setStdVersion(rollbackVersion);
            rolledBack.setIsActive("是");
            standardMapper.insert(rolledBack);

            // 恢复参数项
            if (snapshot.has("items") && snapshot.get("items").isArray()) {
                String itemsJson = snapshot.get("items").toString();
                FaiStandardItemRequest[] itemArray = objectMapper.readValue(itemsJson, FaiStandardItemRequest[].class);
                List<FaiStandardItemRequest> items = java.util.Arrays.asList(itemArray);
                saveItems(rolledBack.getId(), items, plantCode,
                        loginUser.getPlantCode().getChineseName(), loginUser.getRealName());
            }

            // P0: 记录回滚历史
            String beforeSnapshot = buildSnapshot(standardId);
            String afterSnapshot = buildSnapshot(rolledBack.getId());
            String reason = "回滚至历史版本 v" + snapshotStandard.getStdVersion()
                    + "（基于历史记录 #" + historyId + "）";
            recordHistory(rolledBack.getId(), "UPDATE", beforeSnapshot, afterSnapshot, reason, loginUser);

            log.info("回滚完成: oldId={} → newId={} v{}, 基于historyId={}",
                    standardId, rolledBack.getId(), rollbackVersion, historyId);
            return rolledBack.getId();
        } catch (Exception e) {
            log.error("回滚失败: standardId={}, historyId={}", standardId, historyId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "回滚失败: " + e.getMessage());
        }
    }

    // ========== P3：定期复审提醒 ==========

    @Override
    public void markReviewed(Long standardId, LoginUser loginUser) {
        FaiInspectionStandard standard = standardMapper.selectById(standardId);
        if (standard == null) throw new BusinessException(ResultCode.NOT_FOUND, "标准模板不存在");

        String plantCode = loginUser.getPlantCode().name();
        if (!plantCode.equals(standard.getPlantCode()))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他厂区的标准");

        standard.setLastReviewedAt(LocalDateTime.now());
        standard.setUpdatedBy(loginUser.getRealName());
        standardMapper.updateById(standard);
    }

    @Override
    public List<FaiStandardResponse> getOverdueReviews(String plantCode) {
        return list(plantCode).stream()
                .filter(s -> {
                    if (s.getLastReviewedAt() == null) return true; // 从未复审过，算逾期
                    if (s.getReviewIntervalDays() == null || s.getReviewIntervalDays() <= 0) return false;
                    LocalDateTime deadline = s.getLastReviewedAt().plusDays(s.getReviewIntervalDays());
                    return LocalDateTime.now().isAfter(deadline);
                })
                .collect(Collectors.toList());
    }

    /**
     * 从 fai_inspection_standard（检验标准维护）按 分类 + 代码 + 厂区去重取已维护工序。
     * itemCode 非空时仅返回该代码绑定的工序；为空时返回该分类下全部已维护工序。
     * 保证变更触发工序下拉与实际维护的标准一致。
     */
    @Override
    public List<FaiStandardProcessVO> listProcessesByItemType(String plantCode, String itemType, String itemCode) {
        LambdaQueryWrapper<FaiInspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionStandard::getPlantCode, plantCode)
                .eq(FaiInspectionStandard::getItemType, itemType)
                .eq(StringUtils.hasText(itemCode), FaiInspectionStandard::getItemCode, itemCode)
                .isNotNull(FaiInspectionStandard::getProcessCode)
                .ne(FaiInspectionStandard::getProcessCode, "")
                .select(FaiInspectionStandard::getProcessCode, FaiInspectionStandard::getProcessName)
                .groupBy(FaiInspectionStandard::getProcessCode, FaiInspectionStandard::getProcessName)
                .orderByAsc(FaiInspectionStandard::getProcessCode);
        List<FaiInspectionStandard> list = standardMapper.selectList(wrapper);
        return list.stream()
                .map(s -> new FaiStandardProcessVO(s.getProcessCode(), s.getProcessName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<FaiStandardSpcParamVO> listSpcParams(String itemType, String itemCode, String processName, String plantCode) {
        FaiStandardResponse standard = latestActive(itemCode, itemType, processName, plantCode);
        if (standard == null || standard.getItems() == null || standard.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        // 筛选 spcEnabled="是" 的项
        List<FaiInspectionStandardItem> spcItems = standard.getItems().stream()
                .filter(isItem -> "是".equals(isItem.getSpcEnabled()))
                .collect(Collectors.toList());

        if (spcItems.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查 SPC 参数字典（用于补全 subgroupSize / chartType）
        Set<Long> spcParamIds = spcItems.stream()
                .map(FaiInspectionStandardItem::getSpcParameterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SpcParameter> spcParamMap = spcParamIds.isEmpty() ? Collections.emptyMap() :
                spcParameterMapper.selectBatchIds(spcParamIds).stream()
                        .collect(Collectors.toMap(SpcParameter::getId, p -> p));

        return spcItems.stream()
                .map(it -> {
                    SpcParameter spcParam = it.getSpcParameterId() != null
                            ? spcParamMap.get(it.getSpcParameterId())
                            : null;

                    Integer subgroupSize = it.getSubgroupSize();
                    if (subgroupSize == null && spcParam != null) {
                        subgroupSize = spcParam.getSubgroupSize();
                    }

                    String chartType = it.getChartType();
                    if (!StringUtils.hasText(chartType) && spcParam != null) {
                        chartType = spcParam.getChartType();
                    }

                    return new FaiStandardSpcParamVO(
                            it.getId(),
                            it.getSpcParameterId(),
                            it.getParamName(),
                            it.getParamCode(),
                            it.getUpperLimit(),
                            it.getLowerLimit(),
                            it.getStandardValue(),
                            it.getUnit(),
                            subgroupSize,
                            chartType
                    );
                })
                .collect(Collectors.toList());
    }

}
