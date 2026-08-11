package com.kangli.qms.service.spc;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.service.spc.dto.SpcBatchNoDTO;
import com.kangli.qms.service.spc.dto.SpcSubgroupResponse;
import com.kangli.qms.service.spc.dto.SpcSubgroupSaveDTO;
import com.kangli.qms.service.spc.dto.SpcPendingSampleAppendDTO;

import java.util.List;
import java.util.Map;

/**
 * M4 SPC 子组（数据采集）Service。
 */
public interface SpcSubgroupService {

    /** 手动录入子组（样本数须等于参数 subgroupSize） */
    SpcSubgroupResponse save(SpcSubgroupSaveDTO dto, LoginUser loginUser);

    /**
     * 电子签名完成后自动将首件中匹配的参数写入 SPC 子组。
     * 匹配键为工序名称、参数编码、单位和子组大小；调用方必须与签名处于同一事务。
     */
    void autoImportFromSignedFai(Long faiRecordId, LoginUser loginUser);

    /** 子组列表（按参数 + 分公司） */
    List<SpcSubgroupResponse> list(Long paramId, String plantCode);

    /** 子组列表（按首件记录 + 分公司，用于 FAI 跳转 SPC 自动定位待补子组） */
    List<SpcSubgroupResponse> listByFai(Long faiRecordId, String plantCode);

    /** 子组详情（含样本） */
    SpcSubgroupResponse detail(Long id);

    /** 向首件创建的待补样本子组追加手工样本；达到 n 后自动完成。 */
    SpcSubgroupResponse appendPendingSamples(Long subgroupId, SpcPendingSampleAppendDTO dto, LoginUser loginUser);

    /** 删除子组及明细（逻辑删除） */
    void remove(Long id);

    /**
     * 录入时按 itemType + itemCode 返回两表中该代码下的真实批次号列表（去重）。
     * PRODUCT 查 finished_goods_inspection.prod_batch_or_sn；MATERIAL 查 material_inspection.material_batch_no。
     *
     * @param itemType PRODUCT / MATERIAL
     * @param itemCode 产品/物料代码
     * @param loginUser 当前登录用户（取厂区做隔离）
     */
    List<SpcBatchNoDTO> listBatchNos(String itemType, String itemCode, LoginUser loginUser);

    /**
     * 按 itemType + itemCode + batchNo 反查两表，返回该批次在来源表（成品/物料）中的明细行。
     * 用于 SPC 溯源抽屉展示真实来源数据。
     *
     * @param itemType PRODUCT / MATERIAL
     * @param itemCode 产品/物料代码
     * @param batchNo  批次号
     * @param barcode   条码（追溯标识），可空；与 batchNo 二选一即可命中来源（OR 关系）
     * @param loginUser 当前登录用户（取厂区做隔离）
     * @return 来源行明细（字段随表不同），未找到返回 null
     */
    Map<String, Object> sourceDetail(String itemType, String itemCode, String batchNo, String barcode, String plantCode);

}
