package com.kangli.qms.api.incoming;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.service.incoming.CriticalMaterialBindingService;
import com.kangli.qms.service.incoming.dto.CriticalMaterialBindingResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    private final FinishedGoodsInspectionMapper fgMapper;
    private final MaterialInspectionMapper matMapper;

    public CriticalMaterialBindingController(CriticalMaterialBindingService bindingService,
                                             FinishedGoodsInspectionMapper fgMapper,
                                             MaterialInspectionMapper matMapper) {
        this.bindingService = bindingService;
        this.fgMapper = fgMapper;
        this.matMapper = matMapper;
    }

    @GetMapping
    @ApiOperation(value = "分页查询关键物料绑定")
    public R<PageResult<CriticalMaterialBindingResponse>> list(
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
        return R.ok(bindingService.page(pageObj, wrapper));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "关键物料绑定详情")
    public R<CriticalMaterialBindingResponse> detail(@PathVariable Long id) {
        return R.ok(bindingService.detail(id));
    }

    @PostMapping
    @ApiOperation(value = "新增关键物料绑定")
    public R<CriticalMaterialBindingResponse> create(@RequestBody CriticalMaterialBinding record) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        return R.ok(bindingService.create(record, loginUser), "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新关键物料绑定")
    public R<CriticalMaterialBindingResponse> update(@PathVariable Long id,
                                                     @RequestBody CriticalMaterialBinding record) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setUpdatedBy(loginUser.getRealName());
        return R.ok(bindingService.update(id, record, loginUser), "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除关键物料绑定")
    public R<Void> delete(@PathVariable Long id) {
        bindingService.delete(id);
        return R.ok(null, "删除成功");
    }

    /**
     * 绑定弹窗专用：按 category + 条码/名称双框模糊查询子项候选列表。
     * category=半成品 → 查 finished_goods_inspection；category=物料 → 查 material_inspection。
     * barcodeKeyword 和 nameKeyword 均可选，都填则 AND 模糊查询。
     */
    @GetMapping("/search-children")
    @ApiOperation(value = "模糊查询绑定子项候选")
    public R<PageResult<Map<String, Object>>> searchChildren(
            @RequestParam String category,
            @RequestParam(required = false) String barcodeKeyword,
            @RequestParam(required = false) String nameKeyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        LoginUser loginUser = getCurrentLoginUser();
        String currentPlant = loginUser.getPlantCode().name();

        boolean hasBarcode = StringUtils.hasText(barcodeKeyword);
        boolean hasName = StringUtils.hasText(nameKeyword);
        if (!hasBarcode && !hasName) {
            return R.ok(new PageResult<>(Collections.emptyList(), 0L, (long) page, (long) size));
        }

        if ("半成品".equals(category)) {
            return searchFinishedGoods(hasBarcode ? barcodeKeyword.trim() : null,
                    hasName ? nameKeyword.trim() : null, currentPlant, page, size);
        } else if ("物料".equals(category)) {
            return searchMaterials(hasBarcode ? barcodeKeyword.trim() : null,
                    hasName ? nameKeyword.trim() : null, currentPlant, page, size);
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的分类：" + category);
    }

    private R<PageResult<Map<String, Object>>> searchFinishedGoods(
            String barcodeKw, String nameKw, String plant, int page, int size) {
        LambdaQueryWrapper<FinishedGoodsInspection> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(FinishedGoodsInspection::getPlantCode, plant);
        if (barcodeKw != null) countWrapper.like(FinishedGoodsInspection::getProdBatchOrSn, barcodeKw);
        if (nameKw != null) countWrapper.like(FinishedGoodsInspection::getProductName, nameKw);
        long total = fgMapper.selectCount(countWrapper);

        Page<FinishedGoodsInspection> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<FinishedGoodsInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinishedGoodsInspection::getPlantCode, plant);
        if (barcodeKw != null) wrapper.like(FinishedGoodsInspection::getProdBatchOrSn, barcodeKw);
        if (nameKw != null) wrapper.like(FinishedGoodsInspection::getProductName, nameKw);
        wrapper.orderByDesc(FinishedGoodsInspection::getCreatedAt);
        List<FinishedGoodsInspection> records = fgMapper.selectPage(pageObj, wrapper).getRecords();

        List<Map<String, Object>> items = new ArrayList<>();
        for (FinishedGoodsInspection fg : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", fg.getId());
            item.put("barcode", fg.getProdBatchOrSn());
            item.put("name", fg.getProductName());
            item.put("specModel", fg.getModelSpec());
            item.put("category", fg.getCategory());
            item.put("materialCode", fg.getMaterialCode());
            item.put("inspectionResult", fg.getInspectionResult());
            items.add(item);
        }
        return R.ok(new PageResult<>(items, total, (long) page, (long) size));
    }

    private R<PageResult<Map<String, Object>>> searchMaterials(
            String barcodeKw, String nameKw, String plant, int page, int size) {
        LambdaQueryWrapper<MaterialInspection> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(MaterialInspection::getPlantCode, plant)
                    .isNotNull(MaterialInspection::getMaterialBarcode);
        if (barcodeKw != null) countWrapper.like(MaterialInspection::getMaterialBarcode, barcodeKw);
        if (nameKw != null) countWrapper.like(MaterialInspection::getMaterialName, nameKw);
        long total = matMapper.selectCount(countWrapper);

        Page<MaterialInspection> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<MaterialInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialInspection::getPlantCode, plant)
               .isNotNull(MaterialInspection::getMaterialBarcode);
        if (barcodeKw != null) wrapper.like(MaterialInspection::getMaterialBarcode, barcodeKw);
        if (nameKw != null) wrapper.like(MaterialInspection::getMaterialName, nameKw);
        wrapper.orderByDesc(MaterialInspection::getCreatedAt);
        List<MaterialInspection> records = matMapper.selectPage(pageObj, wrapper).getRecords();

        List<Map<String, Object>> items = new ArrayList<>();
        for (MaterialInspection mat : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", mat.getId());
            item.put("barcode", mat.getMaterialBarcode());
            item.put("name", mat.getMaterialName());
            item.put("specModel", mat.getSpecModel());
            item.put("materialCode", mat.getMaterialCode());
            item.put("materialBatchNo", mat.getMaterialBatchNo());
            item.put("inspectionResult", mat.getInspectionResult());
            items.add(item);
        }
        return R.ok(new PageResult<>(items, total, (long) page, (long) size));
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
