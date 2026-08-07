package com.kangli.qms.api.admin;

import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.R;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.domain.exception.vo.ExceptionApprovalConfigVO;
import com.kangli.qms.service.exception.dto.ExceptionApprovalConfigDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 异常整改阶段级审批配置 Controller（系统管理模块）。
 * <p>路径：/api/v1/admin/approval-config</p>
 * <p>8D（D0-D8）与 CAPA（C1-C4）共用，按阶段配置是否需审批及审批角色（R04/R06）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/approval-config")
@Api(tags = "系统管理-审批配置")
@Validated
public class ApprovalConfigController {

    private final ExceptionApprovalConfigService approvalConfigService;

    public ApprovalConfigController(ExceptionApprovalConfigService approvalConfigService) {
        this.approvalConfigService = approvalConfigService;
    }

    @GetMapping("/{processFlow}")
    @ApiOperation(value = "查询某流程维度的阶段审批配置", notes = "processFlow=8D 或 CAPA")
    public R<List<ExceptionApprovalConfigVO>> listByFlow(
            @ApiParam(value = "流程维度：8D / CAPA", required = true) @PathVariable String processFlow) {
        String plantCode = LoginUserHolder.get() != null ? LoginUserHolder.get().getPlantCode().name() : "SZ";
        return R.ok(approvalConfigService.listByFlow(processFlow, plantCode));
    }

    @PostMapping
    @ApiOperation(value = "保存/更新一条阶段审批配置")
    public R<Void> save(@Valid @RequestBody ExceptionApprovalConfigDTO dto) {
        approvalConfigService.saveOrUpdateConfig(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "删除一条阶段审批配置")
    public R<Void> delete(@ApiParam(value = "配置主键", required = true) @PathVariable Long id) {
        approvalConfigService.deleteConfig(id);
        return R.ok();
    }
}
