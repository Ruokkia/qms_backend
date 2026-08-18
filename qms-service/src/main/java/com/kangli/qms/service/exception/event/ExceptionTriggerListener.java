package com.kangli.qms.service.exception.event;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.ExceptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检验不合格事件监听器（M2 异常自动触发，事件驱动实时派单）。
 *
 * <p>消费 {@link UnqualifiedInspectionEvent}，异步调用
 * {@code ExceptionService#createFromXxx} 派生异常整改单。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>实时</b>：检验业务发布事件后立即返回，派单在独立线程异步执行，无 5 分钟轮询延迟。</li>
 *   <li><b>解耦</b>：派单失败不影响检验事务提交（事件已发布即与检验事务脱钩）。</li>
 *   <li><b>不刷屏</b>：以 (sourceType, sourceId) 维度去重，建单失败记入 {@code failedKeys}，
 *       后续同 key 事件仅限频日志、不再重复尝试/打完整堆栈；建单成功后自动移出，便于重试Recovery。</li>
 * </ul>
 */
@Slf4j
@Component
public class ExceptionTriggerListener {

    private final ExceptionService exceptionService;

    /** 建单失败过的 (sourceType:sourceId) 集合，避免反复刷日志与无效重试 */
    private final Set<String> failedKeys = ConcurrentHashMap.newKeySet();

    public ExceptionTriggerListener(ExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @Async("exceptionTriggerExecutor")
    @EventListener
    public void onUnqualified(UnqualifiedInspectionEvent event) {
        String key = event.getSourceType() + ":" + event.getSourceId();
        LoginUser systemUser = buildSystemUser(event.getPlantCode());
        LoginUserHolder.set(systemUser);
        try {
            ExceptionOrder order = doCreate(event);
            if (order != null) {
                // 建单成功：若此前失败过则移出，恢复该来源后续可重试能力
                if (failedKeys.remove(key)) {
                    log.info("[异常自动触发] 来源 {} 建单成功，已清除失败标记", key);
                }
            }
        } catch (Exception e) {
            handleFailure(key, event, e);
        } finally {
            LoginUserHolder.clear();
        }
    }

    private ExceptionOrder doCreate(UnqualifiedInspectionEvent event) {
        switch (event.getSourceType()) {
            case "来料不良":
                return exceptionService.createFromMaterialInspection(event.getMaterialInspection(),
                        LoginUserHolder.get());
            case "首件不良":
                return exceptionService.createFromFai(event.getFaiInspectionRecord(),
                        LoginUserHolder.get());
            case "成品不良":
                return exceptionService.createFromFinishedGoods(event.getFinishedGoodsInspection(),
                        LoginUserHolder.get());
            default:
                log.warn("[异常自动触发] 未知来源类型，忽略事件：{}", event.getSourceType());
                return null;
        }
    }

    private void handleFailure(String key, UnqualifiedInspectionEvent event, Exception e) {
        if (failedKeys.add(key)) {
            // 首次失败：记录完整堆栈便于排查
            log.error("[异常自动触发] 为 {} 记录 {} 建单失败（后续将静默跳过，直至建单成功）：{}",
                    event.getSourceType(), event.getSourceId(), e.getMessage(), e);
        } else {
            // 已失败过：仅限频提示，避免每事件刷 ERROR + 堆栈
            log.debug("[异常自动触发] 为 {} 记录 {} 建单仍失败（已记入失败集合，静默跳过）：{}",
                    event.getSourceType(), event.getSourceId(), e.getMessage());
        }
    }

    /** 构造异步建单使用的「系统」账号（plantCode 以来源记录自身为准，仅作兜底） */
    private LoginUser buildSystemUser(String plantCode) {
        PlantCode pc = PlantCode.SZ;
        if (plantCode != null) {
            try {
                pc = PlantCode.valueOf(plantCode);
            } catch (IllegalArgumentException ignored) {
                // 非法厂编码回退 SZ
            }
        }
        return LoginUser.builder()
                .userId(-1L)
                .account("system")
                .realName("系统")
                .roleCode("R00")
                .plantCode(pc)
                .canSwitchArea(false)
                .authVersion(0)
                .build();
    }
}
