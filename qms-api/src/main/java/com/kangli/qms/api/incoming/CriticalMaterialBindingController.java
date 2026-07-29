package com.kangli.qms.api.incoming;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.service.incoming.CriticalMaterialBindingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * M1-3 关键物料绑定 Controller。
 * <p>路径：/api/v1/material-bindings</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/material-bindings")
@Api(tags = "M1-关键物料绑定")
public class CriticalMaterialBindingController {

    private final CriticalMaterialBindingService bindingService;

    public CriticalMaterialBindingController(CriticalMaterialBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询关键物料绑定")
    public R<PageResult<CriticalMaterialBinding>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String productBarcode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String processCode) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<CriticalMaterialBinding> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<CriticalMaterialBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CriticalMaterialBinding::getPlantCode, loginUser.getPlantCode().name());
        if (StringUtils.hasText(workOrderNo)) {
            wrapper.eq(CriticalMaterialBinding::getWorkOrderNo, workOrderNo);
        }
        if (StringUtils.hasText(productBarcode)) {
            wrapper.eq(CriticalMaterialBinding::getProductBarcode, productBarcode);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.eq(CriticalMaterialBinding::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(processCode)) {
            wrapper.eq(CriticalMaterialBinding::getProcessCode, processCode);
        }
        wrapper.orderByDesc(CriticalMaterialBinding::getScanTime);
        return R.ok(PageResult.of(bindingService.page(pageObj, wrapper)));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "关键物料绑定详情")
    public R<CriticalMaterialBinding> detail(@PathVariable Long id) {
        CriticalMaterialBinding record = bindingService.getById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return R.ok(record);
    }

    @PostMapping
    @ApiOperation(value = "新增关键物料绑定")
    public R<CriticalMaterialBinding> create(@RequestBody CriticalMaterialBinding record) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        bindingService.save(record);
        return R.ok(record, "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新关键物料绑定")
    public R<Void> update(@PathVariable Long id, @RequestBody CriticalMaterialBinding record) {
        record.setId(id);
        LoginUser loginUser = getCurrentLoginUser();
        record.setUpdatedBy(loginUser.getRealName());
        bindingService.updateById(record);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除关键物料绑定")
    public R<Void> delete(@PathVariable Long id) {
        bindingService.removeById(id);
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
