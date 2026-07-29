package com.kangli.qms.api.exception;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.service.exception.dto.EscalationCheckDTO;
import com.kangli.qms.service.exception.dto.EscalationReviewDTO;
import com.kangli.qms.service.exception.dto.EscalationPlanDTO;
import com.kangli.qms.service.exception.dto.EscalationExecutionDTO;
import com.kangli.qms.service.exception.dto.EscalationVerificationDTO;
import com.kangli.qms.service.exception.dto.EscalationCloseDTO;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.service.exception.EscalationService;
import com.kangli.qms.domain.exception.vo.EscalationCheckResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

/**
 * M2-5/8 供应商升级 Controller。
 * <p>路径：/api/v1/escalations</p>
 * <p>含：CRUD + 批量升级检查</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/escalations")
@Api(tags = "M2-供应商升级")
public class EscalationController {

    private final EscalationService escalationService;

    public EscalationController(EscalationService escalationService) {
        this.escalationService = escalationService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询升级记录")
    public R<PageResult<Escalation>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierId) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<Escalation> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Escalation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Escalation::getPlantCode, loginUser.getPlantCode().name());
        if (StringUtils.hasText(status)) {
            wrapper.eq(Escalation::getStatus, status);
        }
        if (StringUtils.hasText(supplierId)) {
            wrapper.eq(Escalation::getSupplierId, supplierId);
        }
        wrapper.orderByDesc(Escalation::getCreatedAt);
        return R.ok(PageResult.of(escalationService.page(pageObj, wrapper)));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "升级记录详情")
    public R<Escalation> detail(@PathVariable Long id) {
        Escalation escalation = escalationService.getById(id);
        if (escalation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "升级记录不存在");
        }
        return R.ok(escalation);
    }

    @PostMapping
    @ApiOperation(value = "发起升级")
    public R<Escalation> create(@RequestBody Escalation escalation) {
        LoginUser loginUser = getCurrentLoginUser();
        escalation.setPlantCode(loginUser.getPlantCode().name());
        escalation.setPlantName(loginUser.getPlantCode().getChineseName());
        escalation.setCreatedBy(loginUser.getRealName());
        escalation.setUpdatedBy(loginUser.getRealName());
        if (escalation.getStatus() == null) {
            escalation.setStatus("PENDING_REVIEW");
        }
        escalation.setProcessStage("PENDING_REVIEW");
        escalationService.save(escalation);
        return R.ok(escalation, "升级已发起");
    }

    @PostMapping("/check")
    @ApiOperation(value = "批量升级检查", notes = "检查90天内同类不良≥N次的供应商，返回应升级列表")
    public R<EscalationCheckResultVO> check(@RequestBody(required = false) EscalationCheckDTO dto) {
        return R.ok(escalationService.checkEscalation(dto));
    }

    @PostMapping("/{id}/review")
    @ApiOperation(value = "审核自动触发的供应商升级任务")
    public R<Escalation> review(@PathVariable Long id, @Valid @RequestBody EscalationReviewDTO dto) {
        return R.ok(escalationService.review(id, dto), "升级审核完成");
    }

    @PostMapping("/{id}/plan")
    public R<Escalation> savePlan(@PathVariable Long id, @Valid @RequestBody EscalationPlanDTO dto) { return R.ok(escalationService.savePlan(id, dto)); }

    @PostMapping("/{id}/execute")
    public R<Escalation> submitExecution(@PathVariable Long id, @Valid @RequestBody EscalationExecutionDTO dto) { return R.ok(escalationService.submitExecution(id, dto)); }

    @PostMapping("/{id}/verify")
    public R<Escalation> verify(@PathVariable Long id, @Valid @RequestBody EscalationVerificationDTO dto) { return R.ok(escalationService.verify(id, dto)); }

    @PostMapping("/{id}/close")
    public R<Escalation> close(@PathVariable Long id, @Valid @RequestBody EscalationCloseDTO dto) { return R.ok(escalationService.close(id, dto)); }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
