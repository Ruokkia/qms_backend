package com.kangli.qms.service.fai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.fai.dto.FaiInspectionItemResponse;
import com.kangli.qms.service.fai.dto.FaiInspectionQuery;
import com.kangli.qms.service.fai.dto.FaiInspectionRecordResponse;
import com.kangli.qms.service.fai.dto.FaiItemValueRequest;
import com.kangli.qms.service.fai.dto.FaiReportResponse;
import com.kangli.qms.service.fai.dto.FaiSignatureRequest;
import com.kangli.qms.service.fai.dto.FaiSpcBaselineVO;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.domain.fai.entity.FaiChangeTrigger;
import com.kangli.qms.domain.fai.entity.FaiInspectionItem;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.entity.FaiSignature;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.fai.mapper.FaiChangeTriggerMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.fai.mapper.FaiSignatureMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.fai.FaiInspectionService;
import com.kangli.qms.service.fai.SignatureIntegrity;
import com.kangli.qms.service.spc.SpcSubgroupService;
import com.kangli.qms.service.fai.FaiStandardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M3 首件检验 Service 实现（核心业务）。
 * <p>建单：从变更触发读取物料+工序 → 复制最新激活标准模板参数项。</p>
 * <p>判定：actual_value 与上下限/标准值比对，自动设置 result；必检项未填或不合格则整单不合格。</p>
 * <p>签名：SHA-256 摘要入库；合格且已签触发 SPC 联动（spc_subgroup 表当前未建，先打印日志模拟，TODO）。</p>
 */
@Slf4j
@Service
public class FaiInspectionServiceImpl implements FaiInspectionService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");
    private static final DateTimeFormatter SIGN_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FaiInspectionRecordMapper recordMapper;
    private final FaiInspectionItemMapper itemMapper;
    private final FaiChangeTriggerMapper changeTriggerMapper;
    private final FaiInspectionStandardMapper standardMapper;
    private final FaiInspectionStandardItemMapper standardItemMapper;
    private final FaiSignatureMapper signatureMapper;
    private final FaiStandardService standardService;
    private final SpcSubgroupService spcSubgroupService;
    private final ExceptionService exceptionService;
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public FaiInspectionServiceImpl(FaiInspectionRecordMapper recordMapper,
                                    FaiInspectionItemMapper itemMapper,
                                    FaiChangeTriggerMapper changeTriggerMapper,
                                    FaiInspectionStandardMapper standardMapper,
                                    FaiInspectionStandardItemMapper standardItemMapper,
                                    FaiSignatureMapper signatureMapper,
                                    FaiStandardService standardService,
                                    SpcSubgroupService spcSubgroupService,
                                    ExceptionService exceptionService,
                                    SysUserMapper userMapper,
                                    AuditLogService auditLogService) {
        this.recordMapper = recordMapper;
        this.itemMapper = itemMapper;
        this.changeTriggerMapper = changeTriggerMapper;
        this.standardMapper = standardMapper;
        this.standardItemMapper = standardItemMapper;
        this.signatureMapper = signatureMapper;
        this.standardService = standardService;
        this.spcSubgroupService = spcSubgroupService;
        this.exceptionService = exceptionService;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaiInspectionRecordResponse create(Long changeTriggerId, LoginUser loginUser) {
        FaiChangeTrigger trigger = changeTriggerMapper.selectById(changeTriggerId);
        if (trigger == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变更触发记录不存在");
        }
        String plantCode = loginUser.getPlantCode().name();

        // 提前加载最新激活标准模板，用于快照和复制参数项
        FaiStandardResponse standard =
                standardService.latestActive(trigger.getMaterialCode(), trigger.getProcessName(), plantCode);

        FaiInspectionRecord record = new FaiInspectionRecord();
        record.setFaiNo(generateFaiNo(plantCode));
        record.setChangeTriggerId(trigger.getId());
        record.setMaterialCode(trigger.getMaterialCode());
        record.setMaterialName(trigger.getMaterialName());
        record.setBatchNo(trigger.getBatchNo());
        record.setProcessName(trigger.getProcessName());
        record.setProcessCode(trigger.getProcessCode());
        record.setItemType(trigger.getItemType());
        record.setItemCode(trigger.getItemCode());
        record.setItemName(trigger.getItemName());
        record.setItemBarcode(trigger.getItemBarcode());
        record.setInspectionResult("待判定");
        record.setSignatureStatus("未签");
        record.setPlantCode(plantCode);
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());

        // 建单时记录标准配置快照，用于审计追溯
        if (standard != null) {
            try {
                record.setFormSnapshot(objectMapper.writeValueAsString(standard));
            } catch (Exception e) {
                log.warn("序列化标准快照失败 triggerId={}", changeTriggerId, e);
            }
        }

        recordMapper.insert(record);

        // 复制最新激活标准模板参数项 → 检验明细（actual_value 为空，result=待判定）
        if (standard != null && standard.getItems() != null) {
            List<FaiInspectionItem> items = new ArrayList<>();
            int sort = 0;
            for (FaiInspectionStandardItem stdItem : standard.getItems()) {
                FaiInspectionItem item = new FaiInspectionItem();
                item.setFaiRecordId(record.getId());
                item.setStandardItemId(stdItem.getId()); // 关联标准参数项，支持后续动态同步
                item.setParamName(stdItem.getParamName());
                item.setParamCode(stdItem.getParamCode());
                item.setParamCategory(stdItem.getParamCategory());
                item.setStandardValue(stdItem.getStandardValue());
                item.setUpperLimit(stdItem.getUpperLimit());
                item.setLowerLimit(stdItem.getLowerLimit());
                item.setUnit(stdItem.getUnit());
                item.setSpcEnabled(stdItem.getSpcEnabled());
                item.setSpcParameterId(stdItem.getSpcParameterId());
                item.setResult("待判定");
                item.setSortOrder(sort++);
                item.setPlantCode(plantCode);
                item.setPlantName(loginUser.getPlantCode().getChineseName());
                item.setCreatedBy(loginUser.getRealName());
                item.setUpdatedBy(loginUser.getRealName());
                items.add(item);
            }
            for (FaiInspectionItem item : items) {
                itemMapper.insert(item);
            }
        }

        // 变更触发状态推进为「已检验」（不关闭）
        trigger.setStatus("已检验");
        trigger.setUpdatedBy(loginUser.getRealName());
        changeTriggerMapper.updateById(trigger);

        return detail(record.getId());
    }

    @Override
    public PageResult<FaiInspectionRecordResponse> page(FaiInspectionQuery query, String plantCode) {
        Page<FaiInspectionRecord> pageObj = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<FaiInspectionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionRecord::getPlantCode, plantCode);
        if (StringUtils.hasText(query.getFaiNo())) {
            wrapper.like(FaiInspectionRecord::getFaiNo, query.getFaiNo());
        }
        if (StringUtils.hasText(query.getBatchNo())) {
            wrapper.eq(FaiInspectionRecord::getBatchNo, query.getBatchNo());
        }
        if (StringUtils.hasText(query.getMaterialName())) {
            wrapper.like(FaiInspectionRecord::getMaterialName, query.getMaterialName());
        }
        if (StringUtils.hasText(query.getItemType())) {
            wrapper.eq(FaiInspectionRecord::getItemType, query.getItemType());
        }
        if (Boolean.TRUE.equals(query.getArchiveOnly())) {
            // 档案模式：「未签名不进档案」，强制仅返回已签记录（不合格亦可进档案，便于查阅已签的不合格报告）；
            // 忽略外部 inspectionResult 入参，由档案模式统一控制，避免冲突
            wrapper.eq(FaiInspectionRecord::getSignatureStatus, "已签");
        } else {
            if (StringUtils.hasText(query.getInspectionResult())) {
                wrapper.eq(FaiInspectionRecord::getInspectionResult, query.getInspectionResult());
            }
            if (StringUtils.hasText(query.getSignatureStatus())) {
                wrapper.eq(FaiInspectionRecord::getSignatureStatus, query.getSignatureStatus());
            }
        }
        wrapper.orderByDesc(FaiInspectionRecord::getCreatedAt);
        Page<FaiInspectionRecord> page = recordMapper.selectPage(pageObj, wrapper);
        List<FaiInspectionRecordResponse> list = page.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResult.of(new Page<FaiInspectionRecordResponse>() {{
            setRecords(list);
            setTotal(page.getTotal());
            setCurrent(page.getCurrent());
            setSize(page.getSize());
        }});
    }

    @Override
    public FaiInspectionRecordResponse detail(Long id) {
        FaiInspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        return toResponse(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaiInspectionRecordResponse submitItems(FaiItemValueRequest request, LoginUser loginUser) {
        FaiInspectionRecord record = recordMapper.selectById(request.getFaiRecordId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        // 防御性校验：已签名记录不可修改检验数据
        if ("已签".equals(record.getSignatureStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "首件检验记录[" + record.getFaiNo() + "]已完成电子签名，数据不可修改。如需修改请先作废后重新建单");
        }
        if (request.getItems() != null) {
            for (FaiItemValueRequest.ItemValue v : request.getItems()) {
                if (v.getId() == null) {
                    continue;
                }
                FaiInspectionItem item = itemMapper.selectById(v.getId());
                if (item == null || !record.getId().equals(item.getFaiRecordId())) {
                    continue;
                }
                item.setActualValue(v.getActualValue());
                item.setResult(judgeItem(item));
                item.setUpdatedBy(loginUser.getRealName());
                itemMapper.updateById(item);
            }
        }
        // 自动判定主表
        autoJudge(record, loginUser);
        return detail(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaiInspectionRecordResponse judge(Long id, LoginUser loginUser) {
        FaiInspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        if (listItems(record.getId()).isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "请先在首件检验标准维护中为该物料和工序配置需要判定的SPC参数");
        }
        autoJudge(record, loginUser);
        return detail(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaiInspectionRecordResponse signature(FaiSignatureRequest request, LoginUser loginUser) {
        FaiInspectionRecord record = recordMapper.selectById(request.getFaiRecordId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        // 电子签名前置：必须先提交检验结果（录入实际值并完成判定，得出合格/不合格结论）
        if ("待判定".equals(record.getInspectionResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "请先提交检验结果（录入实际值并完成判定）后再进行电子签名");
        }
        SysUser signer = verifySignaturePassword(request.getPassword(), loginUser);
        LocalDateTime signedAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String signedAtStr = signedAt.format(SIGN_DTF);
        String signerId = String.valueOf(signer.getId());
        String signerName = signer.getRealName();
        String raw = record.getFaiNo() + "|" + signerId + "|" + signedAtStr + "|" + request.getSignReason();
        String hash = sha256(raw);

        // 内容绑定哈希：将电子签名与完整检验记录内容（逐项实际值/判定/标准值/上下限 + 主表结论）绑定，
        // 满足 21 CFR Part 11「电子签名须与所签记录内容绑定、防事后篡改」要求
        String contentHash = computeContentHash(record, listItems(record.getId()), signerId, signedAtStr, request.getSignReason());

        FaiSignature signature = new FaiSignature();
        signature.setFaiRecordId(record.getId());
        signature.setSignerId(signerId);
        signature.setSignerName(signerName);
        signature.setSignType(request.getSignType());
        signature.setSignatureHash(hash);
        signature.setContentHash(contentHash);
        signature.setSignedAt(signedAt);
        signature.setSignReason(request.getSignReason());
        signature.setPlantCode(record.getPlantCode());
        signature.setPlantName(record.getPlantName());
        signature.setCreatedBy(loginUser.getRealName());
        signature.setUpdatedBy(loginUser.getRealName());
        signatureMapper.insert(signature);

        record.setSignatureStatus("已签");
        record.setUpdatedBy(loginUser.getRealName());
        recordMapper.updateById(record);

        // SPC 联动：仅 合格 && 已签 时触发
        if ("合格".equals(record.getInspectionResult()) && "已签".equals(record.getSignatureStatus())) {
            try {
                spcSubgroupService.autoImportFromSignedFai(record.getId(), loginUser);
                record.setSpcSyncAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
                // 更新 sync 时间（与签名事务分离的独立 update，不影响签名本身）
                updateSpcSyncAt(record.getId());
            } catch (Exception e) {
                log.error("SPC子组同步失败 faiRecordId={}，签名已完成但数据未同步到SPC", record.getId(), e);
                // 签名已完成不回滚，SPC同步失败仅记录日志，后续可手动重试
            }
        }
        return detail(record.getId());
    }

    private SysUser verifySignaturePassword(String password, LoginUser loginUser) {
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.ACCOUNT_OR_PASSWORD_ERROR, "电子签名密码错误");
        }
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.ACCOUNT_OR_PASSWORD_ERROR, "电子签名密码错误");
        }
        return user;
    }

    @Override
    public FaiReportResponse report(Long id) {
        FaiInspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        // 「未签名不进档案」：未完成电子签名的记录禁止获取完整报告（服务端硬拦截兜底）
        if (!"已签".equals(record.getSignatureStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "首件检验记录[" + id + "]尚未完成电子签名，禁止查看完整报告");
        }
        // 状态 + 哈希双校验：已签且内容绑定哈希一致方可导出；TAMPERED 视为签名无效/被篡改并拒绝
        SignatureIntegrity integrity = verifySignatureIntegrity(id);
        if (integrity == SignatureIntegrity.TAMPERED) {
            auditLogService.record("fai_signature", id, "VERIFY", null, null,
                    "电子签名完整性校验失败（疑似内容被篡改），拒绝导出报告；faiNo=" + record.getFaiNo());
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "首件检验记录[" + id + "]电子签名完整性校验未通过（疑似内容被篡改），禁止导出报告");
        }
        FaiReportResponse resp = new FaiReportResponse();
        BeanUtils.copyProperties(record, resp);
        List<FaiInspectionItem> items = listItems(record.getId());
        List<FaiInspectionItemResponse> itemRespList = toItemResponses(record, items);
        resp.setItems(itemRespList);
        resp.setSignatures(listSignatures(record.getId()));
        resp.setSignatureIntact(integrity == SignatureIntegrity.INTACT);
        resp.setLegacySignature(integrity == SignatureIntegrity.LEGACY_UNVERIFIABLE);
        if (integrity == SignatureIntegrity.LEGACY_UNVERIFIABLE) {
            log.warn("历史遗留签名无法做内容绑定复核（content_hash 为空），按祖父条款允许导出报告：faiNo={}", record.getFaiNo());
        }

        int total = itemRespList.size();
        int qualified = 0;
        int unqualified = 0;
        for (FaiInspectionItemResponse it : itemRespList) {
            if ("合格".equals(it.getResult())) {
                qualified++;
            } else if ("不合格".equals(it.getResult())) {
                unqualified++;
            }
        }
        resp.setTotalCount(total);
        resp.setQualifiedCount(qualified);
        resp.setUnqualifiedCount(unqualified);
        BigDecimal passRate = total == 0 ? BigDecimal.ZERO
                : new BigDecimal(qualified).multiply(new BigDecimal("100"))
                .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
        resp.setPassRate(passRate);
        return resp;
    }

    @Override
    public List<FaiSpcBaselineVO> spcBaseline(Long faiRecordId) {
        FaiInspectionRecord record = recordMapper.selectById(faiRecordId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        List<FaiInspectionItem> items = listItems(faiRecordId);
        // 按 param_code 分组（同一参数多行视为一个子组内的多个样本）
        Map<String, List<FaiInspectionItem>> groups = items.stream()
                .filter(i -> i.getActualValue() != null)
                .collect(Collectors.groupingBy(i -> i.getParamCode() == null ? "" : i.getParamCode(),
                        LinkedHashMap::new, Collectors.toList()));
        List<FaiSpcBaselineVO> result = new ArrayList<>();
        for (Map.Entry<String, List<FaiInspectionItem>> entry : groups.entrySet()) {
            for (FaiInspectionItem it : entry.getValue()) {
                FaiSpcBaselineVO vo = new FaiSpcBaselineVO();
                vo.setSubgroupNo(record.getFaiNo());
                vo.setParamCode(it.getParamCode());
                vo.setParamName(it.getParamName());
                vo.setValue(it.getActualValue());
                vo.setSampleTime(record.getCreatedAt());
                vo.setPlantCode(record.getPlantCode());
                result.add(vo);
            }
        }
        return result;
    }

    // ===== 内部工具 =====

    /**
     * 自动判定主表结果（三态语义）：
     * - 任一明细不合格 → 整单不合格；
     * - 否则任一明细待判定（实际值未录入）→ 整单待判定；
     * - 全部明细录入且合格 → 整单合格。
     * 不合格时推进变更触发状态为「已检验」（不关闭），生产系统据 inspection_result 拦截生产。
     */
    private void autoJudge(FaiInspectionRecord record, LoginUser loginUser) {
        List<FaiInspectionItem> items = listItems(record.getId());
        if (items.isEmpty()) {
            record.setInspectionResult("待判定");
            record.setSignatureStatus("未签");
            record.setUpdatedBy(loginUser.getRealName());
            recordMapper.updateById(record);
            return;
        }
        Map<String, String> requiredMap = buildRequiredMap(record);

        boolean hasFail = false;     // 任一明细不合格
        boolean hasPending = false;  // 任一明细待判定（实际值未录入）
        for (FaiInspectionItem item : items) {
            String result = judgeItem(item);
            item.setResult(result);
            itemMapper.updateById(item);
            String required = requiredMap.getOrDefault(item.getParamCode(), "否");
            if ("是".equals(required) && "待判定".equals(result)) {
                hasPending = true; // 必检项未录入
            }
            if ("不合格".equals(result)) {
                hasFail = true;
            }
            if ("待判定".equals(result)) {
                hasPending = true;
            }
        }
        // 不合格优先；其次待判定；全部合格则合格
        record.setInspectionResult(hasFail ? "不合格" : (hasPending ? "待判定" : "合格"));
        // 结论回退为「待判定」(实际值未录全) 时，既往电子签名失效（须重新提交判定后方可再签）
        // L16：非「合格」即视为电子签名失效（含合格→不合格回退及待判定），需重新签字
        if (!"合格".equals(record.getInspectionResult())) {
            boolean wasSigned = "已签".equals(record.getSignatureStatus());
            record.setSignatureStatus("未签");
            if (wasSigned) {
                auditLogService.record("fai_inspection_record", record.getId(), "UPDATE", null, null,
                        "首件检验结论非合格（" + record.getInspectionResult() + "），电子签名自动失效，须重新签字；faiNo=" + record.getFaiNo());
            }
        }
        record.setUpdatedBy(loginUser.getRealName());
        recordMapper.updateById(record);

        if (hasFail) {
            FaiChangeTrigger trigger = changeTriggerMapper.selectById(record.getChangeTriggerId());
            if (trigger != null) {
                trigger.setStatus("已检验"); // 不合格：更新状态但不关闭，记录禁止生产标记
                trigger.setUpdatedBy(loginUser.getRealName());
                changeTriggerMapper.updateById(trigger);
            }
            // L30：首件不合格自动建异常整改单（去重由 createFromFai 保证）
            exceptionService.createFromFai(record, loginUser);
        }
    }

    /**
     * 单参数判定算法（三态语义）：
     * 1) actual 为 null → 待判定（实际值尚未录入）；
     * 2) 上下限均非空 → [lower, upper] 内为合格，超出为不合格；
     * 3) 上下限均空 → 以 standard_value 精确匹配（容差 ±0.0001），不符为不合格；
     * 4) 仅一侧有界 → 仅按该边界判定；
     * 5) 已录入但无任何判定基准（无上下限且无标准值）→ 不合格（无法确认合格）。
     */
    private String judgeItem(FaiInspectionItem item) {
        BigDecimal actual = item.getActualValue();
        if (actual == null) {
            return "待判定";
        }
        BigDecimal upper = item.getUpperLimit();
        BigDecimal lower = item.getLowerLimit();
        if (upper != null && lower != null) {
            return (actual.compareTo(lower) >= 0 && actual.compareTo(upper) <= 0) ? "合格" : "不合格";
        }
        if (upper == null && lower == null) {
            if (!StringUtils.hasText(item.getStandardValue())) {
                return "不合格"; // 已录入但无判定基准，无法确认合格
            }
            try {
                BigDecimal std = new BigDecimal(item.getStandardValue());
                int cmp = actual.subtract(std).abs().compareTo(TOLERANCE);
                return cmp <= 0 ? "合格" : "不合格";
            } catch (NumberFormatException e) {
                return "不合格";
            }
        }
        boolean ok = true;
        if (lower != null && actual.compareTo(lower) < 0) {
            ok = false;
        }
        if (upper != null && actual.compareTo(upper) > 0) {
            ok = false;
        }
        return ok ? "合格" : "不合格";
    }

    /**
     * 构建 param_code → 是否必检 映射（取最新激活标准模板）。
     */
    private Map<String, String> buildRequiredMap(FaiInspectionRecord record) {
        Map<String, String> map = new java.util.HashMap<>();
        FaiStandardResponse standard =
                standardService.latestActive(record.getMaterialCode(), record.getProcessName(), record.getPlantCode());
        if (standard != null && standard.getItems() != null) {
            for (FaiInspectionStandardItem it : standard.getItems()) {
                map.put(it.getParamCode() == null ? "" : it.getParamCode(), it.getIsRequired());
            }
        }
        return map;
    }

    private String generateFaiNo(String plantCode) {
        String dateStr = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "FAI-" + plantCode + "-" + dateStr + "-";
        Long count = recordMapper.selectCount(
                new LambdaQueryWrapper<FaiInspectionRecord>().likeRight(FaiInspectionRecord::getFaiNo, prefix));
        long seq = (count == null ? 0 : count) + 1;
        return prefix + String.format("%04d", seq);
    }

    private void updateSpcSyncAt(Long recordId) {
        FaiInspectionRecord r = new FaiInspectionRecord();
        r.setId(recordId);
        r.setSpcSyncAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        recordMapper.updateById(r);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "签名哈希计算失败");
        }
    }

    private List<FaiInspectionItem> listItems(Long recordId) {
        LambdaQueryWrapper<FaiInspectionItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiInspectionItem::getFaiRecordId, recordId)
                .orderByAsc(FaiInspectionItem::getSortOrder);
        return itemMapper.selectList(wrapper);
    }

    private List<FaiSignature> listSignatures(Long recordId) {
        LambdaQueryWrapper<FaiSignature> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaiSignature::getFaiRecordId, recordId)
                .orderByAsc(FaiSignature::getSignedAt);
        return signatureMapper.selectList(wrapper);
    }

    private FaiInspectionRecordResponse toResponse(FaiInspectionRecord record) {
        FaiInspectionRecordResponse resp = new FaiInspectionRecordResponse();
        BeanUtils.copyProperties(record, resp);
        List<FaiInspectionItem> items = listItems(record.getId());
        resp.setItems(toItemResponses(record, items));
        resp.setSignatures(listSignatures(record.getId()));
        // 暴露签名完整性标记，便于前端识别历史遗留/被篡改记录
        if ("已签".equals(record.getSignatureStatus())) {
            SignatureIntegrity integrity = verifySignatureIntegrity(record.getId());
            resp.setSignatureIntact(integrity == SignatureIntegrity.INTACT);
            resp.setLegacySignature(integrity == SignatureIntegrity.LEGACY_UNVERIFIABLE);
        } else {
            resp.setSignatureIntact(false);
            resp.setLegacySignature(false);
        }
        return resp;
    }

    @Override
    public SignatureIntegrity verifySignatureIntegrity(Long id) {
        FaiInspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        if (!"已签".equals(record.getSignatureStatus())) {
            // 未签名，无完整性判定意义
            return SignatureIntegrity.INTACT;
        }
        List<FaiSignature> sigs = listSignatures(id);
        if (sigs.isEmpty()) {
            // 状态为已签但无签名记录：数据异常，按被篡改处理
            return SignatureIntegrity.TAMPERED;
        }
        FaiSignature sig = sigs.get(sigs.size() - 1);
        if (sig.getContentHash() == null) {
            // 历史遗留签名未绑定内容哈希，无法复核（祖父条款认可）
            return SignatureIntegrity.LEGACY_UNVERIFIABLE;
        }
        String current = computeContentHash(record, listItems(id), sig.getSignerId(),
                sig.getSignedAt() == null ? "" : sig.getSignedAt().format(SIGN_DTF), sig.getSignReason());
        return current.equals(sig.getContentHash()) ? SignatureIntegrity.INTACT : SignatureIntegrity.TAMPERED;
    }

    /**
     * 计算「内容绑定哈希」：将电子签名与完整检验记录内容绑定。
     * <p>覆盖 faiNo|signerId|signedAt|signReason|主表结论 inspectionResult，以及逐项
     * (paramCode|actualValue|result|standardValue|upperLimit|lowerLimit)，按上传顺序拼接。
     * 任一检验数据或判定结论在签名后被篡改，重算哈希必然失配，满足 21 CFR Part 11 防篡改要求。</p>
     */
    String computeContentHash(FaiInspectionRecord record, List<FaiInspectionItem> items,
                               String signerId, String signedAtStr, String signReason) {
        StringBuilder itemsPart = new StringBuilder();
        for (FaiInspectionItem it : items) {
            itemsPart.append("|")
                    .append(it.getParamCode() == null ? "" : it.getParamCode())
                    .append("=").append(it.getActualValue() == null ? "" : it.getActualValue())
                    .append("|").append(it.getResult() == null ? "" : it.getResult())
                    .append("|").append(it.getStandardValue() == null ? "" : it.getStandardValue())
                    .append("|").append(it.getUpperLimit() == null ? "" : it.getUpperLimit())
                    .append("|").append(it.getLowerLimit() == null ? "" : it.getLowerLimit());
        }
        String raw = record.getFaiNo() + "|" + signerId + "|" + signedAtStr + "|"
                + (signReason == null ? "" : signReason) + "|" + record.getInspectionResult() + itemsPart;
        return sha256(raw);
    }

    private List<FaiInspectionItemResponse> toItemResponses(FaiInspectionRecord record, List<FaiInspectionItem> items) {
        Map<String, String> requiredMap = buildRequiredMap(record);
        // 获取当前激活标准的最新参数项，用于标准值对比
        Map<String, FaiInspectionStandardItem> latestStandardMap = buildStandardItemMap(record);
        return items.stream().map(it -> {
            FaiInspectionItemResponse r = new FaiInspectionItemResponse();
            BeanUtils.copyProperties(it, r);
            String key = it.getParamCode() == null ? "" : it.getParamCode();
            r.setIsRequired(requiredMap.getOrDefault(key, "否"));

            // 合并最新标准值：通过 param_code 匹配当前激活标准
            FaiInspectionStandardItem latestStd = latestStandardMap.get(key);
            if (latestStd != null) {
                r.setLatestStandardValue(latestStd.getStandardValue());
                r.setLatestUpperLimit(latestStd.getUpperLimit());
                r.setLatestLowerLimit(latestStd.getLowerLimit());
                r.setLatestUnit(latestStd.getUnit());
                // 任一标准字段不一致则标记为已变更
                boolean changed = !nullSafeEquals(r.getStandardValue(), latestStd.getStandardValue())
                        || !nullSafeBigDecimalEquals(r.getUpperLimit(), latestStd.getUpperLimit())
                        || !nullSafeBigDecimalEquals(r.getLowerLimit(), latestStd.getLowerLimit())
                        || !nullSafeEquals(r.getUnit(), latestStd.getUnit());
                r.setHasStandardChanged(changed);
            } else {
                // 标准中已删除该参数项
                r.setHasStandardChanged(true);
            }
            return r;
        }).collect(Collectors.toList());
    }

    /**
     * 构建当前激活标准的 param_code → 标准项 映射，用于标准值动态对比。
     */
    private Map<String, FaiInspectionStandardItem> buildStandardItemMap(FaiInspectionRecord record) {
        Map<String, FaiInspectionStandardItem> map = new java.util.HashMap<>();
        FaiStandardResponse standard =
                standardService.latestActive(record.getMaterialCode(), record.getProcessName(), record.getPlantCode());
        if (standard != null && standard.getItems() != null) {
            for (FaiInspectionStandardItem it : standard.getItems()) {
                map.put(it.getParamCode() == null ? "" : it.getParamCode(), it);
            }
        }
        return map;
    }

    private boolean nullSafeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private boolean nullSafeBigDecimalEquals(BigDecimal a, BigDecimal b) {
        return (a == null && b == null) || (a != null && a.compareTo(b) == 0);
    }

    /**
     * 刷新检验明细标准值：从当前激活标准中同步最新值到未录入实际值的检验项，
     * 并补全标准中新增的参数项。已完成实际值录入的项目仅更新 standardItemId 引用，不覆盖已填值。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaiInspectionRecordResponse refreshStandard(Long id, LoginUser loginUser) {
        FaiInspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件检验记录不存在");
        }
        // 防御性校验：已签名记录不可刷新标准
        if ("已签".equals(record.getSignatureStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "首件检验记录[" + record.getFaiNo() + "]已完成电子签名，不可刷新标准。如需修改请先作废后重新建单");
        }

        Map<String, FaiInspectionStandardItem> latestStandardMap = buildStandardItemMap(record);
        if (latestStandardMap.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "请先在首件检验标准维护中为该物料和工序配置需要判定的SPC参数");
        }
        List<FaiInspectionItem> items = listItems(record.getId());

        // 已有检验项的 param_code 集合
        Set<String> existingKeys = new HashSet<>();
        for (FaiInspectionItem item : items) {
            String key = item.getParamCode() == null ? "" : item.getParamCode();
            existingKeys.add(key);
            FaiInspectionStandardItem latestStd = latestStandardMap.get(key);
            if (latestStd != null) {
                // 更新 standardItemId 与参数类别（指向最新标准项）
                item.setStandardItemId(latestStd.getId());
                item.setParamCategory(latestStd.getParamCategory());
                item.setSpcEnabled(latestStd.getSpcEnabled());
                item.setSpcParameterId(latestStd.getSpcParameterId());
                // 仅当 actualValue 为空（未录入）时，同步标准值
                if (item.getActualValue() == null) {
                    item.setStandardValue(latestStd.getStandardValue());
                    item.setUpperLimit(latestStd.getUpperLimit());
                    item.setLowerLimit(latestStd.getLowerLimit());
                    item.setUnit(latestStd.getUnit());
                }
                item.setUpdatedBy(loginUser.getRealName());
                itemMapper.updateById(item);
            }
        }

        // 标准中新增的参数项（param_code 不在已有检验项中），追加到检验明细
        int maxSort = items.stream().mapToInt(it -> it.getSortOrder() == null ? 0 : it.getSortOrder()).max().orElse(0);
        for (Map.Entry<String, FaiInspectionStandardItem> entry : latestStandardMap.entrySet()) {
            if (!existingKeys.contains(entry.getKey())) {
                FaiInspectionStandardItem stdItem = entry.getValue();
                FaiInspectionItem newItem = new FaiInspectionItem();
                newItem.setFaiRecordId(record.getId());
                newItem.setStandardItemId(stdItem.getId());
                newItem.setParamCode(stdItem.getParamCode());
                newItem.setParamName(stdItem.getParamName());
                newItem.setParamCategory(stdItem.getParamCategory());
                newItem.setStandardValue(stdItem.getStandardValue());
                newItem.setUpperLimit(stdItem.getUpperLimit());
                newItem.setLowerLimit(stdItem.getLowerLimit());
                newItem.setUnit(stdItem.getUnit());
                newItem.setSpcEnabled(stdItem.getSpcEnabled());
                newItem.setSpcParameterId(stdItem.getSpcParameterId());
                newItem.setActualValue(null);
                newItem.setResult("待判定");
                newItem.setSortOrder(++maxSort);
                newItem.setPlantCode(record.getPlantCode());
                newItem.setPlantName(record.getPlantName());
                newItem.setCreatedBy(loginUser.getRealName());
                newItem.setUpdatedBy(loginUser.getRealName());
                itemMapper.insert(newItem);
            }
        }

        // 刷新后重新判定
        autoJudge(record, loginUser);
        return detail(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUpdatedSpcParameter(SpcParameter parameter, LoginUser loginUser) {
        List<FaiInspectionStandardItem> standardItems = standardItemMapper.selectList(
                new LambdaQueryWrapper<FaiInspectionStandardItem>()
                        .eq(FaiInspectionStandardItem::getSpcParameterId, parameter.getId()));
        for (FaiInspectionStandardItem item : standardItems) {
            applySpcParameter(item, parameter);
            item.setUpdatedBy(loginUser.getRealName());
            standardItemMapper.updateById(item);
        }
        List<FaiInspectionItem> items = itemMapper.selectList(new LambdaQueryWrapper<FaiInspectionItem>()
                .eq(FaiInspectionItem::getSpcParameterId, parameter.getId()));
        Set<Long> recordIds = new HashSet<>();
        for (FaiInspectionItem item : items) {
            FaiInspectionRecord record = recordMapper.selectById(item.getFaiRecordId());
            if (record == null || "已签".equals(record.getSignatureStatus())) continue;
            applySpcParameter(item, parameter);
            item.setUpdatedBy(loginUser.getRealName());
            itemMapper.updateById(item);
            recordIds.add(record.getId());
        }
        for (Long recordId : recordIds) {
            FaiInspectionRecord record = recordMapper.selectById(recordId);
            autoJudge(record, loginUser);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaiInspectionRecordResponse resyncToSpc(Long id, LoginUser loginUser) {
        FaiInspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "首件记录不存在");
        }
        if (!"合格".equals(record.getInspectionResult()) || !"已签".equals(record.getSignatureStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已签且合格的首件记录可同步SPC");
        }
        spcSubgroupService.autoImportFromSignedFai(id, loginUser);
        updateSpcSyncAt(id);
        return detail(id);
    }

    private void applySpcParameter(FaiInspectionStandardItem item, SpcParameter parameter) {
        item.setParamName(parameter.getParamName());
        item.setParamCode(parameter.getParamCode());
        item.setUnit(parameter.getUnit());
        item.setStandardValue(parameter.getTargetValue() == null ? null : parameter.getTargetValue().stripTrailingZeros().toPlainString());
        item.setUpperLimit(parameter.getUpperSpecLimit());
        item.setLowerLimit(parameter.getLowerSpecLimit());
    }

    private void applySpcParameter(FaiInspectionItem item, SpcParameter parameter) {
        item.setParamName(parameter.getParamName());
        item.setParamCode(parameter.getParamCode());
        item.setUnit(parameter.getUnit());
        item.setStandardValue(parameter.getTargetValue() == null ? null : parameter.getTargetValue().stripTrailingZeros().toPlainString());
        item.setUpperLimit(parameter.getUpperSpecLimit());
        item.setLowerLimit(parameter.getLowerSpecLimit());
    }
}
