package com.kangli.qms.api.fai;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.fai.dto.CreateChangeTriggerRequest;
import com.kangli.qms.service.fai.dto.FaiChangeTriggerQuery;
import com.kangli.qms.service.fai.dto.FaiChangeTriggerResponse;
import com.kangli.qms.service.fai.dto.FaiInspectionQuery;
import com.kangli.qms.service.fai.dto.FaiInspectionRecordResponse;
import com.kangli.qms.service.fai.dto.FaiInspectionRequest;
import com.kangli.qms.service.fai.dto.FaiItemValueRequest;
import com.kangli.qms.service.fai.dto.FaiReportResponse;
import com.kangli.qms.service.fai.dto.FaiSignatureRequest;
import com.kangli.qms.service.fai.dto.FaiSpcBaselineVO;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSaveRequest;
import com.kangli.qms.service.fai.FaiChangeTriggerService;
import com.kangli.qms.service.fai.FaiInspectionService;
import com.kangli.qms.service.fai.FaiStandardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M3 首件检验管理 Controller。
 * <p>路径前缀：/api/v1/fai</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fai")
@Api(tags = "M3-首件检验管理")
public class FaiController {

    private final FaiChangeTriggerService changeTriggerService;
    private final FaiInspectionService inspectionService;
    private final FaiStandardService standardService;

    public FaiController(FaiChangeTriggerService changeTriggerService,
                         FaiInspectionService inspectionService,
                         FaiStandardService standardService) {
        this.changeTriggerService = changeTriggerService;
        this.inspectionService = inspectionService;
        this.standardService = standardService;
    }

    // ===== 变更触发 =====

    @PostMapping("/change-triggers")
    @ApiOperation(value = "创建变更触发")
    public R<FaiChangeTriggerResponse> createChangeTrigger(@RequestBody CreateChangeTriggerRequest request) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(changeTriggerService.create(request, loginUser), "创建成功");
    }

    @GetMapping("/change-triggers")
    @ApiOperation(value = "分页查询变更触发")
    public R<PageResult<FaiChangeTriggerResponse>> listChangeTriggers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemType) {
        LoginUser loginUser = getCurrentLoginUser();
        FaiChangeTriggerQuery query = new FaiChangeTriggerQuery();
        query.setPage(page);
        query.setSize(size);
        query.setTriggerType(triggerType);
        query.setMaterialCode(materialCode);
        query.setBatchNo(batchNo);
        query.setStatus(status);
        query.setItemType(itemType);
        return R.ok(changeTriggerService.page(query, loginUser.getPlantCode().name()));
    }

    @GetMapping("/change-triggers/{id}")
    @ApiOperation(value = "变更触发详情")
    public R<FaiChangeTriggerResponse> changeTriggerDetail(@PathVariable Long id) {
        return R.ok(changeTriggerService.detail(id));
    }

    // ===== 首件检验 =====

    @PostMapping("/inspections")
    @ApiOperation(value = "从变更触发创建首件检验单")
    public R<FaiInspectionRecordResponse> createInspection(@RequestBody FaiInspectionRequest request) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(inspectionService.create(request.getChangeTriggerId(), loginUser), "创建成功");
    }

    @GetMapping("/inspections")
    @ApiOperation(value = "分页查询首件检验记录")
    public R<PageResult<FaiInspectionRecordResponse>> listInspections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String faiNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String inspectionResult,
            @RequestParam(required = false) String signatureStatus,
            @RequestParam(required = false) Boolean archiveOnly,
            @RequestParam(required = false) String itemType) {
        LoginUser loginUser = getCurrentLoginUser();
        FaiInspectionQuery query = new FaiInspectionQuery();
        query.setPage(page);
        query.setSize(size);
        query.setFaiNo(faiNo);
        query.setBatchNo(batchNo);
        query.setMaterialName(materialName);
        query.setInspectionResult(inspectionResult);
        query.setSignatureStatus(signatureStatus);
        query.setArchiveOnly(archiveOnly);
        query.setItemType(itemType);
        return R.ok(inspectionService.page(query, loginUser.getPlantCode().name()));
    }

    @GetMapping("/inspections/{id}")
    @ApiOperation(value = "首件检验详情（含参数明细）")
    public R<FaiInspectionRecordResponse> inspectionDetail(@PathVariable Long id) {
        return R.ok(inspectionService.detail(id));
    }

    @PutMapping("/inspections/{id}/items")
    @ApiOperation(value = "批量填写参数实际值并自动判定")
    public R<FaiInspectionRecordResponse> submitItems(@PathVariable Long id,
                                                      @RequestBody FaiItemValueRequest request) {
        LoginUser loginUser = getCurrentLoginUser();
        request.setFaiRecordId(id);
        return R.ok(inspectionService.submitItems(request, loginUser));
    }

    @PostMapping("/inspections/{id}/judge")
    @ApiOperation(value = "重新自动判定")
    public R<FaiInspectionRecordResponse> judge(@PathVariable Long id) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(inspectionService.judge(id, loginUser));
    }

    @PostMapping("/inspections/{id}/refresh-standard")
    @ApiOperation(value = "刷新检验标准值：从当前激活标准同步最新值，补全新标准项，重新判定")
    public R<FaiInspectionRecordResponse> refreshStandard(@PathVariable Long id) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(inspectionService.refreshStandard(id, loginUser), "标准已刷新");
    }

    @PostMapping("/inspections/{id}/signature")
    @ApiOperation(value = "电子签名")
    public R<FaiInspectionRecordResponse> signature(@PathVariable Long id,
                                                    @RequestBody FaiSignatureRequest request) {
        LoginUser loginUser = getCurrentLoginUser();
        request.setFaiRecordId(id);
        return R.ok(inspectionService.signature(request, loginUser));
    }

    @GetMapping("/inspections/{id}/report")
    @ApiOperation(value = "首件检验报告（JSON）")
    public R<FaiReportResponse> report(@PathVariable Long id) {
        return R.ok(inspectionService.report(id));
    }

    // ===== 标准模板 =====

    @GetMapping("/standards")
    @ApiOperation(value = "标准模板列表")
    public R<List<FaiStandardResponse>> listStandards(
            @ApiParam(value = "分类：PRODUCT/AMATERIAL，可选") @RequestParam(required = false) String itemType) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(standardService.listByItemType(loginUser.getPlantCode().name(), itemType));
    }

    @GetMapping("/standards/{materialCode}/{processName}")
    @ApiOperation(value = "查询最新激活标准（按物料代码+工序）")
    public R<FaiStandardResponse> latestStandard(
            @ApiParam(value = "物料代码") @PathVariable String materialCode,
            @ApiParam(value = "工序") @PathVariable String processName) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(standardService.latestActive(materialCode, processName, loginUser.getPlantCode().name()));
    }

    @GetMapping("/standards/item")
    @ApiOperation(value = "查询最新激活标准（按分类+代码+工序）")
    public R<FaiStandardResponse> latestStandardByItem(
            @ApiParam(value = "分类：PRODUCT/AMATERIAL") @RequestParam String itemType,
            @ApiParam(value = "产品/物料代码") @RequestParam String itemCode,
            @ApiParam(value = "工序") @RequestParam String processName) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(standardService.latestActive(itemCode, itemType, processName, loginUser.getPlantCode().name()));
    }

    @PostMapping("/standards")
    @ApiOperation(value = "新增检验标准模板（手动设置物料/工序标准）")
    public R<Long> createStandard(@RequestBody FaiStandardSaveRequest request) {
        LoginUser loginUser = getCurrentLoginUser();
        return R.ok(standardService.createStandard(request, loginUser), "创建成功");
    }

    @PutMapping("/standards/{id}")
    @ApiOperation(value = "更新检验标准模板")
    public R<Void> updateStandard(@ApiParam(value = "标准 id") @PathVariable Long id,
                                   @RequestBody FaiStandardSaveRequest request) {
        LoginUser loginUser = getCurrentLoginUser();
        standardService.updateStandard(id, request, loginUser);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/standards/{id}")
    @ApiOperation(value = "删除检验标准模板（逻辑删除）")
    public R<Void> deleteStandard(@ApiParam(value = "标准 id") @PathVariable Long id) {
        LoginUser loginUser = getCurrentLoginUser();
        standardService.deleteStandard(id, loginUser);
        return R.ok(null, "删除成功");
    }

    // ===== SPC 联动 =====

    @GetMapping("/spc-baseline/{faiRecordId}")
    @ApiOperation(value = "SPC 调取基准数据")
    public R<List<FaiSpcBaselineVO>> spcBaseline(@PathVariable Long faiRecordId) {
        return R.ok(inspectionService.spcBaseline(faiRecordId));
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
