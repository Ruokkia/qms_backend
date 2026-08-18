package com.kangli.qms.api.supplier;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.service.supplier.SupplierService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * M2-1 供应商基础 Controller。
 * <p>路径：/api/v1/suppliers</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/suppliers")
@Api(tags = "M2-供应商管理")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询供应商")
    public R<PageResult<Supplier>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<Supplier> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Supplier::getPlantCode, loginUser.getPlantCode().name());
        if (StringUtils.hasText(keyword)) {
            // 统一转大写再模糊匹配，规避 PostgreSQL LIKE 大小写敏感问题
            // （供应商编码多为 SUP-SZ-01 这类大写格式）
            final String kw = keyword.trim().toUpperCase();
            wrapper.and(w -> w.like(Supplier::getSupplierName, kw)
                    .or().like(Supplier::getSupplierCode, kw));
        }
        wrapper.orderByDesc(Supplier::getCreatedAt);
        return R.ok(PageResult.of(supplierService.page(pageObj, wrapper)));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "供应商详情")
    public R<Supplier> detail(@PathVariable Long id) {
        Supplier supplier = supplierService.getById(id);
        if (supplier == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        }
        return R.ok(supplier);
    }

    @PostMapping
    @ApiOperation(value = "新增供应商")
    public R<Supplier> create(@RequestBody Supplier supplier) {
        LoginUser loginUser = getCurrentLoginUser();
        supplier.setPlantCode(loginUser.getPlantCode().name());
        supplier.setPlantName(loginUser.getPlantCode().getChineseName());
        supplier.setCreatedBy(loginUser.getRealName());
        supplier.setUpdatedBy(loginUser.getRealName());
        if (supplier.getStatus() == null) {
            supplier.setStatus("启用");
        }
        supplierService.save(supplier);
        return R.ok(supplier, "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新供应商")
    public R<Void> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        supplier.setId(id);
        LoginUser loginUser = getCurrentLoginUser();
        supplier.setUpdatedBy(loginUser.getRealName());
        supplierService.updateById(supplier);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除供应商")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.removeById(id);
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
