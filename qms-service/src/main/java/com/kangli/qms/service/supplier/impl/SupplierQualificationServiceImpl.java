package com.kangli.qms.service.supplier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.entity.SupplierQualification;
import com.kangli.qms.domain.supplier.mapper.SupplierQualificationMapper;
import com.kangli.qms.domain.supplier.vo.SupplierQualificationExpiryVO;
import com.kangli.qms.domain.supplier.vo.SupplierQualificationVO;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.supplier.SupplierQualificationService;
import com.kangli.qms.service.supplier.dto.SupplierQualificationCreateDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商资质证照业务实现。
 */
@Service
public class SupplierQualificationServiceImpl implements SupplierQualificationService {

    /** 预警阈值（天） */
    private static final long WARN_URGENT = 30L;
    private static final long WARN_ALERT = 60L;
    private static final long WARN_NOTICE = 90L;

    @Resource
    private SupplierQualificationMapper qualificationMapper;

    @Resource
    private com.kangli.qms.domain.supplier.mapper.SupplierMapper supplierMapper;

    @Resource
    private AdminService adminService;

    @Resource
    private NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierQualificationVO create(SupplierQualificationCreateDTO dto) {
        SupplierQualification entity = toEntity(dto, null);
        enrichSupplierInfo(entity);
        LoginUser user = LoginUserHolder.get();
        if (user != null) {
            entity.setCreatedBy(user.getRealName() != null ? user.getRealName() : user.getAccount());
            entity.setUpdatedBy(entity.getCreatedBy());
            entity.setPlantCode(user.getPlantCode() == null ? null : user.getPlantCode().name());
            entity.setPlantName(user.getPlantCode() == null ? null : user.getPlantCode().getChineseName());
        }
        qualificationMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierQualificationVO update(Long id, SupplierQualificationCreateDTO dto) {
        SupplierQualification exist = qualificationMapper.selectById(id);
        if (exist == null || (exist.getIsDeleted() != null && exist.getIsDeleted() == 1)) {
            throw new com.kangli.qms.common.BusinessException(
                    com.kangli.qms.common.ResultCode.BAD_REQUEST, "资质不存在");
        }
        SupplierQualification entity = toEntity(dto, id);
        enrichSupplierInfo(entity);
        LoginUser user = LoginUserHolder.get();
        if (user != null) {
            entity.setUpdatedBy(user.getRealName() != null ? user.getRealName() : user.getAccount());
        }
        qualificationMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SupplierQualification exist = qualificationMapper.selectById(id);
        if (exist == null) {
            throw new com.kangli.qms.common.BusinessException(
                    com.kangli.qms.common.ResultCode.BAD_REQUEST, "资质不存在");
        }
        qualificationMapper.deleteById(id);
    }

    @Override
    public List<SupplierQualificationVO> listBySupplier(Long supplierId) {
        if (supplierId == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<SupplierQualification> w = new LambdaQueryWrapper<>();
        w.eq(SupplierQualification::getSupplierId, supplierId)
                .orderByAsc(SupplierQualification::getExpireDate);
        return qualificationMapper.selectList(w).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<SupplierQualificationVO> listPage(int page, int size, Long supplierId,
                                                        String warnLevel, String keyword) {
        LambdaQueryWrapper<SupplierQualification> w = new LambdaQueryWrapper<>();
        if (supplierId != null) {
            w.eq(SupplierQualification::getSupplierId, supplierId);
        }
        if (StringUtils.isNotBlank(keyword)) {
            w.and(q -> q.like(SupplierQualification::getCertType, keyword)
                    .or().like(SupplierQualification::getCertNo, keyword)
                    .or().like(SupplierQualification::getSupplierName, keyword));
        }
        w.orderByAsc(SupplierQualification::getExpireDate);
        Page<SupplierQualification> p = qualificationMapper.selectPage(
                new Page<>(page, size), w);
        List<SupplierQualificationVO> list = p.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        // 预警级别过滤（在内存，因属于派生字段）
        if (StringUtils.isNotBlank(warnLevel)) {
            list = list.stream()
                    .filter(v -> warnLevel.equals(v.getWarnLevel()))
                    .collect(Collectors.toList());
        }
        Page<SupplierQualificationVO> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(list);
        return PageResult.of(vp);
    }

    @Override
    public List<SupplierQualificationExpiryVO> scanExpiring() {
        // 抓取 90 天内到期 或 已过期（未逻辑删除）的资质
        LocalDate horizon = LocalDate.now().plusDays(WARN_NOTICE);
        LambdaQueryWrapper<SupplierQualification> w = new LambdaQueryWrapper<>();
        w.isNotNull(SupplierQualification::getExpireDate)
                .eq(SupplierQualification::getLongTerm, 0)
                .le(SupplierQualification::getExpireDate, horizon)
                .orderByAsc(SupplierQualification::getExpireDate);
        return qualificationMapper.selectList(w).stream()
                .map(this::toExpiryVO)
                .collect(Collectors.toList());
    }

    /**
     * 定时预警：扫描并给分公司质量相关角色推送通知。
     */
    public void pushExpiryWarnings() {
        List<SupplierQualificationExpiryVO> items = scanExpiring();
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        for (SupplierQualificationExpiryVO item : items) {
            // 每家分公司质量经理/质量工程师接收
            List<AdminUserVO> receivers = adminService.listUsers().stream()
                    .filter(u -> "1".equals(String.valueOf(u.getStatus()))
                            && item.getPlantCode() != null
                            && item.getPlantCode().equals(u.getPlantCode())
                            && ("R05".equals(u.getRoleCode()) || "R06".equals(u.getRoleCode())))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(receivers)) {
                continue;
            }
            String level = "已过期".equals(item.getWarnLevel()) ? "严重"
                    : ("紧急".equals(item.getWarnLevel()) ? "严重"
                    : ("预警".equals(item.getWarnLevel()) ? "警告" : "提醒"));
            for (AdminUserVO r : receivers) {
                NotificationCreateDTO n = new NotificationCreateDTO();
                n.setUserId(r.getId());
                n.setType("quality");
                n.setLevel(level);
                n.setBusinessType("supplierQualification");
                n.setBusinessId(item.getQualificationId());
                n.setPlantCode(item.getPlantCode());
                n.setTitle("供应商资质" + item.getWarnLevel() + "：" + item.getSupplierName());
                n.setContent("供应商【" + item.getSupplierName() + "】的" + item.getCertType()
                        + "（证照编号：" + item.getCertNo() + "）将于 " + item.getExpireDate()
                        + " 到期，剩余 " + item.getDaysToExpire() + " 天，请及时更新。");
                n.setExtraData("{\"supplierId\":" + item.getSupplierId() + ",\"qualificationId\":" + item.getQualificationId() + "}");
                notificationService.createNotification(n);
            }
        }
    }

    // ---------------- 私有方法 ----------------

    private void enrichSupplierInfo(SupplierQualification entity) {
        if (entity.getSupplierId() == null) {
            return;
        }
        Supplier s = supplierMapper.selectById(entity.getSupplierId());
        if (s != null) {
            entity.setSupplierCode(s.getSupplierCode());
            entity.setSupplierName(s.getSupplierName());
        }
    }

    private SupplierQualification toEntity(SupplierQualificationCreateDTO dto, Long id) {
        SupplierQualification e = new SupplierQualification();
        if (id != null) {
            e.setId(id);
        }
        e.setSupplierId(dto.getSupplierId());
        e.setCertType(dto.getCertType());
        e.setCertNo(dto.getCertNo());
        e.setIssuer(dto.getIssuer());
        e.setIssueDate(parseDate(dto.getIssueDate()));
        e.setExpireDate(parseDate(dto.getExpireDate()));
        e.setLongTerm(dto.getLongTerm() != null && dto.getLongTerm() ? (short) 1 : (short) 0);
        e.setRemark(dto.getRemark());
        if (!CollectionUtils.isEmpty(dto.getFileUrls())) {
            e.setFileUrls(String.join(",", dto.getFileUrls()));
        }
        return e;
    }

    private LocalDate parseDate(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        return LocalDate.parse(s);
    }

    private SupplierQualificationVO toVO(SupplierQualification e) {
        SupplierQualificationVO vo = new SupplierQualificationVO();
        vo.setQualification(e);
        vo.setDaysToExpire(computeDays(e));
        vo.setWarnLevel(computeWarnLevel(e));
        return vo;
    }

    private SupplierQualificationExpiryVO toExpiryVO(SupplierQualification e) {
        SupplierQualificationExpiryVO vo = new SupplierQualificationExpiryVO();
        vo.setQualificationId(e.getId());
        vo.setSupplierId(e.getSupplierId());
        vo.setSupplierName(e.getSupplierName());
        vo.setCertType(e.getCertType());
        vo.setCertNo(e.getCertNo());
        vo.setExpireDate(e.getExpireDate() == null ? null : e.getExpireDate().toString());
        vo.setDaysToExpire(computeDays(e));
        vo.setWarnLevel(computeWarnLevel(e));
        vo.setPlantCode(e.getPlantCode());
        vo.setPlantName(e.getPlantName());
        return vo;
    }

    private Long computeDays(SupplierQualification e) {
        if (e.getExpireDate() == null || (e.getLongTerm() != null && e.getLongTerm() == 1)) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), e.getExpireDate());
    }

    private String computeWarnLevel(SupplierQualification e) {
        Long days = computeDays(e);
        if (days == null) {
            return "正常";
        }
        if (days < 0) {
            return "已过期";
        }
        if (days <= WARN_URGENT) {
            return "紧急";
        }
        if (days <= WARN_ALERT) {
            return "预警";
        }
        if (days <= WARN_NOTICE) {
            return "提醒";
        }
        return "正常";
    }
}
