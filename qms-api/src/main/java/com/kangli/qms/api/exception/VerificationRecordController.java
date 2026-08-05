package com.kangli.qms.api.exception;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.exception.entity.VerificationRecord;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.exception.VerificationRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * M2-4 验证记录 Controller。
 * <p>路径：/api/v1/verification-records</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/verification-records")
@Api(tags = "M2-验证记录")
public class VerificationRecordController {

    private final VerificationRecordService verificationRecordService;
    private final AuditLogService auditLogService;

    public VerificationRecordController(VerificationRecordService verificationRecordService,
                                        AuditLogService auditLogService) {
        this.verificationRecordService = verificationRecordService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @ApiOperation(value = "按异常单分页查询验证记录")
    public R<PageResult<VerificationRecord>> list(
            @RequestParam Long exceptionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<VerificationRecord> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<VerificationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerificationRecord::getExceptionId, exceptionId)
                .eq(VerificationRecord::getPlantCode, loginUser.getPlantCode().name())
                .orderByDesc(VerificationRecord::getVerifyDate);
        return R.ok(PageResult.of(verificationRecordService.page(pageObj, wrapper)));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "验证记录详情")
    public R<VerificationRecord> detail(@PathVariable Long id) {
        VerificationRecord record = verificationRecordService.getById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "验证记录不存在");
        }
        return R.ok(record);
    }

    @PostMapping
    @ApiOperation(value = "新增验证记录")
    public R<VerificationRecord> create(@RequestBody VerificationRecord record) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setPlantCode(loginUser.getPlantCode().name());
        record.setPlantName(loginUser.getPlantCode().getChineseName());
        record.setCreatedBy(loginUser.getRealName());
        record.setUpdatedBy(loginUser.getRealName());
        if (record.getResult() != null && !"通过".equals(record.getResult()) && !"不通过".equals(record.getResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证结果必须是「通过」或「不通过」");
        }
        // 防御：新增时不允许客户端指定主键，交由 PG 标识列生成（GENERATED ALWAYS AS IDENTITY）
        record.setId(null);
        verificationRecordService.save(record);
        auditLogService.record("verification_record", record.getId(), "CREATE", null, record, "新增验证记录");
        return R.ok(record, "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新验证记录")
    public R<Void> update(@PathVariable Long id, @RequestBody VerificationRecord record) {
        VerificationRecord before = verificationRecordService.getById(id);
        record.setId(id);
        LoginUser loginUser = getCurrentLoginUser();
        record.setUpdatedBy(loginUser.getRealName());
        verificationRecordService.updateById(record);
        auditLogService.record("verification_record", id, "UPDATE", before, record, "更新验证记录");
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除验证记录")
    public R<Void> delete(@PathVariable Long id) {
        VerificationRecord before = verificationRecordService.getById(id);
        verificationRecordService.removeById(id);
        auditLogService.record("verification_record", id, "DELETE", before, null, "删除验证记录");
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
