package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.entity.ItemNode;
import com.kangli.qms.mapper.ItemNodeMapper;
import com.kangli.qms.service.TraceService;
import com.kangli.qms.vo.TraceBatchInfoVO;
import com.kangli.qms.vo.TraceDashboardVO;
import com.kangli.qms.vo.TraceNodeDetailVO;
import com.kangli.qms.vo.TraceNodeVO;
import com.kangli.qms.vo.TraceStatsVO;
import com.kangli.qms.vo.TraceTreeResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M0 全链路追溯 Service 实现。
 * <p>核心：PostgreSQL CTE 递归查询 + 树形结构组装 + 批次信息填充。</p>
 */
@Slf4j
@Service
public class TraceServiceImpl implements TraceService {

    /** 追溯层级上限（固化基线） */
    private static final int TRACE_MAX_LEVEL = 8;

    private final ItemNodeMapper itemNodeMapper;

    public TraceServiceImpl(ItemNodeMapper itemNodeMapper) {
        this.itemNodeMapper = itemNodeMapper;
    }

    // ===== M0: 正向追溯（向下） =====

    @Override
    public TraceTreeResultVO forwardTrace(String nodeCode, Integer maxLevel) {
        validateNodeCode(nodeCode);
        int effectiveMaxLevel = sanitizeMaxLevel(maxLevel);
        String plantCode = getCurrentPlantCode();

        List<TraceNodeVO> flatNodes = itemNodeMapper.selectForwardTrace(nodeCode, plantCode, effectiveMaxLevel);
        if (flatNodes.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到节点：" + nodeCode);
        }

        fillBatchInfo(flatNodes);

        TraceNodeVO rootNode = flatNodes.get(0);
        List<TraceNodeVO> children = buildChildrenTree(flatNodes, rootNode.getId());
        rootNode.setChildren(children);

        TraceTreeResultVO result = new TraceTreeResultVO();
        result.setRootNode(rootNode);
        result.setChildren(children);
        result.setStats(buildStats(flatNodes));
        return result;
    }

    // ===== M0: 反向追溯（向上） =====

    @Override
    public TraceTreeResultVO backwardTrace(String nodeCode, Integer maxLevel) {
        validateNodeCode(nodeCode);
        int effectiveMaxLevel = sanitizeMaxLevel(maxLevel);
        String plantCode = getCurrentPlantCode();

        List<TraceNodeVO> flatNodes = itemNodeMapper.selectBackwardTrace(nodeCode, plantCode, effectiveMaxLevel);
        if (flatNodes.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到节点：" + nodeCode);
        }

        fillBatchInfo(flatNodes);

        // rootNode = 起始节点（第一个，level=1）
        TraceNodeVO rootNode = flatNodes.get(0);
        rootNode.setChildren(new ArrayList<>());

        // upward = 从起点到顶层的完整路径（按 level 正序）
        List<TraceNodeVO> upward = flatNodes.stream()
                .map(this::shallowCopy)
                .collect(Collectors.toList());

        TraceTreeResultVO result = new TraceTreeResultVO();
        result.setRootNode(rootNode);
        result.setChildren(new ArrayList<>());
        result.setUpward(upward);
        result.setStats(buildStats(flatNodes));
        return result;
    }

    // ===== M0: 双向追溯 =====

    @Override
    public TraceTreeResultVO fullTrace(String nodeCode, Integer maxLevel) {
        validateNodeCode(nodeCode);
        int effectiveMaxLevel = sanitizeMaxLevel(maxLevel);
        String plantCode = getCurrentPlantCode();

        List<TraceNodeVO> forwardNodes = itemNodeMapper.selectForwardTrace(nodeCode, plantCode, effectiveMaxLevel);
        List<TraceNodeVO> backwardNodes = itemNodeMapper.selectBackwardTrace(nodeCode, plantCode, effectiveMaxLevel);

        if (forwardNodes.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到节点：" + nodeCode);
        }

        // 合并去重
        Map<Long, TraceNodeVO> allNodeMap = new LinkedHashMap<>();
        forwardNodes.forEach(n -> allNodeMap.put(n.getId(), n));
        backwardNodes.forEach(n -> allNodeMap.putIfAbsent(n.getId(), n));
        List<TraceNodeVO> allNodes = new ArrayList<>(allNodeMap.values());

        fillBatchInfo(allNodes);

        TraceNodeVO rootNode = forwardNodes.get(0);
        List<TraceNodeVO> children = buildChildrenTree(forwardNodes, rootNode.getId());
        rootNode.setChildren(children);

        // upward = 反向父节点链（不含起始节点）
        List<TraceNodeVO> upward = backwardNodes.stream()
                .filter(n -> !n.getId().equals(rootNode.getId()))
                .map(this::shallowCopy)
                .collect(Collectors.toList());

        TraceTreeResultVO result = new TraceTreeResultVO();
        result.setRootNode(rootNode);
        result.setChildren(children);
        result.setUpward(upward);
        result.setStats(buildStats(allNodes));
        return result;
    }

    // ===== M0: 节点详情 =====

    @Override
    public TraceNodeDetailVO getNodeDetail(Long id) {
        ItemNode item = itemNodeMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在：" + id);
        }

        TraceNodeDetailVO detail = new TraceNodeDetailVO();
        detail.setDetail(toTraceNodeVO(item));

        // 父节点
        if (item.getParentId() != null) {
            ItemNode parent = itemNodeMapper.selectById(item.getParentId());
            if (parent != null) {
                detail.setParent(toTraceNodeVO(parent));
            }
        }

        // 直接子节点
        LambdaQueryWrapper<ItemNode> childQuery = new LambdaQueryWrapper<>();
        childQuery.eq(ItemNode::getParentId, id);
        List<ItemNode> childEntities = itemNodeMapper.selectList(childQuery);
        List<TraceNodeVO> childVOs = childEntities.stream()
                .map(this::toTraceNodeVO)
                .collect(Collectors.toList());
        detail.setChildren(childVOs);

        // batchInfo
        if (item.getBatchId() != null) {
            List<TraceBatchInfoVO> batchInfos = itemNodeMapper.selectBatchInfoList(
                    Collections.singletonList(item.getBatchId()));
            if (!batchInfos.isEmpty()) {
                detail.setBatchInfo(batchInfos.get(0));
            }
        }

        // IQC 检验明细（暂无检验项子表，返回空列表）
        detail.setInspections(new ArrayList<>());

        return detail;
    }

    // ===== M0: 看板统计 =====

    @Override
    public TraceDashboardVO getDashboard() {
        String plantCode = getCurrentPlantCode();
        TraceDashboardVO dashboard = itemNodeMapper.selectDashboardStats(plantCode);
        if (dashboard == null) {
            dashboard = new TraceDashboardVO();
            dashboard.setTotalBatches(0);
            dashboard.setQualifiedBatches(0);
            dashboard.setAbnormalBatches(0);
            dashboard.setInCheckBatches(0);
            dashboard.setPassRate(BigDecimal.ZERO);
            dashboard.setPpm(0);
            dashboard.setSupplierCount(0);
            dashboard.setSnCount(0);
            dashboard.setNodeCount(0);
        }
        return dashboard;
    }

    // ===== 私有方法 =====

    private void validateNodeCode(String nodeCode) {
        if (nodeCode == null || nodeCode.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节点编码不能为空");
        }
    }

    private int sanitizeMaxLevel(Integer maxLevel) {
        if (maxLevel == null || maxLevel <= 0 || maxLevel > TRACE_MAX_LEVEL) {
            return TRACE_MAX_LEVEL;
        }
        return maxLevel;
    }

    private String getCurrentPlantCode() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser.getPlantCode().name();
    }

    /**
     * 为所有有 batchId 的节点填充 batchInfo（批量查询，避免 N+1）。
     */
    private void fillBatchInfo(List<TraceNodeVO> nodes) {
        List<Long> batchIds = nodes.stream()
                .filter(n -> n.getBatchId() != null)
                .map(TraceNodeVO::getBatchId)
                .distinct()
                .collect(Collectors.toList());

        if (batchIds.isEmpty()) {
            return;
        }

        List<TraceBatchInfoVO> batchInfos = itemNodeMapper.selectBatchInfoList(batchIds);
        Map<Long, TraceBatchInfoVO> infoMap = batchInfos.stream()
                .collect(Collectors.toMap(TraceBatchInfoVO::getBatchId, b -> b, (a, b) -> a));

        for (TraceNodeVO node : nodes) {
            if (node.getBatchId() != null) {
                TraceBatchInfoVO info = infoMap.get(node.getBatchId());
                if (info != null) {
                    node.setBatchInfo(info);
                }
            }
        }
    }

    /**
     * 将扁平节点列表组装为以 rootId 为根的子树。
     * <p>移除根节点后，剩余节点按 parentId 递归挂载。</p>
     */
    private List<TraceNodeVO> buildChildrenTree(List<TraceNodeVO> flatNodes, Long rootId) {
        Map<Long, TraceNodeVO> nodeMap = new LinkedHashMap<>();
        for (TraceNodeVO node : flatNodes) {
            if (!node.getId().equals(rootId)) {
                node.setChildren(new ArrayList<>());
                nodeMap.put(node.getId(), node);
            }
        }

        List<TraceNodeVO> roots = new ArrayList<>();
        for (TraceNodeVO node : nodeMap.values()) {
            Long parentId = node.getParentId();
            if (parentId != null && nodeMap.containsKey(parentId)) {
                nodeMap.get(parentId).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        return roots;
    }

    private TraceStatsVO buildStats(List<TraceNodeVO> nodes) {
        TraceStatsVO stats = new TraceStatsVO();
        stats.setTotalNodes(nodes.size());

        int maxDepth = nodes.stream()
                .filter(n -> n.getLevel() != null)
                .mapToInt(TraceNodeVO::getLevel)
                .max()
                .orElse(1);
        stats.setMaxDepth(maxDepth);
        stats.setLevelCap(TRACE_MAX_LEVEL);

        long batchCount = nodes.stream()
                .filter(n -> n.getBatchId() != null)
                .map(TraceNodeVO::getBatchId)
                .distinct()
                .count();
        stats.setBatchCount((int) batchCount);

        long supplierCount = nodes.stream()
                .filter(n -> n.getBatchInfo() != null && n.getBatchInfo().getSupplierName() != null)
                .map(n -> n.getBatchInfo().getSupplierName())
                .distinct()
                .count();
        stats.setSupplierCount((int) supplierCount);

        return stats;
    }

    private TraceNodeVO toTraceNodeVO(ItemNode item) {
        TraceNodeVO vo = new TraceNodeVO();
        vo.setId(item.getId());
        vo.setNodeType(item.getNodeType());
        vo.setNodeCode(item.getNodeCode());
        vo.setParentId(item.getParentId());
        vo.setBatchId(item.getBatchId());
        vo.setQtyUsed(item.getQtyUsed());
        vo.setWorkOrderId(item.getWorkOrderId());
        vo.setPlantCode(item.getPlantCode());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    /**
     * 浅拷贝节点（children 置空，用于 upward 链）。
     */
    private TraceNodeVO shallowCopy(TraceNodeVO source) {
        TraceNodeVO copy = new TraceNodeVO();
        copy.setId(source.getId());
        copy.setNodeType(source.getNodeType());
        copy.setNodeCode(source.getNodeCode());
        copy.setParentId(source.getParentId());
        copy.setBatchId(source.getBatchId());
        copy.setQtyUsed(source.getQtyUsed());
        copy.setWorkOrderId(source.getWorkOrderId());
        copy.setPlantCode(source.getPlantCode());
        copy.setLevel(source.getLevel());
        copy.setPath(source.getPath());
        copy.setBatchInfo(source.getBatchInfo());
        return copy;
    }
}
