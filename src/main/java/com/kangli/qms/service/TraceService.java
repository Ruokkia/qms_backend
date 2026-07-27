package com.kangli.qms.service;

import com.kangli.qms.vo.TraceDashboardVO;
import com.kangli.qms.vo.TraceNodeDetailVO;
import com.kangli.qms.vo.TraceTreeResultVO;

/**
 * M0 全链路追溯 Service。
 */
public interface TraceService {

    /**
     * 正向追溯（向下：成品 → 来料）。
     *
     * @param nodeCode 起始节点编码
     * @param maxLevel 最大层级（默认 8，上限 8）
     * @return 树形追溯结果
     */
    TraceTreeResultVO forwardTrace(String nodeCode, Integer maxLevel);

    /**
     * 反向追溯（向上：来料 → 成品）。
     *
     * @param nodeCode 起始节点编码
     * @param maxLevel 最大层级
     * @return 树形追溯结果（upward 填充向上链）
     */
    TraceTreeResultVO backwardTrace(String nodeCode, Integer maxLevel);

    /**
     * 双向全链路追溯。
     *
     * @param nodeCode 起始节点编码
     * @param maxLevel 最大层级
     * @return 树形追溯结果（children 向下 + upward 向上）
     */
    TraceTreeResultVO fullTrace(String nodeCode, Integer maxLevel);

    /**
     * 查询节点详情（含批次信息、父子节点、IQC 检验明细）。
     *
     * @param id 节点ID
     * @return 节点详情
     */
    TraceNodeDetailVO getNodeDetail(Long id);

    /**
     * 追溯看板统计（KPI）。
     *
     * @return 看板统计数据
     */
    TraceDashboardVO getDashboard();
}
