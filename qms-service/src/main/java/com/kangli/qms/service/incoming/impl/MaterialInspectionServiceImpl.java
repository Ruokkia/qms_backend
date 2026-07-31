package com.kangli.qms.service.incoming.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportDTO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportResultVO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionReconcileResultVO;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.incoming.MaterialInspectionService;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendVO;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendRowVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import com.kangli.qms.service.incoming.dto.MaterialInspectionImportPreviewVO;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
@Service
public class MaterialInspectionServiceImpl
        extends ServiceImpl<MaterialInspectionMapper, MaterialInspection>
        implements MaterialInspectionService {

    private final ExceptionService exceptionService;
    private final TransactionTemplate requiresNewTemplate;

    public MaterialInspectionServiceImpl(ExceptionService exceptionService,
                                         PlatformTransactionManager transactionManager) {
        this.exceptionService = exceptionService;
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public MaterialInspectionStatsVO stats() {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();

        MaterialInspectionStatsVO stats = baseMapper.selectStatsBase(plantCode);
        if (stats == null) {
            stats = new MaterialInspectionStatsVO();
            stats.setTotalBatches(0);
            stats.setQualifiedBatches(0);
            stats.setUnqualifiedBatches(0);
            stats.setQualifiedRate(BigDecimal.ZERO);
            stats.setPendingReviewCount(0);
            stats.setUrgentCount(0);
        }
        stats.setTopDefectDesc(baseMapper.selectTopDefectDesc(plantCode));
        stats.setSupplierRank(baseMapper.selectSupplierRank(plantCode, null, null));
        stats.setDailyTrend(baseMapper.selectDailyTrend(plantCode));
        return stats;
    }

    @Override
    public MaterialInspectionImportResultVO importRecords(MaterialInspectionImportDTO dto) {
        LoginUser loginUser = getCurrentLoginUser();
        boolean autoCreateException = dto.getAutoCreateException() == null || dto.getAutoCreateException();

        MaterialInspectionImportResultVO result = new MaterialInspectionImportResultVO();
        List<MaterialInspectionImportResultVO.FailItem> failList = new ArrayList<>();
        List<Long> createdExceptionIds = new ArrayList<>();
        int successCount = 0;

        List<MaterialInspection> list = dto.getList();
        if (list == null) {
            list = new ArrayList<>();
        }

        int index = 0;
        for (MaterialInspection record : list) {
            final int currentIndex = index;
            try {
                // 每条记录独立事务提交，任一条失败只回滚自己，避免 PG aborted transaction 毒化整批
                Long exId = requiresNewTemplate.execute(status -> importSingle(record, currentIndex, loginUser, autoCreateException));
                successCount++;
                if (exId != null) {
                    createdExceptionIds.add(exId);
                }
            } catch (BusinessException e) {
                MaterialInspectionImportResultVO.FailItem item = new MaterialInspectionImportResultVO.FailItem();
                item.setIndex(index);
                item.setRecordNo(record.getRecordNo());
                item.setReason(e.getMessage());
                failList.add(item);
            } catch (Exception e) {
                MaterialInspectionImportResultVO.FailItem item = new MaterialInspectionImportResultVO.FailItem();
                item.setIndex(index);
                item.setRecordNo(record.getRecordNo());
                item.setReason("系统错误：" + e.getMessage());
                failList.add(item);
            }
            index++;
        }

        result.setTotalCount(list.size());
        result.setSuccessCount(successCount);
        result.setFailCount(failList.size());
        result.setFailList(failList);
        result.setCreatedExceptionCount(createdExceptionIds.size());
        result.setCreatedExceptionIds(createdExceptionIds);

        log.info("物料检验导入完成：total={}, success={}, fail={}, createdException={}",
                list.size(), successCount, failList.size(), createdExceptionIds.size());
        return result;
    }

    /**
     * 单条物料检验记录导入：在调用方提供的 REQUIRES_NEW 事务内原子执行。
     * 校验 + 入库 + （不合格时）建异常单 在同一事务内提交，
     * 后续记录可看到前序已提交记录，从而避免同批次内单号重复与事务毒化。
     * 事务边界由 importRecords 中的 TransactionTemplate 控制，此处不再加 @Transactional。
     */
    private Long importSingle(MaterialInspection record, int index, LoginUser loginUser, boolean autoCreateException) {
        validateImportRecord(record, index);
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        baseMapper.insert(record);

        Long exId = null;
        if ("不合格".equals(record.getInspectionResult()) && autoCreateException) {
            ExceptionOrder order = exceptionService.createFromMaterialInspection(record, loginUser);
            exId = order.getId();
        }
        return exId;
    }

    @Override
    @Transactional
    public MaterialInspectionReconcileResultVO reconcile(String startDate, String endDate, String plantCode) {
        LoginUser loginUser = getCurrentLoginUser();
        String effectivePlantCode = plantCode != null ? plantCode : loginUser.getPlantCode().name();
        // 跨厂数据权限：非 ALL_PLANTS/R06（canSwitchArea）用户禁止查询其他分公司
        if (plantCode != null && !plantCode.equals(loginUser.getPlantCode().name()) && !loginUser.isCanSwitchArea()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查询其他分公司数据");
        }

        List<MaterialInspection> unqualifiedList = findUnlinkedUnqualified(startDate, endDate, effectivePlantCode);
        List<Long> createdExceptionIds = new ArrayList<>();

        for (MaterialInspection record : unqualifiedList) {
            ExceptionOrder order = exceptionService.createFromMaterialInspection(record, loginUser);
            createdExceptionIds.add(order.getId());
        }

        MaterialInspectionReconcileResultVO result = new MaterialInspectionReconcileResultVO();
        result.setScannedCount(unqualifiedList.size());
        result.setCreatedCount(createdExceptionIds.size());
        result.setCreatedExceptionIds(createdExceptionIds);

        log.info("物料检验对账完成：scanned={}, created={}", unqualifiedList.size(), createdExceptionIds.size());
        return result;
    }

    @Override
    @Transactional
    public MaterialInspection saveWithException(MaterialInspection record, boolean autoCreateException) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        baseMapper.insert(record);

        if ("不合格".equals(record.getInspectionResult()) && autoCreateException) {
            exceptionService.createFromMaterialInspection(record, loginUser);
        }
        return record;
    }

    @Override
    @Transactional
    public boolean save(MaterialInspection entity) {
        return super.save(entity);
    }

    @Override
    @Transactional
    public boolean updateById(MaterialInspection entity) {
        MaterialInspection before = getById(entity.getId());
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "物料检验记录不存在：" + entity.getId());
        }
        String oldResult = before.getInspectionResult();

        boolean updated = super.updateById(entity);
        if (!updated) {
            return false;
        }

        MaterialInspection after = getById(entity.getId());

        // 状态由"合格"降级为"不合格"：同一事务内强制生成关联异常单（强一致，不依赖对账）
        if ("合格".equals(oldResult) && "不合格".equals(after.getInspectionResult())) {
            LoginUser loginUser = getCurrentLoginUser();
            exceptionService.createFromMaterialInspection(after, loginUser);
            log.info("来料检验状态 合格→不合格，已自动创建异常单：materialInspectionId={}", after.getId());
        }
        return true;
    }

    @Override
    public List<MaterialInspection> findUnlinkedUnqualified(String startDate, String endDate, String plantCode) {
        LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(30);
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.atTime(23, 59, 59);

        return baseMapper.selectUnlinkedUnqualified(
                plantCode, "不合格", "来料不良", startAt, endAt);
    }

    private void validateImportRecord(MaterialInspection record, int index) {
        if (record == null) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条记录为空");
        }
        if (!StringUtils.hasText(record.getRecordNo())) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条记录编号不能为空");
        }
        if (!StringUtils.hasText(record.getInspectionResult())) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条检验结果不能为空");
        }
        if (!"合格".equals(record.getInspectionResult()) && !"不合格".equals(record.getInspectionResult())) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "第 " + index + " 条检验结果必须是 合格/不合格");
        }
        // recordNo 唯一性校验
        LambdaQueryWrapper<MaterialInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialInspection::getRecordNo, record.getRecordNo());
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "记录编号已存在：" + record.getRecordNo());
        }
    }

    @Override
    public KeySupplierTrendVO keySupplierTrend(int topN, String startDate, String endDate) {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();

        // 重点供应商 TopN（时间范围内批次量）
        List<SupplierRankItemVO> top = baseMapper.selectKeySuppliers(plantCode, topN, startDate, endDate);
        // 重点供应商时间范围内每日合格率矩阵
        List<KeySupplierTrendRowVO> rows = baseMapper.selectKeySupplierTrend(plantCode, topN, startDate, endDate);

        KeySupplierTrendVO vo = new KeySupplierTrendVO();
        List<KeySupplierTrendVO.KeySupplierItemVO> items = new ArrayList<>();
        for (SupplierRankItemVO s : top) {
            KeySupplierTrendVO.KeySupplierItemVO item = new KeySupplierTrendVO.KeySupplierItemVO();
            item.setSupplierCode(s.getSupplierCode());
            item.setSupplierName(s.getSupplierName());
            item.setTotalBatches(s.getTotalBatches());
            item.setPassRate(s.getPassRate());
            items.add(item);
        }
        vo.setKeySuppliers(items);

        // 组装统一日期轴 + 每家供应商对齐日期轴的合格率序列
        List<String> dates = new ArrayList<>();
        Map<String, KeySupplierTrendVO.KeySupplierSeriesVO> seriesMap = new LinkedHashMap<>();
        for (KeySupplierTrendRowVO row : rows) {
            if (!dates.contains(row.getDate())) {
                dates.add(row.getDate());
            }
            KeySupplierTrendVO.KeySupplierSeriesVO series = seriesMap.computeIfAbsent(
                    row.getSupplierCode(),
                    k -> {
                        KeySupplierTrendVO.KeySupplierSeriesVO ns = new KeySupplierTrendVO.KeySupplierSeriesVO();
                        ns.setSupplierCode(row.getSupplierCode());
                        ns.setSupplierName(row.getSupplierName());
                        ns.setPassRateList(new ArrayList<>());
                        return ns;
                    });
            series.getPassRateList().add(row.getPassRate());
        }
        vo.setDates(dates);
        vo.setSeries(new ArrayList<>(seriesMap.values()));
        return vo;
    }

    @Override
    public List<SupplierRankItemVO> supplierRank(String startDate, String endDate) {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();
        return baseMapper.selectSupplierRank(plantCode, startDate, endDate);
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }

    @Override
    public MaterialInspectionImportPreviewVO previewImport(MultipartFile file) {
        MaterialInspectionImportPreviewVO vo = new MaterialInspectionImportPreviewVO();
        List<MaterialInspection> list = new ArrayList<>();
        List<MaterialInspectionImportPreviewVO.PreviewFailItem> errors = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "导入文件为空");
        }
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();
        Set<String> seenRecordNos = new HashSet<>();
        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "Excel 无工作表");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "Excel 缺少表头行");
            }
            Map<String, Integer> colIndex = new HashMap<>();
            for (Cell c : header) {
                String h = readString(c);
                if (StringUtils.hasText(h)) {
                    colIndex.put(h.trim(), c.getColumnIndex());
                }
            }
            int last = sheet.getLastRowNum();
            for (int r = 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                int rowIndex = r + 1;
                String recordNo = readString(cell(row, colIndex.get("记录编号")));
                if (StringUtils.hasText(recordNo) && !seenRecordNos.add(recordNo)) {
                    addPreviewFail(errors, rowIndex, recordNo, "Excel 内记录编号重复");
                    continue;
                }
                try {
                    MaterialInspection record = parseRow(row, colIndex, plantCode, loginUser);
                    validateImportRecord(record, rowIndex);
                    list.add(record);
                } catch (BusinessException be) {
                    addPreviewFail(errors, rowIndex, recordNo, be.getMessage());
                } catch (Exception ex) {
                    addPreviewFail(errors, rowIndex, recordNo, "系统错误：" + ex.getMessage());
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Excel 解析失败：" + ex.getMessage());
        }
        vo.setList(list);
        vo.setErrors(errors);
        vo.setValidCount(list.size());
        vo.setTotalCount(list.size() + errors.size());
        return vo;
    }

    @Override
    public byte[] generateTemplate() {
        String[] headers = {
                "记录编号", "检验结果", "检验日期", "判定日期", "到货日期",
                "供应商名称", "供应商编码", "物料编码", "物料名称", "规格型号",
                "物料批号", "送检数量", "合格数量", "不良数量", "检验员",
                "审核状态", "不合格描述", "处理方式", "工序号", "入库单号", "采购单号"
        };
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("来料检验导入模板");
            Row header = sheet.createRow(0);
            CellStyle requiredStyle = wb.createCellStyle();
            requiredStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.index);
            requiredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                if (i < 2) {
                    cell.setCellStyle(requiredStyle);
                }
            }
            Row example = sheet.createRow(1);
            String[] exampleValues = {
                    "IMP-2026-0001", "合格", "2026-01-01", "2026-01-01", "2026-01-01",
                    "示例供应商", "SUP-001", "MAT-001", "示例物料", "规格A",
                    "BATCH-001", "100", "98", "2", "张三",
                    "待审核", "外观划伤", "挑选", "P10", "IN-001", "PO-001"
            };
            for (int i = 0; i < exampleValues.length; i++) {
                example.createCell(i).setCellValue(exampleValues[i]);
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "模板生成失败：" + ex.getMessage());
        }
    }

    private MaterialInspection parseRow(Row row, Map<String, Integer> col, String plantCode, LoginUser loginUser) {
        MaterialInspection e = new MaterialInspection();
        e.setRecordNo(readString(cell(row, col.get("记录编号"))));
        e.setInspectionResult(readString(cell(row, col.get("检验结果"))));
        e.setInspectionDate(readDate(cell(row, col.get("检验日期"))));
        e.setJudgementDate(readDate(cell(row, col.get("判定日期"))));
        e.setArrivalDate(readDate(cell(row, col.get("到货日期"))));
        e.setSupplierName(readString(cell(row, col.get("供应商名称"))));
        e.setSupplierCode(readString(cell(row, col.get("供应商编码"))));
        e.setMaterialCode(readString(cell(row, col.get("物料编码"))));
        e.setMaterialName(readString(cell(row, col.get("物料名称"))));
        e.setSpecModel(readString(cell(row, col.get("规格型号"))));
        e.setMaterialBatchNo(readString(cell(row, col.get("物料批号"))));
        e.setSubmittedQty(readDecimal(cell(row, col.get("送检数量"))));
        e.setQualifiedQty(readDecimal(cell(row, col.get("合格数量"))));
        e.setUnqualifiedQty(readDecimal(cell(row, col.get("不良数量"))));
        e.setInspector(readString(cell(row, col.get("检验员"))));
        e.setReviewStatus(readString(cell(row, col.get("审核状态"))));
        e.setDefectDesc(readString(cell(row, col.get("不合格描述"))));
        e.setHandlingMethod(readString(cell(row, col.get("处理方式"))));
        e.setProcessNo(readString(cell(row, col.get("工序号"))));
        e.setInboundNo(readString(cell(row, col.get("入库单号"))));
        e.setPurchaseOrder(readString(cell(row, col.get("采购单号"))));
        return e;
    }

    private Cell cell(Row row, Integer idx) {
        if (idx == null) {
            return null;
        }
        return row.getCell(idx);
    }

    private String readString(Cell c) {
        if (c == null) {
            return "";
        }
        switch (c.getCellType()) {
            case STRING:
                return c.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(c)) {
                    return c.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = c.getNumericCellValue();
                if (d == (long) d) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(c.getBooleanCellValue());
            default:
                return "";
        }
    }

    private BigDecimal readDecimal(Cell c) {
        String s = readString(c);
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate readDate(Cell c) {
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        return toLocalDate(readString(c));
    }

    private LocalDate toLocalDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim().replace('/', '-');
        try {
            return LocalDate.parse(t, DateTimeFormatter.ofPattern("yyyy-M-d"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private boolean isRowEmpty(Row row) {
        for (Cell c : row) {
            if (c != null && StringUtils.hasText(readString(c))) {
                return false;
            }
        }
        return true;
    }

    private void addPreviewFail(List<MaterialInspectionImportPreviewVO.PreviewFailItem> list, int rowIndex, String recordNo, String reason) {
        MaterialInspectionImportPreviewVO.PreviewFailItem f = new MaterialInspectionImportPreviewVO.PreviewFailItem();
        f.setRowIndex(rowIndex);
        f.setRecordNo(recordNo);
        f.setReason(reason);
        list.add(f);
    }
}
