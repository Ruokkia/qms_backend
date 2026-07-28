package com.kangli.qms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.entity.RectificationPlan;
import com.kangli.qms.service.AuditLogService;
import com.kangli.qms.service.RectificationPlanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * M2-5 整改计划 Controller。
 * <p>路径：/api/v1/rectification-plans</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rectification-plans")
@Api(tags = "M2-整改计划")
public class RectificationPlanController {

    private final RectificationPlanService rectificationPlanService;
    private final AuditLogService auditLogService;

    public RectificationPlanController(RectificationPlanService rectificationPlanService,
                                       AuditLogService auditLogService) {
        this.rectificationPlanService = rectificationPlanService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @ApiOperation(value = "按异常单查询整改计划")
    public R<PageResult<RectificationPlan>> list(
            @RequestParam Long exceptionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<RectificationPlan> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<RectificationPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RectificationPlan::getExceptionId, exceptionId)
                .eq(RectificationPlan::getPlantCode, loginUser.getPlantCode().name())
                .orderByDesc(RectificationPlan::getCreatedAt);
        return R.ok(PageResult.of(rectificationPlanService.page(pageObj, wrapper)));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "整改计划详情")
    public R<RectificationPlan> detail(@PathVariable Long id) {
        RectificationPlan plan = rectificationPlanService.getById(id);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "整改计划不存在");
        }
        return R.ok(plan);
    }

    @PostMapping
    @ApiOperation(value = "新增整改计划")
    public R<RectificationPlan> create(@RequestBody RectificationPlan plan) {
        LoginUser loginUser = getCurrentLoginUser();
        if (plan.getExceptionId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "异常单ID不能为空");
        }
        plan.setPlantCode(loginUser.getPlantCode().name());
        plan.setPlantName(loginUser.getPlantCode().getChineseName());
        plan.setCreatedBy(loginUser.getRealName());
        plan.setUpdatedBy(loginUser.getRealName());
        if (plan.getStatus() == null) {
            plan.setStatus("待执行");
        }
        rectificationPlanService.save(plan);
        if (plan.getPlanNo() == null || plan.getPlanNo().isEmpty()) {
            plan.setPlanNo("RP" + String.format("%06d", plan.getId()));
            rectificationPlanService.updateById(plan);
        }
        auditLogService.record("rectification_plan", plan.getId(), "CREATE", null, plan, "新增整改计划");
        return R.ok(plan, "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新整改计划")
    public R<Void> update(@PathVariable Long id, @RequestBody RectificationPlan plan) {
        RectificationPlan before = rectificationPlanService.getById(id);
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "整改计划不存在");
        }
        plan.setId(id);
        LoginUser loginUser = getCurrentLoginUser();
        plan.setUpdatedBy(loginUser.getRealName());
        rectificationPlanService.updateById(plan);
        auditLogService.record("rectification_plan", id, "UPDATE", before, plan, "更新整改计划");
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除整改计划")
    public R<Void> delete(@PathVariable Long id) {
        RectificationPlan before = rectificationPlanService.getById(id);
        rectificationPlanService.removeById(id);
        auditLogService.record("rectification_plan", id, "DELETE", before, null, "删除整改计划");
        return R.ok(null, "删除成功");
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
