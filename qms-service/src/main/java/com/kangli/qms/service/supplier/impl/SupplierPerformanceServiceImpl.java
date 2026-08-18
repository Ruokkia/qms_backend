package com.kangli.qms.service.supplier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.entity.SupplierQualification;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierQualificationMapper;
import com.kangli.qms.domain.supplier.vo.SupplierPerfParetoVO;
import com.kangli.qms.domain.supplier.vo.SupplierPerfTrendVO;
import com.kangli.qms.domain.supplier.vo.SupplierPerformanceVO;
import com.kangli.qms.service.supplier.SupplierPerformanceService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商绩效评审业务实现。
 * <p>指标：来料合格率、不良率、整改及时率、资质合规率、交付及时率(待接入)，
 * 加权综合评分并映射 A/B/C/D 等级。</p>
 */
@Service
public class SupplierPerformanceServiceImpl implements SupplierPerformanceService {

    /** 综合评分权重 */
    private static final BigDecimal W_PASS = new BigDecimal("0.40");
    private static final BigDecimal W_RECTIFY = new BigDecimal("0.25");
    private static final BigDecimal W_COMPLIANCE = new BigDecimal("0.20");
    private static final BigDecimal W_DELIVERY = new BigDecimal("0.15");

    /** 视为合规所必须具备的资质类型 */
    private static final List<String> REQUIRED_CERT_TYPES = List.of("营业执照", "ISO13485");

    @Resource
    private SupplierMapper supplierMapper;
    @Resource
    private MaterialInspectionMapper materialInspectionMapper;
    @Resource
    private ExceptionOrderMapper exceptionOrderMapper;
    @Resource
    private SupplierQualificationMapper qualificationMapper;

    @Override
    public List<SupplierPerformanceVO> rank() {
        String plant = currentPlant();
        List<Supplier> suppliers = supplierMapper.selectList(
                new LambdaQueryWrapper<Supplier>().eq(Supplier::getPlantCode, plant)
                        .eq(Supplier::getStatus, "启用"));

        // 预加载统计数据
        List<MaterialInspection> inspections = materialInspectionMapper.selectList(
                new LambdaQueryWrapper<MaterialInspection>().eq(MaterialInspection::getPlantCode, plant));
        List<ExceptionOrder> exceptions = exceptionOrderMapper.selectList(
                new LambdaQueryWrapper<ExceptionOrder>().eq(ExceptionOrder::getPlantCode, plant));
        List<SupplierQualification> quals = qualificationMapper.selectList(
                new LambdaQueryWrapper<SupplierQualification>().eq(SupplierQualification::getPlantCode, plant));

        Map<String, List<MaterialInspection>> inspBySupplier = inspections.stream()
                .filter(i -> i.getSupplierCode() != null)
                .collect(Collectors.groupingBy(MaterialInspection::getSupplierCode));
        Map<Long, List<ExceptionOrder>> excBySupplier = exceptions.stream()
                .filter(e -> e.getSupplierId() != null)
                .collect(Collectors.groupingBy(ExceptionOrder::getSupplierId));
        Map<Long, List<SupplierQualification>> qualBySupplier = quals.stream()
                .filter(q -> q.getSupplierId() != null)
                .collect(Collectors.groupingBy(SupplierQualification::getSupplierId));

        List<SupplierPerformanceVO> result = new ArrayList<>();
        for (Supplier s : suppliers) {
            SupplierPerformanceVO vo = new SupplierPerformanceVO();
            vo.setSupplierId(s.getId());
            vo.setSupplierName(s.getSupplierName());
            vo.setSupplierCode(s.getSupplierCode());
            vo.setRiskLevel(s.getRiskLevel());

            // 来料合格率 / 不良率
            List<MaterialInspection> insp = inspBySupplier.getOrDefault(s.getSupplierCode(), List.of());
            int total = insp.size();
            long pass = insp.stream().filter(i -> "合格".equals(i.getInspectionResult())).count();
            long unqual = total - pass;
            vo.setTotalBatches(total);
            BigDecimal passRate = total == 0 ? BigDecimal.ZERO
                    : new BigDecimal(pass).multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
            vo.setPassRate(passRate);
            vo.setUnqualifiedRate(total == 0 ? BigDecimal.ZERO
                    : new BigDecimal(unqual).multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP));

            // 整改及时率
            List<ExceptionOrder> exc = excBySupplier.getOrDefault(s.getId(), List.of());
            int excTotal = exc.size();
            long onTime = exc.stream()
                    .filter(e -> e.getClosedAt() != null && e.getDeadline() != null
                            && !e.getClosedAt().toLocalDate().isAfter(e.getDeadline()))
                    .count();
            BigDecimal rectifyRate = excTotal == 0 ? BigDecimal.valueOf(100)
                    : new BigDecimal(onTime).multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(excTotal), 2, RoundingMode.HALF_UP);
            vo.setRectifyOnTimeRate(rectifyRate);

            // 资质合规率
            List<SupplierQualification> sq = qualBySupplier.getOrDefault(s.getId(), List.of());
            long hasRequired = REQUIRED_CERT_TYPES.stream()
                    .filter(t -> sq.stream().anyMatch(q -> t.equals(q.getCertType()))).count();
            BigDecimal compliance = new BigDecimal(hasRequired)
                    .multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(REQUIRED_CERT_TYPES.size()), 2, RoundingMode.HALF_UP);
            vo.setComplianceRate(compliance);

            // 交付及时率（数据未接入）
            vo.setDeliveryOnTimeRate(null);

            // 综合评分
            BigDecimal score = passRate.multiply(W_PASS)
                    .add(rectifyRate.multiply(W_RECTIFY))
                    .add(compliance.multiply(W_COMPLIANCE));
            // 交付及时率缺省按 100 计入权重（待接入后替换）
            score = score.add(BigDecimal.valueOf(100).multiply(W_DELIVERY));
            vo.setScore(score.setScale(2, RoundingMode.HALF_UP));
            vo.setGrade(gradeOf(score));
            result.add(vo);
        }
        result.sort(Comparator.comparing(SupplierPerformanceVO::getScore).reversed());
        return result;
    }

    @Override
    public List<SupplierPerfTrendVO> trend() {
        String plant = currentPlant();
        List<MaterialInspection> inspections = materialInspectionMapper.selectList(
                new LambdaQueryWrapper<MaterialInspection>().eq(MaterialInspection::getPlantCode, plant)
                        .isNotNull(MaterialInspection::getInspectionDate));
        Map<String, List<MaterialInspection>> byMonth = inspections.stream()
                .collect(Collectors.groupingBy(i -> i.getInspectionDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))));
        List<SupplierPerfTrendVO> list = new ArrayList<>();
        for (Map.Entry<String, List<MaterialInspection>> e : byMonth.entrySet()) {
            List<MaterialInspection> items = e.getValue();
            int total = items.size();
            long pass = items.stream().filter(i -> "合格".equals(i.getInspectionResult())).count();
            BigDecimal avgPass = total == 0 ? BigDecimal.ZERO
                    : new BigDecimal(pass).multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
            SupplierPerfTrendVO vo = new SupplierPerfTrendVO();
            vo.setMonth(e.getKey());
            vo.setAvgPassRate(avgPass);
            vo.setBatches(total);
            vo.setAvgScore(avgPass);
            list.add(vo);
        }
        list.sort(Comparator.comparing(SupplierPerfTrendVO::getMonth));
        return list;
    }

    @Override
    public List<SupplierPerfParetoVO> pareto() {
        String plant = currentPlant();
        List<MaterialInspection> inspections = materialInspectionMapper.selectList(
                new LambdaQueryWrapper<MaterialInspection>().eq(MaterialInspection::getPlantCode, plant)
                        .ne(MaterialInspection::getInspectionResult, "合格"));
        Map<String, Long> cnt = inspections.stream()
                .map(i -> normalizeDefect(i.getDefectDesc()))
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        long total = cnt.values().stream().mapToLong(Long::longValue).sum();
        List<SupplierPerfParetoVO> list = cnt.entrySet().stream()
                .map(en -> {
                    SupplierPerfParetoVO v = new SupplierPerfParetoVO();
                    v.setCategory(en.getKey());
                    v.setCount(en.getValue());
                    return v;
                })
                .sorted(Comparator.comparing(SupplierPerfParetoVO::getCount).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        long cum = 0;
        for (SupplierPerfParetoVO v : list) {
            cum += v.getCount();
            v.setCumulativeRate(total == 0 ? BigDecimal.ZERO
                    : new BigDecimal(cum).multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP));
        }
        return list;
    }

    // ---------------- 私有方法 ----------------

    private String normalizeDefect(String desc) {
        if (desc == null || desc.isBlank()) {
            return "其他";
        }
        String d = desc.trim();
        if (d.contains("尺寸")) return "尺寸不良";
        if (d.contains("外观")) return "外观不良";
        if (d.contains("性能")) return "性能不达标";
        if (d.contains("包装")) return "包装不良";
        if (d.contains("材质")) return "材质不良";
        return d.length() > 12 ? d.substring(0, 12) + "…" : d;
    }

    private String gradeOf(BigDecimal score) {
        if (score.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (score.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (score.compareTo(new BigDecimal("70")) >= 0) return "C";
        return "D";
    }

    private String currentPlant() {
        LoginUser u = LoginUserHolder.get();
        if (u == null || u.getPlantCode() == null) {
            throw new com.kangli.qms.common.BusinessException(
                    com.kangli.qms.common.ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return u.getPlantCode().name();
    }
}
