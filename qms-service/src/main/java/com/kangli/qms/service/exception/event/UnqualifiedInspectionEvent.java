package com.kangli.qms.service.exception.event;

import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 检验不合格事件（M2 异常自动触发）。
 *
 * <p>由来料 / 首件 / 成品检验在判定为不合格时发布，由
 * {@code ExceptionTriggerListener} 异步消费并派生成异常整改单。</p>
 *
 * <p>采用事件驱动替代原定时轮询扫描，兼具「实时派单」与「检验业务 / 异常生成解耦」：
 * 发布方只负责发事件，派单失败不影响检验事务提交；消费方异步建单，
 * 失败按 (sourceType, sourceId) 去重、限频日志，避免反复刷屏。</p>
 */
@Getter
public class UnqualifiedInspectionEvent extends ApplicationEvent {

    /** 来源类型：来料不良 / 首件不良 / 成品不良 */
    private final String sourceType;

    /** 来源记录 ID（对应 material_inspection / fai_inspection_record / finished_goods_inspection 主键） */
    private final Long sourceId;

    /** 来源记录自身分公司编码（建单兜底归属用） */
    private final String plantCode;

    private final MaterialInspection materialInspection;
    private final FaiInspectionRecord faiInspectionRecord;
    private final FinishedGoodsInspection finishedGoodsInspection;

    private UnqualifiedInspectionEvent(Object source, String sourceType, Long sourceId,
                                       String plantCode, MaterialInspection materialInspection,
                                       FaiInspectionRecord faiInspectionRecord,
                                       FinishedGoodsInspection finishedGoodsInspection) {
        super(source);
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.plantCode = plantCode;
        this.materialInspection = materialInspection;
        this.faiInspectionRecord = faiInspectionRecord;
        this.finishedGoodsInspection = finishedGoodsInspection;
    }

    public static UnqualifiedInspectionEvent fromMaterial(MaterialInspection inspection) {
        return new UnqualifiedInspectionEvent(inspection, "来料不良", inspection.getId(),
                inspection.getPlantCode(), inspection, null, null);
    }

    public static UnqualifiedInspectionEvent fromFai(FaiInspectionRecord record) {
        return new UnqualifiedInspectionEvent(record, "首件不良", record.getId(),
                record.getPlantCode(), null, record, null);
    }

    public static UnqualifiedInspectionEvent fromFinishedGoods(FinishedGoodsInspection inspection) {
        return new UnqualifiedInspectionEvent(inspection, "成品不良", inspection.getId(),
                inspection.getPlantCode(), null, null, inspection);
    }
}
