package com.kangli.qms.scheduler;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.ExceptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M2 异常整改自动触发调度器。
 *
 * <p>将原「检验接口提交时同步派生异常单」改为「定时轮询扫描数据库检检验不合格记录」，
 * 达到「从数据库读异常来触发」的目的，使异常生成与检验业务解耦。</p>
 *
 * <p>轮询范围：扫描来料 / 首件 / 成品三张检验表里「不合格且尚未关联异常单」的记录，
 * 逐条调用 {@link ExceptionService#createFromMaterialInspection} / {@code createFromFai} /
 * {@code createFromFinishedGoods} 自动建单。各建单方法内部已按 sourceId+sourceType 去重，
 * 即使重复扫描也不会建出重复单。</p>
 *
 * <p>由于轮询运行在后台线程（无 HTTP 请求上下文），使用「系统」账号作为建单人。
 * 异常单的分公司归属以来料/成品/首件记录自身的 plant_code 为准。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExceptionAutoTriggerScheduler {

    /** 默认扫描窗口：近 N 天内的不合格记录（避免每次全表扫描） */
    @Value("${qms.exception.auto-trigger.window-days:30}")
    private int windowDays;

    private final MaterialInspectionMapper materialInspectionMapper;
    private final FaiInspectionRecordMapper faiInspectionRecordMapper;
    private final FinishedGoodsInspectionMapper finishedGoodsInspectionMapper;
    private final ExceptionService exceptionService;

    /**
     * 每 5 分钟扫描一次（可被配置覆盖）。
     * 固定延迟避免与长事务重叠，单条失败不影响其余记录。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("[异常自动触发] 调度器已注册并就绪，固定间隔={}ms，扫描窗口={}天",
                fixedDelayMs(), windowDays);
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${qms.exception.auto-trigger.fixed-delay-ms:300000}")
    public void scan() {
        LoginUser systemUser = buildSystemUser();
        LocalDateTime endAt = LocalDateTime.now();
        LocalDateTime startAt = endAt.minusDays(windowDays);

        log.info("[异常自动触发] 开始扫描（窗口 {} 天）", windowDays);

        scanMaterial(systemUser, startAt, endAt);
        scanFai(systemUser, startAt, endAt);
        scanFinishedGoods(systemUser, startAt, endAt);

        log.info("[异常自动触发] 扫描结束");
    }

    private void scanMaterial(LoginUser systemUser, LocalDateTime startAt, LocalDateTime endAt) {
        // 来料按分公司扫描（selectUnlinkedUnqualified 已绕过 tenant 拦截器，需逐厂调用）
        for (PlantCode plant : PlantCode.values()) {
            List<MaterialInspection> list = materialInspectionMapper.selectUnlinkedUnqualified(
                    plant.name(), "不合格", "来料不良", startAt, endAt);
            for (MaterialInspection inspection : list) {
                safeCreate(() -> exceptionService.createFromMaterialInspection(inspection, systemUser),
                        "来料", inspection.getId());
            }
        }
    }

    private void scanFai(LoginUser systemUser, LocalDateTime startAt, LocalDateTime endAt) {
        List<FaiInspectionRecord> list = faiInspectionRecordMapper.selectUnlinkedUnqualified(
                "首件不良", startAt, endAt);
        for (FaiInspectionRecord record : list) {
            safeCreate(() -> exceptionService.createFromFai(record, systemUser),
                    "首件", record.getId());
        }
    }

    private void scanFinishedGoods(LoginUser systemUser, LocalDateTime startAt, LocalDateTime endAt) {
        List<FinishedGoodsInspection> list = finishedGoodsInspectionMapper.selectUnlinkedUnqualified(
                "成品不良", startAt, endAt);
        for (FinishedGoodsInspection inspection : list) {
            safeCreate(() -> exceptionService.createFromFinishedGoods(inspection, systemUser),
                    "成品", inspection.getId());
        }
    }

    /**
     * 单条建单包装：失败仅记录日志，不中断整轮扫描。
     * 建单方法本身已内部去重，重复触发返回已存在单，不抛异常。
     */
    @Transactional(rollbackFor = Exception.class)
    protected ExceptionOrder safeCreate(CreateCall call, String sourceLabel, Long sourceId) {
        // 调度线程无 HTTP 请求上下文，需手动设置系统用户到 ThreadLocal，
        // 供建单逻辑（含审计日志写入）读取 plant_code 等当前用户信息。
        LoginUserHolder.set(buildSystemUser());
        try {
            return call.run();
        } catch (Exception e) {
            log.error("[异常自动触发] 为{}记录 {} 建单失败：{}", sourceLabel, sourceId, e.getMessage(), e);
            return null;
        } finally {
            LoginUserHolder.clear();
        }
    }

    @FunctionalInterface
    private interface CreateCall {
        ExceptionOrder run();
    }

    /** 读取配置中的扫描间隔（毫秒），读取失败回退 300000 */
    private long fixedDelayMs() {
        try {
            return Long.parseLong(System.getProperty("qms.exception.auto-trigger.fixed-delay-ms", "300000"));
        } catch (Exception e) {
            return 300000L;
        }
    }

    /**
     * 构造后台调度使用的「系统」账号。
     * 因建单方法以来料/检验记录自身的 plant_code 为准，此处 plantCode 仅作兜底。
     */
    private LoginUser buildSystemUser() {
        return LoginUser.builder()
                .userId(-1L)
                .account("system")
                .realName("系统")
                .roleCode("R00")
                .plantCode(PlantCode.SZ)
                .canSwitchArea(false)
                .authVersion(0)
                .build();
    }
}
