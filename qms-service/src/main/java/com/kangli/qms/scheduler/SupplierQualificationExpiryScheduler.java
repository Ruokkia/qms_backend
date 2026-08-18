package com.kangli.qms.scheduler;

import com.kangli.qms.service.supplier.impl.SupplierQualificationServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 供应商资质到期预警定时任务 — 每天 08:00 扫描 90 天内到期/已过期资质，
 * 并向对应分公司质量经理/质量工程师推送站内通知（含 WebSocket 实时推送）。
 * <p>可通过 {@code supplier.qualification.expiry-check.enabled=false} 关闭。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "supplier.qualification.expiry-check.enabled", havingValue = "true", matchIfMissing = true)
public class SupplierQualificationExpiryScheduler {

    private final SupplierQualificationServiceImpl qualificationService;

    public SupplierQualificationExpiryScheduler(SupplierQualificationServiceImpl qualificationService) {
        this.qualificationService = qualificationService;
    }

    /** 每天 08:00 执行 */
    @Scheduled(cron = "0 0 8 * * ?")
    public void checkExpiring() {
        log.info("开始扫描供应商资质到期预警...");
        try {
            qualificationService.pushExpiryWarnings();
            log.info("供应商资质到期预警扫描完成");
        } catch (Exception e) {
            log.error("供应商资质到期预警扫描失败", e);
        }
    }
}
