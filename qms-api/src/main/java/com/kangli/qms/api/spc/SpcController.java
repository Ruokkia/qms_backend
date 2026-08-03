package com.kangli.qms.api.spc;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.R;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.spc.dto.SpcCapabilityResultDTO;
import com.kangli.qms.service.spc.dto.SpcChartDataDTO;
import com.kangli.qms.service.spc.dto.SpcControlLimitResponse;
import com.kangli.qms.service.spc.dto.SpcParameterRequest;
import com.kangli.qms.service.spc.dto.SpcParameterResponse;
import com.kangli.qms.service.spc.dto.SpcProcessRequest;
import com.kangli.qms.service.spc.dto.SpcProcessResponse;
import com.kangli.qms.service.spc.dto.SpcSubgroupResponse;
import com.kangli.qms.service.spc.dto.SpcSubgroupSaveDTO;
import com.kangli.qms.service.spc.dto.SpcPendingSampleAppendDTO;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.service.spc.SpcCapabilityService;
import com.kangli.qms.service.spc.SpcChartService;
import com.kangli.qms.common.SpcItemDictDTO;
import com.kangli.qms.service.spc.SpcItemDictService;
import com.kangli.qms.service.spc.SpcParameterService;
import com.kangli.qms.service.spc.SpcProcessService;
import com.kangli.qms.service.spc.SpcSubgroupService;
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
 * M4 SPC 过程能力分析 Controller。
 * <p>路径前缀：/api/v1/spc</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/spc")
@Api(tags = "M4-SPC过程能力分析")
public class SpcController {

    private final SpcProcessService processService;
    private final SpcParameterService parameterService;
    private final SpcSubgroupService subgroupService;
    private final SpcChartService chartService;
    private final SpcCapabilityService capabilityService;
    private final SpcItemDictService itemDictService;

    public SpcController(SpcProcessService processService,
                         SpcParameterService parameterService,
                         SpcSubgroupService subgroupService,
                         SpcChartService chartService,
                         SpcCapabilityService capabilityService,
                         SpcItemDictService itemDictService) {
        this.processService = processService;
        this.parameterService = parameterService;
        this.subgroupService = subgroupService;
        this.chartService = chartService;
        this.capabilityService = capabilityService;
        this.itemDictService = itemDictService;
    }

    // ===== 工序管理 =====

    @PostMapping("/processes")
    @ApiOperation(value = "创建工序")
    public R<SpcProcessResponse> createProcess(@RequestBody SpcProcessRequest request) {
        return R.ok(processService.create(request, currentUser()), "创建成功");
    }

    @GetMapping("/processes")
    @ApiOperation(value = "工序列表")
    public R<List<SpcProcessResponse>> listProcesses() {
        return R.ok(processService.list(currentUser().getPlantCode().name()));
    }

    @PutMapping("/processes/{id}")
    @ApiOperation(value = "更新工序")
    public R<SpcProcessResponse> updateProcess(@PathVariable Long id, @RequestBody SpcProcessRequest request) {
        return R.ok(processService.update(id, request, currentUser()));
    }

    @DeleteMapping("/processes/{id}")
    @ApiOperation(value = "删除工序（逻辑删除）")
    public R<Void> removeProcess(@PathVariable Long id) {
        processService.remove(id);
        return R.ok(null, "删除成功");
    }

    // ===== 参数管理 =====

    @PostMapping("/parameters")
    @ApiOperation(value = "创建参数")
    public R<SpcParameterResponse> createParameter(@RequestBody SpcParameterRequest request) {
        return R.ok(parameterService.create(request, currentUser()), "创建成功");
    }

    @GetMapping("/parameters")
    @ApiOperation(value = "参数列表（按工序筛选）")
    public R<List<SpcParameterResponse>> listParameters(
            @RequestParam(required = false) Long processId) {
        return R.ok(parameterService.list(processId, currentUser().getPlantCode().name()));
    }

    @GetMapping("/parameters/{id}")
    @ApiOperation(value = "参数详情")
    public R<SpcParameterResponse> parameterDetail(@PathVariable Long id) {
        return R.ok(parameterService.detail(id));
    }

    @PutMapping("/parameters/{id}")
    @ApiOperation(value = "更新参数")
    public R<SpcParameterResponse> updateParameter(@PathVariable Long id, @RequestBody SpcParameterRequest request) {
        return R.ok(parameterService.update(id, request, currentUser()));
    }

    @DeleteMapping("/parameters/{id}")
    @ApiOperation(value = "删除参数（逻辑删除）")
    public R<Void> removeParameter(@PathVariable Long id) {
        parameterService.remove(id);
        return R.ok(null, "删除成功");
    }

    // ===== 子组 / 数据采集 =====

    @PostMapping("/subgroups")
    @ApiOperation(value = "手动录入子组")
    public R<SpcSubgroupResponse> saveSubgroup(@RequestBody SpcSubgroupSaveDTO dto) {
        return R.ok(subgroupService.save(dto, currentUser()), "保存成功");
    }


    @GetMapping("/subgroups")
    @ApiOperation(value = "子组列表（按参数筛选）")
    public R<List<SpcSubgroupResponse>> listSubgroups(
            @RequestParam(required = false) Long paramId) {
        if (paramId == null) {
            return R.ok(java.util.Collections.emptyList());
        }
        return R.ok(subgroupService.list(paramId, currentUser().getPlantCode().name()));
    }

    @GetMapping("/subgroups/by-fai")
    @ApiOperation(value = "按首件记录查询子组（用于 FAI 跳转 SPC 自动定位待补子组）")
    public R<List<SpcSubgroupResponse>> listSubgroupsByFai(
            @ApiParam(value = "首件记录 id") @RequestParam Long faiRecordId) {
        return R.ok(subgroupService.listByFai(faiRecordId, currentUser().getPlantCode().name()));
    }

    @GetMapping("/subgroups/{id}")
    @ApiOperation(value = "子组详情（含样本）")
    public R<SpcSubgroupResponse> subgroupDetail(@PathVariable Long id) {
        return R.ok(subgroupService.detail(id));
    }

    @PostMapping("/subgroups/{id}/samples")
    @ApiOperation(value = "补录首件待补样本")
    public R<SpcSubgroupResponse> appendPendingSamples(@PathVariable Long id,
                                                        @RequestBody SpcPendingSampleAppendDTO dto) {
        return R.ok(subgroupService.appendPendingSamples(id, dto, currentUser()), "样本已补录");
    }

    @DeleteMapping("/subgroups/{id}")
    @ApiOperation(value = "删除子组（逻辑删除）")
    public R<Void> removeSubgroup(@PathVariable Long id) {
        subgroupService.remove(id);
        return R.ok(null, "删除成功");
    }

    // ===== 控制图 =====

    @GetMapping("/charts/{paramId}/xbar-r")
    @ApiOperation(value = "Xbar-R 控制图数据")
    public R<SpcChartDataDTO> xbarRChart(@ApiParam(value = "参数 id") @PathVariable Long paramId,
                                         @ApiParam(value = "分类 PRODUCT/MATERIAL，可空") @RequestParam(required = false) String itemType,
                                         @ApiParam(value = "产品/物料代码，可空") @RequestParam(required = false) String itemCode) {
        return R.ok(chartService.getChartData(paramId, "Xbar-R", currentUser().getPlantCode().name(), itemType, itemCode));
    }

    @GetMapping("/charts/{paramId}/xbar-s")
    @ApiOperation(value = "Xbar-s 控制图数据")
    public R<SpcChartDataDTO> xbarSChart(@ApiParam(value = "参数 id") @PathVariable Long paramId,
                                         @ApiParam(value = "分类 PRODUCT/MATERIAL，可空") @RequestParam(required = false) String itemType,
                                         @ApiParam(value = "产品/物料代码，可空") @RequestParam(required = false) String itemCode) {
        return R.ok(chartService.getChartData(paramId, "Xbar-s", currentUser().getPlantCode().name(), itemType, itemCode));
    }

    @PostMapping("/control-limits/{paramId}/recalc")
    @ApiOperation(value = "重新计算控制限")
    public R<SpcControlLimitResponse> recalcControlLimits(@ApiParam(value = "参数 id") @PathVariable Long paramId) {
        SpcControlLimit cl = chartService.recalcControlLimits(paramId, currentUser().getPlantCode().name());
        // 子组数不足（<2）时无法计算控制限，返回 null
        return R.ok(cl == null ? null : toControlLimitResponse(cl));
    }

    // ===== 统一代码字典 =====

    @GetMapping("/items/search")
    @ApiOperation(value = "SPC 统一代码字典（已签首件 ∪ 已激活标准，按 itemType+itemCode 去重）")
    public R<List<SpcItemDictDTO>> searchItems(
            @ApiParam(value = "模糊关键字，匹配 itemCode/itemName，可空") @RequestParam(required = false) String keyword) {
        return R.ok(itemDictService.search(currentUser().getPlantCode().name(), keyword));
    }

    private SpcControlLimitResponse toControlLimitResponse(SpcControlLimit cl) {
        SpcControlLimitResponse resp = new SpcControlLimitResponse();
        org.springframework.beans.BeanUtils.copyProperties(cl, resp);
        return resp;
    }

    // ===== 过程能力 =====

    @GetMapping("/capability/{paramId}")
    @ApiOperation(value = "最新过程能力指数")
    public R<SpcCapabilityResultDTO> getCapability(@ApiParam(value = "参数 id") @PathVariable Long paramId) {
        return R.ok(capabilityService.getLatest(paramId, currentUser().getPlantCode().name()));
    }

    @PostMapping("/capability/{paramId}/recalc")
    @ApiOperation(value = "重新计算能力指数")
    public R<SpcCapabilityResultDTO> recalcCapability(@ApiParam(value = "参数 id") @PathVariable Long paramId) {
        return R.ok(capabilityService.recalcCapability(paramId, currentUser().getPlantCode().name()));
    }

    private LoginUser currentUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
