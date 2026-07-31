package com.kangli.qms.service.finishedgoods.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.service.finishedgoods.FinishedGoodsInspectionService;
import com.kangli.qms.service.finishedgoods.dto.FinishedGoodsInspectionResponse;
import com.kangli.qms.service.trace.NaturalKeyConflictMessageResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * M1-4 成品入库检验 Service 实现。
 * <p>补全双签状态机（品管审核 → 管代批准）强制流转、reportNo 唯一性校验，并统一返回响应 DTO（红线 #2）。</p>
 */
@Slf4j
@Service
public class FinishedGoodsInspectionServiceImpl
        extends ServiceImpl<FinishedGoodsInspectionMapper, FinishedGoodsInspection>
        implements FinishedGoodsInspectionService {

    private static final String PENDING = "待审核";
    private static final String APPROVED = "已审核";
    private static final String REJECTED = "驳回";

    public FinishedGoodsInspectionServiceImpl() {
    }

    @Override
    public FinishedGoodsInspectionResponse detail(Long id) {
        FinishedGoodsInspection record = getById(id);
        if (record == null || isDeleted(record)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return toResponse(record);
    }

    @Override
    public PageResult<FinishedGoodsInspectionResponse> page(Page<FinishedGoodsInspection> page,
                                                            LambdaQueryWrapper<FinishedGoodsInspection> wrapper) {
        Page<FinishedGoodsInspection> result = super.page(page, wrapper);
        List<FinishedGoodsInspectionResponse> list = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinishedGoodsInspectionResponse create(FinishedGoodsInspection record, LoginUser loginUser) {
        assertReportNoUnique(record.getReportNo(), null);
        record.setCategory(normalizeCategory(record.getCategory()));
        applyDualSign(record, null, loginUser);
        try {
            if (!save(record)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "保存失败");
            }
        } catch (DuplicateKeyException e) {
            throw naturalKeyConflict(e);
        }
        return toResponse(getById(record.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinishedGoodsInspectionResponse update(Long id, FinishedGoodsInspection record, LoginUser loginUser) {
        FinishedGoodsInspection old = getById(id);
        if (old == null || isDeleted(old)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        assertReportNoUnique(record.getReportNo(), id);
        record.setId(id);
        record.setVersion(old.getVersion());
        record.setPlantCode(old.getPlantCode());
        record.setPlantName(old.getPlantName());
        record.setCreatedBy(old.getCreatedBy());
        record.setCreatedAt(old.getCreatedAt());
        record.setIsDeleted(old.getIsDeleted());
        record.setCategory(StringUtils.hasText(record.getCategory())
                ? normalizeCategory(record.getCategory())
                : normalizeCategory(old.getCategory()));
        applyDualSign(record, old, loginUser);
        try {
            if (!updateById(record)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "更新失败");
            }
        } catch (DuplicateKeyException e) {
            throw naturalKeyConflict(e);
        }
        return toResponse(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinishedGoodsInspection old = getById(id);
        if (old == null || isDeleted(old)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        if (!removeById(id)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "删除失败");
        }
    }

    // ===== reportNo 唯一性校验 =====
    private void assertReportNoUnique(String reportNo, Long excludeId) {
        if (!StringUtils.hasText(reportNo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报告编号 reportNo 不能为空");
        }
        LambdaQueryWrapper<FinishedGoodsInspection> w = new LambdaQueryWrapper<>();
        w.eq(FinishedGoodsInspection::getReportNo, reportNo);
        w.eq(FinishedGoodsInspection::getIsDeleted, 0);
        if (excludeId != null) {
            w.ne(FinishedGoodsInspection::getId, excludeId);
        }
        if (count(w) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报告编号 " + reportNo + " 已存在，不可重复");
        }
    }

    // ===== 双签状态机 =====
    private void applyDualSign(FinishedGoodsInspection record, FinishedGoodsInspection old, LoginUser loginUser) {
        if (old == null) {
            // 新建强制待审核，忽略请求体中的越权状态值
            record.setQcReview(PENDING);
            record.setMgrApproval(PENDING);
            return;
        }
        String oldQc = old.getQcReview();
        String oldMgr = old.getMgrApproval();
        // 更新时若请求体未携带状态字段，沿用旧值，避免误清空已完成的双签
        String newQc = record.getQcReview() != null ? record.getQcReview() : oldQc;
        String newMgr = record.getMgrApproval() != null ? record.getMgrApproval() : oldMgr;

        validateTransition("品管审核(qcReview)", oldQc, newQc);
        validateTransition("管代批准(mgrApproval)", oldMgr, newMgr);

        // 管代批准为「已审核」前，品管必须已「已审核」（不可跳过品管）
        if (APPROVED.equals(newMgr) && !APPROVED.equals(newQc)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "管代批准为「已审核」前，品管必须先审核通过");
        }
        // 品管未通过时，管代不可保持已审核（级联重置）
        if (!APPROVED.equals(newQc) && APPROVED.equals(oldMgr)) {
            record.setMgrApproval(PENDING);
            newMgr = PENDING;
        }

        if (APPROVED.equals(newQc) && !APPROVED.equals(oldQc)) {
            record.setQcReviewer(loginUser.getRealName());
            record.setQcReviewTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        } else if (!APPROVED.equals(newQc)) {
            record.setQcReviewer(null);
            record.setQcReviewTime(null);
        }

        if (APPROVED.equals(newMgr) && !APPROVED.equals(oldMgr)) {
            record.setMgrRepresentative(loginUser.getRealName());
            record.setMgrApprovalTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        } else if (!APPROVED.equals(newMgr)) {
            record.setMgrRepresentative(null);
            record.setMgrApprovalTime(null);
        }
    }

    private void validateTransition(String field, String oldStatus, String newStatus) {
        String from = (oldStatus == null) ? PENDING : oldStatus;
        String to = (newStatus == null) ? PENDING : newStatus;
        if (from.equals(to)) {
            return;
        }
        boolean allowed;
        switch (from) {
            case PENDING:
                allowed = APPROVED.equals(to) || REJECTED.equals(to);
                break;
            case APPROVED:
                allowed = REJECTED.equals(to);
                break;
            case REJECTED:
                allowed = PENDING.equals(to);
                break;
            default:
                allowed = false;
        }
        if (!allowed) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    field + "状态流转不合法：" + from + " → " + to);
        }
    }

    private boolean isDeleted(FinishedGoodsInspection e) {
        return e.getIsDeleted() != null && e.getIsDeleted() == 1;
    }

    private String normalizeCategory(String category) {
        return "半成品".equals(category) ? "半成品" : "成品";
    }

    private BusinessException naturalKeyConflict(DuplicateKeyException e) {
        String message = NaturalKeyConflictMessageResolver.resolve(e.getMostSpecificCause().getMessage());
        return new BusinessException(ResultCode.BAD_REQUEST,
                message != null ? message : "保存数据时发生重复冲突");
    }

    private FinishedGoodsInspectionResponse toResponse(FinishedGoodsInspection e) {
        FinishedGoodsInspectionResponse r = new FinishedGoodsInspectionResponse();
        BeanUtils.copyProperties(e, r);
        return r;
    }

    @Override
    @Transactional
    public boolean save(FinishedGoodsInspection entity) {
        return super.save(entity);
    }

    @Override
    @Transactional
    public boolean updateById(FinishedGoodsInspection entity) {
        return super.updateById(entity);
    }
}
