package com.kangli.qms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.entity.ItemNode;
import com.kangli.qms.vo.TraceBatchInfoVO;
import com.kangli.qms.vo.TraceDashboardVO;
import com.kangli.qms.vo.TraceNodeVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 追溯节点 Mapper — 含 PostgreSQL CTE 递归查询。
 */
public interface ItemNodeMapper extends BaseMapper<ItemNode> {

    /**
     * 正向追溯（向下：成品 → 来料）。
     * <p>从起始节点向下递归查找所有子节点，child.parent_id = parent.id。</p>
     *
     * @param nodeCode  起始节点编码
     * @param plantCode 分公司编码（数据隔离）
     * @param maxLevel  最大层级（上限 8）
     * @return 扁平节点列表（含 level/path），按层级排序
     */
    List<TraceNodeVO> selectForwardTrace(@Param("nodeCode") String nodeCode,
                                        @Param("plantCode") String plantCode,
                                        @Param("maxLevel") int maxLevel);

    /**
     * 反向追溯（向上：来料 → 成品）。
     * <p>从起始节点向上递归查找所有父节点，parent.id = child.parent_id。</p>
     *
     * @param nodeCode  起始节点编码
     * @param plantCode 分公司编码
     * @param maxLevel  最大层级
     * @return 扁平节点列表（含 level/path），按层级排序
     */
    List<TraceNodeVO> selectBackwardTrace(@Param("nodeCode") String nodeCode,
                                          @Param("plantCode") String plantCode,
                                          @Param("maxLevel") int maxLevel);

    /**
     * 批量查询批次信息（来料批次关联 material_inspection 表）。
     *
     * @param batchIds 批次ID列表（item_node.batch_id）
     * @return 批次信息列表
     */
    List<TraceBatchInfoVO> selectBatchInfoList(@Param("batchIds") List<Long> batchIds);

    /**
     * 查询追溯看板统计（KPI）。
     *
     * @param plantCode 分公司编码
     * @return 看板统计数据
     */
    TraceDashboardVO selectDashboardStats(@Param("plantCode") String plantCode);
}
