package com.kangli.qms.service.production.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.production.dto.ProductionRepairSaveDTO;
import com.kangli.qms.service.production.dto.ProductionRepairUpdateDTO;
import com.kangli.qms.domain.production.entity.ProductionRepair;
import com.kangli.qms.domain.production.mapper.ProductionRepairMapper;
import com.kangli.qms.service.production.ProductionRepairService;
import com.kangli.qms.domain.production.vo.ProductionRepairImportResultVO;
import com.kangli.qms.domain.production.vo.ProductionRepairVO;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产维修记录 Service 实现。
 */
@Service
public class ProductionRepairServiceImpl
        extends ServiceImpl<ProductionRepairMapper, ProductionRepair>
        implements ProductionRepairService {

    private static final java.util.List<String> EXCLUDED_REPAIR_STATUS =
            java.util.Arrays.asList("待维修未提交", "待维修", "草稿");

    @Override
    public PageResult<ProductionRepairVO> pageQuery(int page, int size, String keyword, String process,
                                                    String startDate, String endDate, String repairStatus,
                                                    String defectPhenomenon, String productName,
                                                    String productBatchOrSn, String productNo, LoginUser user) {
        String plantCode = user.getPlantCode().name();
        Page<ProductionRepair> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<ProductionRepair> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionRepair::getPlantCode, plantCode);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ProductionRepair::getRepairNo, keyword)
                    .or().like(ProductionRepair::getProductNo, keyword)
                    .or().like(ProductionRepair::getProductName, keyword)
                    .or().like(ProductionRepair::getWorkOrderNo, keyword));
        }
        if (StringUtils.hasText(process)) {
            wrapper.eq(ProductionRepair::getProcess, process);
        }
        if (StringUtils.hasText(defectPhenomenon)) {
            wrapper.like(ProductionRepair::getDefectPhenomenon, defectPhenomenon);
        }
        if (StringUtils.hasText(productName)) {
            wrapper.like(ProductionRepair::getProductName, productName);
        }
        if (StringUtils.hasText(productBatchOrSn)) {
            wrapper.like(ProductionRepair::getProductBatchOrSn, productBatchOrSn);
        }
        if (StringUtils.hasText(productNo)) {
            wrapper.like(ProductionRepair::getProductNo, productNo);
        }
        if (StringUtils.hasText(repairStatus)) {
            wrapper.eq(ProductionRepair::getRepairStatus, repairStatus);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(ProductionRepair::getSendRepairDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(ProductionRepair::getSendRepairDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(ProductionRepair::getCreatedAt);
        IPage<ProductionRepair> result = page(pageObj, wrapper);
        List<ProductionRepairVO> voList = new ArrayList<>();
        for (ProductionRepair e : result.getRecords()) {
            voList.add(toVO(e));
        }
        return new PageResult<>(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ProductionRepairVO detail(Long id, LoginUser user) {
        ProductionRepair e = getById(id);
        if (e == null || e.getIsDeleted() != null && e.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (!user.getPlantCode().name().equals(e.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能访问其他分公司的记录");
        }
        return toVO(e);
    }

    @Override
    public Long create(ProductionRepairSaveDTO dto, LoginUser user) {
        String plantCode = user.getPlantCode().name();
        if (!StringUtils.hasText(dto.getProductNo())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品编号必填");
        }
        // repair_no 同分公司唯一
        long cnt = count(new LambdaQueryWrapper<ProductionRepair>()
                .eq(ProductionRepair::getRepairNo, dto.getRepairNo())
                .eq(ProductionRepair::getPlantCode, plantCode)
                .eq(ProductionRepair::getIsDeleted, 0));
        if (cnt > 0) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "维修编号已存在：" + dto.getRepairNo());
        }

        ProductionRepair e = new ProductionRepair();
        copySave(e, dto);
        e.setPlantCode(plantCode);
        e.setPlantName(user.getPlantCode().getChineseName());
        e.setCreatedBy(user.getRealName());
        e.setUpdatedBy(user.getRealName());
        e.setRepairDone(computeRepairDone(dto.getRepairStatus()));
        save(e);
        return e.getId();
    }

    @Override
    public void update(Long id, ProductionRepairUpdateDTO dto, LoginUser user) {
        String plantCode = user.getPlantCode().name();
        ProductionRepair e = getById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (!plantCode.equals(e.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能修改其他分公司的记录");
        }
        if (StringUtils.hasText(dto.getRepairNo()) && !dto.getRepairNo().equals(e.getRepairNo())) {
            long cnt = count(new LambdaQueryWrapper<ProductionRepair>()
                    .eq(ProductionRepair::getRepairNo, dto.getRepairNo())
                    .eq(ProductionRepair::getPlantCode, plantCode)
                    .eq(ProductionRepair::getIsDeleted, 0));
            if (cnt > 0) {
                throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "维修编号已存在：" + dto.getRepairNo());
            }
        }
        mergeUpdate(e, dto);
        e.setUpdatedBy(user.getRealName());
        updateById(e);
    }

    @Override
    public void remove(Long id, LoginUser user) {
        ProductionRepair e = getById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (!user.getPlantCode().name().equals(e.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能删除其他分公司的记录");
        }
        removeById(id); // 逻辑删除（@TableLogic）
    }

    // ===================== Excel 导入 =====================

    @Override
    public ProductionRepairImportResultVO importExcel(MultipartFile file, LoginUser user) {
        String plantCode = user.getPlantCode().name();
        ProductionRepairImportResultVO result = new ProductionRepairImportResultVO();
        List<ProductionRepairImportResultVO.FailItem> failList = new ArrayList<>();
        int successCount = 0;
        int duplicateSkip = 0;
        int pendingCount = 0;
        int rowIndex = 0;
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "导入文件为空");
        }
        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "Excel 无工作表");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "Excel 无表头");
            }
            java.util.Map<String, Integer> colIndex = new java.util.HashMap<>();
            for (Cell c : header) {
                String h = readString(c);
                if (StringUtils.hasText(h)) {
                    colIndex.put(h.trim(), c.getColumnIndex());
                }
            }
            int last = sheet.getLastRowNum();
            for (int r = 1; r <= last; r++) {
                rowIndex = r;
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                try {
                    ProductionRepair e = parseRow(row, colIndex, plantCode, user);
                    // 去重：分公司 + 维修编号
                    long cnt = count(new LambdaQueryWrapper<ProductionRepair>()
                            .eq(ProductionRepair::getRepairNo, e.getRepairNo())
                            .eq(ProductionRepair::getPlantCode, plantCode)
                            .eq(ProductionRepair::getIsDeleted, 0));
                    if (cnt > 0) {
                        duplicateSkip++;
                        continue;
                    }
                    baseMapper.insert(e);
                    successCount++;
                    if (e.getRepairStatus() == null || EXCLUDED_REPAIR_STATUS.contains(e.getRepairStatus())) {
                        pendingCount++;
                    }
                } catch (BusinessException be) {
                    addFail(failList, rowIndex, readRepairNo(row, colIndex), be.getMessage());
                } catch (Exception ex) {
                    addFail(failList, rowIndex, readRepairNo(row, colIndex), "系统错误：" + ex.getMessage());
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Excel 解析失败：" + ex.getMessage());
        }
        result.setTotalCount(rowIndex);
        result.setSuccessCount(successCount);
        result.setDuplicateSkipCount(duplicateSkip);
        result.setPendingCount(pendingCount);
        result.setFailCount(failList.size());
        result.setFailList(failList);
        return result;
    }

    private ProductionRepair parseRow(Row row, java.util.Map<String, Integer> col, String plantCode, LoginUser user) {
        String repairNo = readString(cell(row, col.get("维修编号")));
        if (!StringUtils.hasText(repairNo)) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "维修编号不能为空");
        }
        String qtyStr = readString(cell(row, col.get("不良数量")));
        BigDecimal qty;
        try {
            qty = new BigDecimal(qtyStr.trim());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "不良数量非法：" + qtyStr);
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.IMPORT_RECORD_INVALID, "不良数量必须大于 0");
        }
        ProductionRepair e = new ProductionRepair();
        e.setRepairNo(repairNo);
        e.setFormName(readString(cell(row, col.get("表格名称"))));
        e.setFormNo(readString(cell(row, col.get("表格编号"))));
        e.setProductNo(readString(cell(row, col.get("产品编号"))));
        e.setProductName(readString(cell(row, col.get("产品名称"))));
        e.setSpecModel(readString(cell(row, col.get("规格型号"))));
        e.setWorkOrderNo(readString(cell(row, col.get("生产工单号"))));
        e.setProcess(readString(cell(row, col.get("生产工序"))));
        e.setDefectQty(qty);
        e.setDefectPhenomenon(readString(cell(row, col.get("不良现象"))));
        e.setDefectCode(readString(cell(row, col.get("不良代码"))));
        e.setProductBatchOrSn(readString(cell(row, col.get("产品批号/序列号"))));
        // 日期
        e.setCreatedAt(toDateTime(readString(cell(row, col.get("创建日期")))));
        e.setSendRepairDate(toDate(readString(cell(row, col.get("送修日期")))));
        e.setRepairDate(toDate(readString(cell(row, col.get("维修日期")))));
        e.setAuditDate(toDate(readString(cell(row, col.get("审核日期")))));
        // 状态文本
        e.setRepairStatus(readString(cell(row, col.get("状态："))));
        e.setRepairJudgmentResult(readString(cell(row, col.get("维修判定结果"))));
        e.setRepairRecord(null);
        e.setSendRepairer(readString(cell(row, col.get("送修人"))));
        e.setRepairer(readString(cell(row, col.get("维修人"))));
        e.setAuditor(readString(cell(row, col.get("审核人"))));
        // 审核状态 0/1 → 文本
        String auditRaw = readString(cell(row, col.get("审核状态")));
        e.setAuditStatus("1".equals(auditRaw.trim()) ? "已审核" : "待审核");
        // 维修状态 0/1 → 数值（非冗余）
        String repairDoneRaw = readString(cell(row, col.get("维修状态")));
        e.setRepairDone("1".equals(repairDoneRaw.trim()) ? 1 : 0);
        // 分公司与操作人
        e.setPlantCode(plantCode);
        e.setPlantName(user.getPlantCode().getChineseName());
        String creator = readString(cell(row, col.get("创建人")));
        e.setCreatedBy(StringUtils.hasText(creator) ? creator : user.getRealName());
        e.setUpdatedBy(e.getCreatedBy());
        return e;
    }

    // ===================== 工具方法 =====================

    private Integer computeRepairDone(String repairStatus) {
        if ("已完成".equals(repairStatus)) {
            return 1;
        }
        return 0;
    }

    private void copySave(ProductionRepair e, ProductionRepairSaveDTO d) {
        e.setRepairNo(d.getRepairNo());
        e.setFormName(d.getFormName());
        e.setFormNo(d.getFormNo());
        e.setProductNo(d.getProductNo());
        e.setProductName(d.getProductName());
        e.setSpecModel(d.getSpecModel());
        e.setWorkOrderNo(d.getWorkOrderNo());
        e.setProductBatchOrSn(d.getProductBatchOrSn());
        e.setProcess(d.getProcess());
        e.setDefectQty(d.getDefectQty());
        e.setDefectPhenomenon(d.getDefectPhenomenon());
        e.setDefectCode(d.getDefectCode());
        e.setSendRepairDate(d.getSendRepairDate());
        e.setRepairDate(d.getRepairDate());
        e.setRepairStatus(d.getRepairStatus());
        e.setRepairJudgmentResult(d.getRepairJudgmentResult());
        e.setRepairRecord(d.getRepairRecord());
        e.setSendRepairer(d.getSendRepairer());
        e.setRepairer(d.getRepairer());
        e.setAuditor(d.getAuditor());
        e.setAuditStatus(d.getAuditStatus());
        e.setAuditDate(d.getAuditDate());
        e.setRemark(d.getRemark());
    }

    private void mergeUpdate(ProductionRepair e, ProductionRepairUpdateDTO d) {
        if (StringUtils.hasText(d.getRepairNo())) e.setRepairNo(d.getRepairNo());
        if (StringUtils.hasText(d.getFormName())) e.setFormName(d.getFormName());
        if (StringUtils.hasText(d.getFormNo())) e.setFormNo(d.getFormNo());
        if (StringUtils.hasText(d.getProductNo())) e.setProductNo(d.getProductNo());
        if (StringUtils.hasText(d.getProductName())) e.setProductName(d.getProductName());
        if (StringUtils.hasText(d.getSpecModel())) e.setSpecModel(d.getSpecModel());
        if (StringUtils.hasText(d.getWorkOrderNo())) e.setWorkOrderNo(d.getWorkOrderNo());
        if (StringUtils.hasText(d.getProductBatchOrSn())) e.setProductBatchOrSn(d.getProductBatchOrSn());
        if (StringUtils.hasText(d.getProcess())) e.setProcess(d.getProcess());
        if (d.getDefectQty() != null) e.setDefectQty(d.getDefectQty());
        if (StringUtils.hasText(d.getDefectPhenomenon())) e.setDefectPhenomenon(d.getDefectPhenomenon());
        if (StringUtils.hasText(d.getDefectCode())) e.setDefectCode(d.getDefectCode());
        if (StringUtils.hasText(d.getSendRepairDate() != null ? d.getSendRepairDate().toString() : "")) e.setSendRepairDate(d.getSendRepairDate());
        if (d.getRepairDate() != null) e.setRepairDate(d.getRepairDate());
        if (StringUtils.hasText(d.getRepairStatus())) e.setRepairStatus(d.getRepairStatus());
        if (StringUtils.hasText(d.getRepairJudgmentResult())) e.setRepairJudgmentResult(d.getRepairJudgmentResult());
        if (StringUtils.hasText(d.getRepairRecord())) e.setRepairRecord(d.getRepairRecord());
        if (StringUtils.hasText(d.getSendRepairer())) e.setSendRepairer(d.getSendRepairer());
        if (StringUtils.hasText(d.getRepairer())) e.setRepairer(d.getRepairer());
        if (StringUtils.hasText(d.getAuditor())) e.setAuditor(d.getAuditor());
        if (StringUtils.hasText(d.getAuditStatus())) e.setAuditStatus(d.getAuditStatus());
        if (d.getAuditDate() != null) e.setAuditDate(d.getAuditDate());
        if (StringUtils.hasText(d.getRemark())) e.setRemark(d.getRemark());
    }

    private ProductionRepairVO toVO(ProductionRepair e) {
        ProductionRepairVO v = new ProductionRepairVO();
        v.setId(e.getId());
        v.setRepairNo(e.getRepairNo());
        v.setFormName(e.getFormName());
        v.setFormNo(e.getFormNo());
        v.setProductNo(e.getProductNo());
        v.setProductName(e.getProductName());
        v.setSpecModel(e.getSpecModel());
        v.setWorkOrderNo(e.getWorkOrderNo());
        v.setProductBatchOrSn(e.getProductBatchOrSn());
        v.setProcess(e.getProcess());
        v.setDefectQty(e.getDefectQty());
        v.setDefectPhenomenon(e.getDefectPhenomenon());
        v.setDefectCode(e.getDefectCode());
        v.setSendRepairDate(e.getSendRepairDate());
        v.setRepairDate(e.getRepairDate());
        v.setRepairJudgmentResult(e.getRepairJudgmentResult());
        v.setRepairStatus(e.getRepairStatus());
        v.setRepairRecord(e.getRepairRecord());
        v.setSendRepairer(e.getSendRepairer());
        v.setRepairer(e.getRepairer());
        v.setAuditor(e.getAuditor());
        v.setAuditStatus(e.getAuditStatus());
        v.setAuditDate(e.getAuditDate());
        v.setRemark(e.getRemark());
        v.setRepairDone(e.getRepairDone());
        v.setPlantCode(e.getPlantCode());
        v.setPlantName(e.getPlantName());
        v.setCreatedBy(e.getCreatedBy());
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        return v;
    }

    // ---- Excel 单元格读取 ----
    private Cell cell(Row row, Integer idx) {
        if (idx == null) return null;
        return row.getCell(idx);
    }
    private String readString(Cell c) {
        if (c == null) return "";
        switch (c.getCellType()) {
            case STRING: return c.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(c)) {
                    return c.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN: return String.valueOf(c.getBooleanCellValue());
            default: return "";
        }
    }
    private String readRepairNo(Row row, java.util.Map<String, Integer> col) {
        return readString(cell(row, col.get("维修编号")));
    }
    private void addFail(List<ProductionRepairImportResultVO.FailItem> list, int rowIndex, String repairNo, String reason) {
        ProductionRepairImportResultVO.FailItem f = new ProductionRepairImportResultVO.FailItem();
        f.setRowIndex(rowIndex);
        f.setRepairNo(repairNo);
        f.setReason(reason);
        list.add(f);
    }
    private LocalDate toDate(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim().replace('/', '-');
        try { return LocalDate.parse(t, DateTimeFormatter.ofPattern("yyyy-M-d")); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd")); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(t); } catch (DateTimeParseException ex) { return null; }
    }
    private LocalDateTime toDateTime(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim().replace('/', '-');
        try { return LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-M-d H:mm")); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss")); } catch (DateTimeParseException ignored) {}
        LocalDate d = toDate(s);
        return d == null ? null : d.atStartOfDay();
    }
}
