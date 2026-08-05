package com.kangli.qms.api.exception;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.exception.ImprovementActionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * M2-3 改善措施 Controller。
 * <p>路径：/api/v1/improvement-actions</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/improvement-actions")
@Api(tags = "M2-改善措施")
public class ImprovementActionController {

    private final ImprovementActionService improvementActionService;
    private final AuditLogService auditLogService;

    public ImprovementActionController(ImprovementActionService improvementActionService,
                                       AuditLogService auditLogService) {
        this.improvementActionService = improvementActionService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @ApiOperation(value = "按异常单分页查询改善措施")
    public R<PageResult<ImprovementAction>> list(
            @RequestParam Long exceptionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<ImprovementAction> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<ImprovementAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImprovementAction::getExceptionId, exceptionId)
                .eq(ImprovementAction::getPlantCode, loginUser.getPlantCode().name())
                .orderByAsc(ImprovementAction::getActionType);
        return R.ok(PageResult.of(improvementActionService.page(pageObj, wrapper)));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "改善措施详情")
    public R<ImprovementAction> detail(@PathVariable Long id) {
        ImprovementAction action = improvementActionService.getById(id);
        if (action == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "改善措施不存在");
        }
        return R.ok(action);
    }

    @PostMapping
    @ApiOperation(value = "新增改善措施")
    public R<ImprovementAction> create(@RequestBody ImprovementAction action) {
        LoginUser loginUser = getCurrentLoginUser();
        action.setPlantCode(loginUser.getPlantCode().name());
        action.setPlantName(loginUser.getPlantCode().getChineseName());
        action.setCreatedBy(loginUser.getRealName());
        action.setUpdatedBy(loginUser.getRealName());
        if (action.getOwnerId() == null) {
            action.setOwnerId(loginUser.getUserId());
        }
        if (action.getStatus() == null) {
            action.setStatus("PENDING");
        }
        // 防御：新增时不允许客户端指定主键，交由 PG 标识列生成（GENERATED ALWAYS AS IDENTITY）
        action.setId(null);
        improvementActionService.save(action);
        auditLogService.record("improvement_action", action.getId(), "CREATE", null, action, "新增改善措施");
        return R.ok(action, "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新改善措施")
    public R<Void> update(@PathVariable Long id, @RequestBody ImprovementAction action) {
        ImprovementAction before = improvementActionService.getById(id);
        action.setId(id);
        LoginUser loginUser = getCurrentLoginUser();
        action.setUpdatedBy(loginUser.getRealName());
        improvementActionService.updateById(action);
        auditLogService.record("improvement_action", id, "UPDATE", before, action, "更新改善措施");
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除改善措施")
    public R<Void> delete(@PathVariable Long id) {
        ImprovementAction before = improvementActionService.getById(id);
        improvementActionService.removeById(id);
        auditLogService.record("improvement_action", id, "DELETE", before, null, "删除改善措施");
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
