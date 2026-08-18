package com.kangli.qms.api.tooling;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.tooling.entity.*;
import com.kangli.qms.domain.tooling.mapper.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tooling")
@Api(tags = "工装管理")
public class ToolingController {
    private final ToolingAssetMapper assetMapper;
    private final ToolingMaintenanceRecordMapper recordMapper;
    private final ToolingAssetBindingMapper bindingMapper;
    private final ToolingVersionRecordMapper versionMapper;
    public ToolingController(ToolingAssetMapper assetMapper, ToolingMaintenanceRecordMapper recordMapper,
                             ToolingAssetBindingMapper bindingMapper, ToolingVersionRecordMapper versionMapper) {
        this.assetMapper = assetMapper; this.recordMapper = recordMapper;
        this.bindingMapper = bindingMapper; this.versionMapper = versionMapper;
    }

    @GetMapping
    @ApiOperation("分页查询工装台账")
    public R<PageResult<ToolingAsset>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status) {
        LoginUser user = currentUser();
        LambdaQueryWrapper<ToolingAsset> q = new LambdaQueryWrapper<ToolingAsset>()
                .eq(ToolingAsset::getPlantCode, user.getPlantCode().name())
                .eq(status != null && !status.trim().isEmpty(), ToolingAsset::getStatus, status)
                .and(keyword != null && !keyword.trim().isEmpty(), w -> w.like(ToolingAsset::getToolingCode, keyword)
                        .or().like(ToolingAsset::getToolingName, keyword).or().like(ToolingAsset::getApplicableProduct, keyword))
                .orderByDesc(ToolingAsset::getUpdatedAt);
        return R.ok(PageResult.of(assetMapper.selectPage(new Page<>(page, size), q)));
    }

    @PostMapping
    @ApiOperation("新建工装台账")
    public R<ToolingAsset> create(@RequestBody ToolingAsset asset) {
        LoginUser user = currentUser();
        asset.setPlantCode(user.getPlantCode().name()); asset.setPlantName(user.getPlantCode().getChineseName());
        asset.setCreatedBy(user.getRealName()); asset.setUpdatedBy(user.getRealName());
        if (asset.getStatus() == null) asset.setStatus("在用");
        if (asset.getVersionNo() == null) asset.setVersionNo("V1.0");
        if (asset.getUsedCount() == null) asset.setUsedCount(0);
        asset.setQrCode("QMS-TL-" + user.getPlantCode().name() + "-" + asset.getToolingCode());
        assetMapper.insert(asset);
        return R.ok(asset, "工装已创建");
    }

    @PutMapping("/{id}")
    @ApiOperation("更新工装台账")
    public R<ToolingAsset> update(@PathVariable Long id, @RequestBody ToolingAsset asset) {
        ToolingAsset existing = assetMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "工装不存在");
        asset.setId(id); asset.setPlantCode(existing.getPlantCode()); asset.setPlantName(existing.getPlantName());
        asset.setUpdatedBy(currentUser().getRealName()); assetMapper.updateById(asset);
        return R.ok(assetMapper.selectById(id), "工装已更新");
    }

    @GetMapping("/{id}/records")
    @ApiOperation("查询工装保养和维修记录")
    public R<java.util.List<ToolingMaintenanceRecord>> records(@PathVariable Long id) {
        return R.ok(recordMapper.selectList(new LambdaQueryWrapper<ToolingMaintenanceRecord>()
                .eq(ToolingMaintenanceRecord::getToolingId, id).orderByDesc(ToolingMaintenanceRecord::getDueDate)));
    }

    @GetMapping("/{id}/bindings")
    @ApiOperation("查询工装关联的产品BOM和工艺路线")
    public R<List<ToolingAssetBinding>> bindings(@PathVariable Long id) {
        return R.ok(bindingMapper.selectList(new LambdaQueryWrapper<ToolingAssetBinding>()
                .eq(ToolingAssetBinding::getToolingId, id).orderByDesc(ToolingAssetBinding::getCreatedAt)));
    }

    @PutMapping("/{id}/bindings")
    @ApiOperation("维护工装关联的产品BOM和工艺路线")
    public R<List<ToolingAssetBinding>> replaceBindings(@PathVariable Long id, @RequestBody List<ToolingAssetBinding> bindings) {
        ToolingAsset asset = requireAsset(id);
        LoginUser user = currentUser();
        bindingMapper.selectList(new LambdaQueryWrapper<ToolingAssetBinding>().eq(ToolingAssetBinding::getToolingId, id))
                .forEach(item -> bindingMapper.deleteById(item.getId()));
        for (ToolingAssetBinding binding : bindings == null ? Collections.<ToolingAssetBinding>emptyList() : bindings) {
            if (!"BOM".equals(binding.getBindingType()) && !"工艺路线".equals(binding.getBindingType())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "关联类型仅支持 BOM 或工艺路线");
            }
            binding.setId(null); binding.setToolingId(id); binding.setPlantCode(asset.getPlantCode()); binding.setPlantName(asset.getPlantName()); binding.setCreatedBy(user.getRealName());
            bindingMapper.insert(binding);
        }
        return bindings(id);
    }

    @GetMapping("/{id}/versions")
    @ApiOperation("查询工装版本和设计变更履历")
    public R<List<ToolingVersionRecord>> versions(@PathVariable Long id) {
        return R.ok(versionMapper.selectList(new LambdaQueryWrapper<ToolingVersionRecord>()
                .eq(ToolingVersionRecord::getToolingId, id).orderByDesc(ToolingVersionRecord::getCreatedAt)));
    }

    @PostMapping("/{id}/versions")
    @ApiOperation("登记工装设计变更或升级")
    public R<ToolingVersionRecord> createVersion(@PathVariable Long id, @RequestBody ToolingVersionRecord record) {
        ToolingAsset asset = requireAsset(id);
        if (record.getVersionNo() == null || record.getVersionNo().trim().isEmpty()
                || record.getChangeType() == null || record.getChangeType().trim().isEmpty()
                || record.getChangeDescription() == null || record.getChangeDescription().trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写新版本号、变更类型和变更说明");
        }
        LoginUser user = currentUser();
        record.setId(null); record.setToolingId(id); record.setChangedBy(user.getRealName()); record.setPlantCode(asset.getPlantCode()); record.setPlantName(asset.getPlantName());
        versionMapper.insert(record);
        asset.setVersionNo(record.getVersionNo()); asset.setUpdatedBy(user.getRealName()); assetMapper.updateById(asset);
        return R.ok(record, "版本变更已登记");
    }

    @GetMapping("/reminders")
    @ApiOperation("查询到期或逾期保养提醒")
    public R<List<ToolingMaintenanceRecord>> reminders() {
        LoginUser user = currentUser();
        return R.ok(recordMapper.selectList(new LambdaQueryWrapper<ToolingMaintenanceRecord>()
                .eq(ToolingMaintenanceRecord::getPlantCode, user.getPlantCode().name())
                .eq(ToolingMaintenanceRecord::getRecordType, "保养")
                .le(ToolingMaintenanceRecord::getDueDate, LocalDate.now())
                .in(ToolingMaintenanceRecord::getStatus, Arrays.asList("待执行", "逾期"))
                .orderByAsc(ToolingMaintenanceRecord::getDueDate)));
    }

    @GetMapping("/failure-stats")
    @ApiOperation("统计工装高频故障类型和根本原因")
    public R<List<Map<String, Object>>> failureStats() {
        LoginUser user = currentUser();
        List<ToolingMaintenanceRecord> failures = recordMapper.selectList(new LambdaQueryWrapper<ToolingMaintenanceRecord>()
                .eq(ToolingMaintenanceRecord::getPlantCode, user.getPlantCode().name()).eq(ToolingMaintenanceRecord::getRecordType, "维修"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ToolingMaintenanceRecord item : failures) {
            String key = (item.getFaultDescription() == null || item.getFaultDescription().trim().isEmpty() ? "未填写故障类型" : item.getFaultDescription())
                    + "｜" + (item.getRootCause() == null || item.getRootCause().trim().isEmpty() ? "未填写根本原因" : item.getRootCause());
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(item -> {
            String[] pair = item.getKey().split("｜", 2); Map<String, Object> value = new LinkedHashMap<>();
            value.put("faultType", pair[0]); value.put("rootCause", pair.length > 1 ? pair[1] : ""); value.put("count", item.getValue()); result.add(value);
        });
        return R.ok(result);
    }

    @PostMapping("/{id}/records")
    @ApiOperation("新增工装保养或维修记录")
    public R<ToolingMaintenanceRecord> createRecord(@PathVariable Long id, @RequestBody ToolingMaintenanceRecord record) {
        ToolingAsset asset = assetMapper.selectById(id);
        if (asset == null) throw new BusinessException(ResultCode.NOT_FOUND, "工装不存在");
        LoginUser user = currentUser();
        record.setToolingId(id); record.setPlantCode(user.getPlantCode().name()); record.setPlantName(user.getPlantCode().getChineseName());
        record.setCreatedBy(user.getRealName()); record.setUpdatedBy(user.getRealName());
        if (record.getStatus() == null) record.setStatus("待执行");
        if ("维修".equals(record.getRecordType())) {
            record.setVerificationResult("待验证");
            if ("合格".equals(record.getAcceptanceResult())) {
                asset.setStatus("待验证");
                record.setStatus("待验证");
            } else {
                asset.setStatus("维修");
            }
            asset.setUpdatedBy(user.getRealName());
            assetMapper.updateById(asset);
        }
        recordMapper.insert(record);
        return R.ok(record, "记录已保存");
    }

    @PostMapping("/records/{recordId}/verify")
    @ApiOperation("维修后精度验证，合格后恢复使用")
    public R<ToolingMaintenanceRecord> verifyRepair(@PathVariable Long recordId, @RequestBody ToolingMaintenanceRecord verification) {
        ToolingMaintenanceRecord record = recordMapper.selectById(recordId);
        if (record == null || !"维修".equals(record.getRecordType())) throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在");
        if (!"待验证".equals(record.getStatus())) throw new BusinessException(ResultCode.BAD_REQUEST, "该维修记录当前无需验证");
        ToolingAsset asset = requireAsset(record.getToolingId());
        record.setVerificationResult(verification.getVerificationResult()); record.setRemark(verification.getRemark()); record.setUpdatedBy(currentUser().getRealName());
        if ("合格".equals(verification.getVerificationResult())) { record.setStatus("已关闭"); asset.setStatus("在用"); }
        else { record.setStatus("验证不合格"); asset.setStatus("维修"); }
        asset.setUpdatedBy(currentUser().getRealName()); assetMapper.updateById(asset); recordMapper.updateById(record);
        return R.ok(recordMapper.selectById(recordId), "精度验证已完成");
    }

    @PostMapping("/{id}/usage")
    @ApiOperation("登记工装使用次数并校验寿命")
    public R<ToolingAsset> addUsage(@PathVariable Long id, @RequestParam(defaultValue = "1") int count) {
        ToolingAsset asset = assetMapper.selectById(id);
        if (asset == null) throw new BusinessException(ResultCode.NOT_FOUND, "工装不存在");
        if (!"在用".equals(asset.getStatus())) throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不可登记使用");
        asset.setUsedCount((asset.getUsedCount() == null ? 0 : asset.getUsedCount()) + count);
        if (asset.getLifeLimit() != null && asset.getUsedCount() >= asset.getLifeLimit()) asset.setStatus("停用");
        asset.setUpdatedBy(currentUser().getRealName()); assetMapper.updateById(asset);
        return R.ok(assetMapper.selectById(id), "使用次数已登记");
    }
    private ToolingAsset requireAsset(Long id) { ToolingAsset asset = assetMapper.selectById(id); if (asset == null) throw new BusinessException(ResultCode.NOT_FOUND, "工装不存在"); return asset; }
    private LoginUser currentUser() { LoginUser u = LoginUserHolder.get(); if (u == null || u.getPlantCode() == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息"); return u; }
}
