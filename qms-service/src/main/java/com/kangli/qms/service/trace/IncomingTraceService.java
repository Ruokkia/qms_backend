package com.kangli.qms.service.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.CriticalMaterialBindingMapper;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 基于三张业务表的追溯服务。
 * <p>替代旧版 trace_node + trace_relation 中间表方案，
 * 改为直接查询 critical_material_binding + material_inspection + finished_goods_inspection。</p>
 * <p>所有查询/写入均按当前登录用户的 plant_code 隔离。</p>
 */
@Service
public class IncomingTraceService {

    private static final Logger log = LoggerFactory.getLogger(IncomingTraceService.class);

    private final CriticalMaterialBindingMapper bindingMapper;
    private final FinishedGoodsInspectionMapper fgMapper;
    private final MaterialInspectionMapper matMapper;

    public IncomingTraceService(CriticalMaterialBindingMapper bindingMapper,
                                FinishedGoodsInspectionMapper fgMapper,
                                MaterialInspectionMapper matMapper) {
        this.bindingMapper = bindingMapper;
        this.fgMapper = fgMapper;
        this.matMapper = matMapper;
    }

    // ==================== 公共 API ====================

    /**
     * 追溯树查询入口。
     *
     * @param rootBarcode 根条码
     * @param direction   DOWN / UP / FULL / BATCH_IMPACT
     */
    public Map<String, Object> tree(String rootBarcode, String direction) {
        if (!StringUtils.hasText(rootBarcode)) {
            throw new IllegalArgumentException("追溯条码不能为空");
        }
        rootBarcode = rootBarcode.trim();

        if ("BATCH_IMPACT".equals(direction)) {
            return batchImpact(rootBarcode);
        }

        Map<String, Object> root = buildRootNode(rootBarcode);
        if (root == null) {
            throw new IllegalArgumentException("未找到追溯节点：" + rootBarcode);
        }

        TraceGraphContext graph = loadGraphContext(rootBarcode, direction);

        if ("FULL".equals(direction)) {
            Set<String> upVisited = new LinkedHashSet<>();
            Set<String> downVisited = new LinkedHashSet<>();
            root.put("upward", expand(rootBarcode, true, new LinkedHashSet<>(), upVisited, graph));
            root.put("children", expand(rootBarcode, false, new LinkedHashSet<>(), downVisited, graph));
            return result(root, upVisited.size() + downVisited.size(), direction, 0);
        } else {
            Set<String> visited = new LinkedHashSet<>();
            boolean up = "UP".equals(direction);
            root.put("children", expand(rootBarcode, up, new LinkedHashSet<>(), visited, graph));
            return result(root, visited.size(), direction, 0);
        }
    }

    /**
     * 单节点详情。
     *
     * @param id   业务表主键
     * @param type "fg" = finished_goods_inspection, "mi" = material_inspection
     */
    public Map<String, Object> node(long id, String type) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("缺少 type 参数（fg/mi）");
        }
        String currentPlant = plant();

        if ("fg".equalsIgnoreCase(type)) {
            FinishedGoodsInspection fg = fgMapper.selectById(id);
            if (fg == null || !Objects.equals(fg.getPlantCode(), currentPlant)) {
                throw new IllegalArgumentException("节点不存在");
            }
            return buildNodeFromFg(fg);
        } else if ("mi".equalsIgnoreCase(type)) {
            MaterialInspection mat = matMapper.selectById(id);
            if (mat == null || !Objects.equals(mat.getPlantCode(), currentPlant)) {
                throw new IllegalArgumentException("节点不存在");
            }
            return buildNodeFromMat(mat);
        }
        throw new IllegalArgumentException("不支持的 type: " + type);
    }

    /**
     * 所有节点列表（成品/半成品 + 物料合并），输出格式兼容前端。
     */
    public List<Map<String, Object>> nodes() {
        String currentPlant = plant();
        List<Map<String, Object>> result = new ArrayList<>();

        // 成品 / 半成品
        LambdaQueryWrapper<FinishedGoodsInspection> fgWrapper = new LambdaQueryWrapper<>();
        fgWrapper.eq(FinishedGoodsInspection::getPlantCode, currentPlant)
                 .orderByAsc(FinishedGoodsInspection::getId);
        for (FinishedGoodsInspection fg : fgMapper.selectList(fgWrapper)) {
            result.add(buildNodeFromFg(fg));
        }

        // 物料（仅含 material_barcode 非空的记录，才能参与追溯）
        LambdaQueryWrapper<MaterialInspection> matWrapper = new LambdaQueryWrapper<>();
        matWrapper.eq(MaterialInspection::getPlantCode, currentPlant)
                  .isNotNull(MaterialInspection::getMaterialBarcode)
                  .orderByAsc(MaterialInspection::getId);
        for (MaterialInspection mat : matMapper.selectList(matWrapper)) {
            result.add(buildNodeFromMat(mat));
        }

        return result;
    }

    /**
     * 所有关系列表（基于 critical_material_binding），输出格式兼容前端。
     */
    public List<Map<String, Object>> relations() {
        String currentPlant = plant();

        LambdaQueryWrapper<CriticalMaterialBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CriticalMaterialBinding::getPlantCode, currentPlant)
               .orderByAsc(CriticalMaterialBinding::getId);
        List<CriticalMaterialBinding> bindings = bindingMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (CriticalMaterialBinding b : bindings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("parentBarcode", b.getProductBarcode());
            row.put("parentName", b.getProductName());
            row.put("childBarcode", b.getMaterialBarcode());
            row.put("childName", b.getMaterialName());
            row.put("category", b.getCategory());
            row.put("quantity", b.getWorkOrderQty());
            row.put("workOrderNo", b.getWorkOrderNo());
            row.put("processName", b.getProcessName());
            row.put("plantCode", b.getPlantCode());
            result.add(row);
        }
        return result;
    }

    /**
     * 追溯仪表盘统计，改为查业务表。
     */
    public Map<String, Object> summary() {
        String currentPlant = plant();
        Map<String, Object> s = new LinkedHashMap<>();

        // 物料检验统计（保留原有聚合 SQL，通过 JdbcTemplate 或直接查）
        QueryWrapper<MaterialInspection> matAll = new QueryWrapper<>();
        matAll.select("inspection_result", "review_status", "supplier_code", "supplier_name",
                "submitted_qty", "unqualified_qty", "material_batch_no")
                .eq("plant_code", currentPlant);
        List<MaterialInspection> allMaterials = matMapper.selectList(matAll);

        long totalBatches = allMaterials.size();
        long qualifiedBatches = allMaterials.stream()
                .filter(m -> "合格".equals(m.getInspectionResult())).count();
        long abnormalBatches = allMaterials.stream()
                .filter(m -> "不合格".equals(m.getInspectionResult())).count();
        long inCheckBatches = allMaterials.stream()
                .filter(m -> "待审核".equals(m.getReviewStatus()) && m.getInspectionResult() == null).count();

        BigDecimal totalSubmitted = allMaterials.stream()
                .map(m -> m.getSubmittedQty() != null ? m.getSubmittedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalUnqualified = allMaterials.stream()
                .map(m -> m.getUnqualifiedQty() != null ? m.getUnqualifiedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long supplierCount = allMaterials.stream()
                .map(m -> StringUtils.hasText(m.getSupplierCode()) ? m.getSupplierCode() : m.getSupplierName())
                .filter(StringUtils::hasText)
                .distinct().count();

        s.put("totalBatches", totalBatches);
        s.put("qualifiedBatches", qualifiedBatches);
        s.put("abnormalBatches", abnormalBatches);
        s.put("inCheckBatches", inCheckBatches);
        s.put("passRate", totalBatches == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(qualifiedBatches * 100.0 / totalBatches).setScale(2, RoundingMode.HALF_UP));
        s.put("ppm", totalSubmitted.compareTo(BigDecimal.ZERO) == 0 ? 0L
                : Math.round(totalUnqualified.doubleValue() * 1000000.0 / totalSubmitted.doubleValue()));
        s.put("supplierCount", supplierCount);

        // 追溯节点统计 — 改为查业务表
        LambdaQueryWrapper<FinishedGoodsInspection> fgWrapper = new LambdaQueryWrapper<>();
        fgWrapper.eq(FinishedGoodsInspection::getPlantCode, currentPlant);
        long fgTotal = fgMapper.selectCount(fgWrapper);

        LambdaQueryWrapper<FinishedGoodsInspection> sfWrapper = new LambdaQueryWrapper<>();
        sfWrapper.eq(FinishedGoodsInspection::getPlantCode, currentPlant)
                 .eq(FinishedGoodsInspection::getCategory, "半成品");
        long semiFinished = fgMapper.selectCount(sfWrapper);

        long finishedGoods = fgTotal - semiFinished;

        // allMaterials 已经包含 material_batch_no，直接复用本次查询结果，避免第二次全表扫描。
        long materialBatches = allMaterials.stream()
                .map(MaterialInspection::getMaterialBatchNo)
                .filter(StringUtils::hasText)
                .distinct().count();

        s.put("nodeCount", fgTotal + materialBatches);
        s.put("snCount", finishedGoods);
        s.put("finishedGoods", finishedGoods);
        s.put("semiFinished", semiFinished);
        s.put("materialBatches", materialBatches);

        return s;
    }

    // ==================== 核心展开 ====================

    /**
     * 核心展开方法 — 基于 critical_material_binding + 三张业务表。
     *
     * @param barcode 当前产品条码（prod_batch_or_sn 或 material_barcode）
     * @param up      true=向上追溯, false=向下追溯
     * @param path    当前路径上的条码集合（分支级防环）
     * @param visited 全局已访问条码集合（共享节点只展示一次）
     * @return 子节点列表
     */
    private List<Map<String, Object>> expand(String barcode, boolean up,
                                              Set<String> path, Set<String> visited,
                                              TraceGraphContext graph) {
        if (!path.add(barcode)) return Collections.emptyList();
        if (!visited.add(barcode)) return Collections.emptyList();

        List<CriticalMaterialBinding> bindings = up
                ? graph.upstream.getOrDefault(barcode, Collections.emptyList())
                : graph.downstream.getOrDefault(barcode, Collections.emptyList());

        List<Map<String, Object>> children = new ArrayList<>();
        for (CriticalMaterialBinding binding : bindings) {
            String targetBarcode = up ? binding.getProductBarcode() : binding.getMaterialBarcode();
            Map<String, Object> child;
            if (up) {
                FinishedGoodsInspection fg = graph.finishedGoodsByBarcode.get(targetBarcode);
                if (fg != null) {
                    child = buildNodeFromFg(fg);
                    child.put("children", expand(targetBarcode, true, new LinkedHashSet<>(path), visited, graph));
                } else {
                    child = buildOrphanNode(targetBarcode);
                }
            } else if ("\u534a\u6210\u54c1".equals(binding.getCategory())) {
                FinishedGoodsInspection fg = graph.finishedGoodsByBarcode.get(targetBarcode);
                child = fg != null ? buildNodeFromFg(fg) : buildOrphanNode(targetBarcode);
                child.put("children", expand(targetBarcode, false, new LinkedHashSet<>(path), visited, graph));
            } else {
                MaterialInspection mat = graph.materialsByBarcode.get(targetBarcode);
                child = mat != null ? buildNodeFromMat(mat) : buildOrphanNode(targetBarcode);
            }
            children.add(child);
        }
        return children;
    }

    /**
     * Loads one plant-scoped graph per request and resolves reachable business rows in batches.
     * This replaces the recursive N+1 queries from the previous implementation.
     */
    private TraceGraphContext loadGraphContext(String rootBarcode, String direction) {
        return loadGraphContext(Collections.singleton(rootBarcode), direction);
    }

    private TraceGraphContext loadGraphContext(Collection<String> rootBarcodes, String direction) {
        String currentPlant = plant();
        TraceGraphContext graph = new TraceGraphContext(currentPlant);

        QueryWrapper<CriticalMaterialBinding> bindingWrapper = new QueryWrapper<>();
        bindingWrapper.select("product_barcode", "material_barcode", "category")
                .eq("plant_code", currentPlant);
        for (CriticalMaterialBinding binding : bindingMapper.selectList(bindingWrapper)) {
            graph.downstream.computeIfAbsent(binding.getProductBarcode(), key -> new ArrayList<>()).add(binding);
            graph.upstream.computeIfAbsent(binding.getMaterialBarcode(), key -> new ArrayList<>()).add(binding);
        }

        Set<String> finishedBarcodes = new LinkedHashSet<>();
        Set<String> materialBarcodes = new LinkedHashSet<>();
        for (String rootBarcode : rootBarcodes) {
            if ("FULL".equals(direction) || "UP".equals(direction)) {
                collectReachableBarcodes(rootBarcode, true, graph, finishedBarcodes, materialBarcodes);
            }
            if ("FULL".equals(direction) || "DOWN".equals(direction)) {
                collectReachableBarcodes(rootBarcode, false, graph, finishedBarcodes, materialBarcodes);
            }
        }

        if (!finishedBarcodes.isEmpty()) {
            QueryWrapper<FinishedGoodsInspection> fgWrapper = new QueryWrapper<>();
            fgWrapper.select("id", "prod_batch_or_sn", "product_name", "material_code",
                    "model_spec", "category", "plant_code")
                    .eq("plant_code", currentPlant)
                    .in("prod_batch_or_sn", finishedBarcodes);
            for (FinishedGoodsInspection fg : fgMapper.selectList(fgWrapper)) {
                graph.finishedGoodsByBarcode.putIfAbsent(fg.getProdBatchOrSn(), fg);
            }
        }
        if (!materialBarcodes.isEmpty()) {
            QueryWrapper<MaterialInspection> matWrapper = new QueryWrapper<>();
            matWrapper.select("id", "material_barcode", "material_name", "material_code",
                    "material_batch_no", "spec_model", "plant_code")
                    .eq("plant_code", currentPlant)
                    .in("material_barcode", materialBarcodes);
            for (MaterialInspection material : matMapper.selectList(matWrapper)) {
                graph.materialsByBarcode.putIfAbsent(material.getMaterialBarcode(), material);
            }
        }
        return graph;
    }

    private void collectReachableBarcodes(String rootBarcode, boolean up,
                                          TraceGraphContext graph,
                                          Set<String> finishedBarcodes,
                                          Set<String> materialBarcodes) {
        Deque<String> queue = new ArrayDeque<>();
        Set<String> expanded = new HashSet<>();
        queue.add(rootBarcode);
        while (!queue.isEmpty()) {
            String barcode = queue.removeFirst();
            if (!expanded.add(barcode)) continue;
            List<CriticalMaterialBinding> bindings = up
                    ? graph.upstream.getOrDefault(barcode, Collections.emptyList())
                    : graph.downstream.getOrDefault(barcode, Collections.emptyList());
            for (CriticalMaterialBinding binding : bindings) {
                String target = up ? binding.getProductBarcode() : binding.getMaterialBarcode();
                if (up || "\u534a\u6210\u54c1".equals(binding.getCategory())) {
                    finishedBarcodes.add(target);
                    queue.addLast(target);
                } else {
                    materialBarcodes.add(target);
                }
            }
        }
    }

    private static final class TraceGraphContext {
        private final String plantCode;
        private final Map<String, List<CriticalMaterialBinding>> downstream = new HashMap<>();
        private final Map<String, List<CriticalMaterialBinding>> upstream = new HashMap<>();
        private final Map<String, FinishedGoodsInspection> finishedGoodsByBarcode = new HashMap<>();
        private final Map<String, MaterialInspection> materialsByBarcode = new HashMap<>();

        private TraceGraphContext(String plantCode) {
            this.plantCode = plantCode;
        }
    }
    private Map<String, Object> batchImpact(String batchNo) {
        String currentPlant = plant();

        LambdaQueryWrapper<MaterialInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialInspection::getMaterialBatchNo, batchNo)
               .eq(MaterialInspection::getPlantCode, currentPlant)
               .isNotNull(MaterialInspection::getMaterialBarcode);
        List<MaterialInspection> materials = matMapper.selectList(wrapper);

        if (materials.isEmpty()) {
            throw new IllegalArgumentException("未找到物料批次：" + batchNo);
        }

        Set<String> visited = new LinkedHashSet<>();
        Set<String> batchBarcodes = new LinkedHashSet<>();
        for (MaterialInspection material : materials) {
            if (StringUtils.hasText(material.getMaterialBarcode())) {
                batchBarcodes.add(material.getMaterialBarcode());
            }
        }
        TraceGraphContext graph = loadGraphContext(batchBarcodes, "UP");
        List<Map<String, Object>> lots = new ArrayList<>();
        for (MaterialInspection mat : materials) {
            Map<String, Object> node = buildNodeFromMat(mat);
            node.put("children", expand(mat.getMaterialBarcode(), true, new LinkedHashSet<>(), visited, graph));
            lots.add(node);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "batch-root");
        root.put("nodeType", "BATCH");
        root.put("barcode", batchNo);
        root.put("name", "来料批次影响范围");
        root.put("children", lots);

        return result(root, visited.size(), "BATCH_IMPACT", lots.size());
    }

    // ==================== 节点构建（§6.4 字段映射） ====================

    /** 构建成品/半成品节点 DTO */
    private Map<String, Object> buildNodeFromFg(FinishedGoodsInspection fg) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "fg_" + fg.getId());
        node.put("nodeType", "半成品".equals(fg.getCategory()) ? "SEMI_FINISHED" : "FINISHED_GOOD");
        node.put("barcode", fg.getProdBatchOrSn());
        node.put("name", StringUtils.hasText(fg.getProductName()) ? fg.getProductName() : fg.getProdBatchOrSn());
        node.put("productCode", fg.getMaterialCode());
        node.put("materialCode", null);
        node.put("materialBatchNo", null);
        node.put("specification", fg.getModelSpec());
        node.put("plantCode", fg.getPlantCode());
        node.put("finishedGoodsInspectionId", fg.getId());
        node.put("materialInspectionId", null);
        node.put("category", fg.getCategory());
        return node;
    }

    /** 构建物料节点 DTO */
    private Map<String, Object> buildNodeFromMat(MaterialInspection mat) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "mi_" + mat.getId());
        node.put("nodeType", "MATERIAL");
        node.put("barcode", mat.getMaterialBarcode());
        node.put("name", StringUtils.hasText(mat.getMaterialName()) ? mat.getMaterialName() : mat.getMaterialBarcode());
        node.put("productCode", null);
        node.put("materialCode", mat.getMaterialCode());
        node.put("materialBatchNo", mat.getMaterialBatchNo());
        node.put("specification", mat.getSpecModel());
        node.put("plantCode", mat.getPlantCode());
        node.put("finishedGoodsInspectionId", null);
        node.put("materialInspectionId", mat.getId());
        return node;
    }

    /** 构建孤立节点（条码在业务表中找不到对应记录） */
    private Map<String, Object> buildOrphanNode(String barcode) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "orphan_" + barcode);
        node.put("nodeType", "UNKNOWN");
        node.put("barcode", barcode);
        node.put("name", barcode);
        node.put("productCode", null);
        node.put("materialCode", null);
        node.put("materialBatchNo", null);
        node.put("specification", null);
        node.put("plantCode", null);
        node.put("finishedGoodsInspectionId", null);
        node.put("materialInspectionId", null);
        return node;
    }

    // ==================== 辅助方法 ====================

    /** 根节点构建：尝试从成品表、物料表查找 */
    private Map<String, Object> buildRootNode(String barcode) {
        String currentPlant = plant();

        // 尝试成品表
        FinishedGoodsInspection fg = selectFirstFg(barcode, currentPlant);
        if (fg != null) {
            return buildNodeFromFg(fg);
        }

        // 尝试物料表
        MaterialInspection mat = matMapper.selectOne(
                new LambdaQueryWrapper<MaterialInspection>()
                        .eq(MaterialInspection::getMaterialBarcode, barcode)
                        .eq(MaterialInspection::getPlantCode, currentPlant));
        if (mat != null) {
            return buildNodeFromMat(mat);
        }

        return null;
    }

    /**
     * 查询成品表，取第一条（prod_batch_or_sn 非唯一索引，可能多条）。
     * 多条时打 WARN 日志。
     */
    private FinishedGoodsInspection selectFirstFg(String prodBatchOrSn, String plant) {
        LambdaQueryWrapper<FinishedGoodsInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinishedGoodsInspection::getProdBatchOrSn, prodBatchOrSn)
               .eq(FinishedGoodsInspection::getPlantCode, plant);
        List<FinishedGoodsInspection> list = fgMapper.selectList(wrapper);
        if (list.size() > 1) {
            log.warn("prod_batch_or_sn={} plant={} 存在{}条记录, 取第一条",
                    prodBatchOrSn, plant, list.size());
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /** 结果包装 */
    private Map<String, Object> result(Map<String, Object> root, int visitedCount,
                                        String direction, int batchLots) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("root", root);
        r.put("direction", direction);
        r.put("summary", summary());
        r.put("visitedNodes", visitedCount);
        r.put("batchLots", batchLots);
        return r;
    }

    /** 当前登录用户厂区 */
    private String plant() {
        LoginUser user = LoginUserHolder.get();
        return user == null || user.getPlantCode() == null ? null : user.getPlantCode().name();
    }

    /**
     * 按 itemType + 条码查询单条产品/物料主数据，用于 FAI/SPC 录入时自动带出代码、名称、批次号。
     *
     * @param itemType PRODUCT(成品表) / MATERIAL(物料表)
     * @param barcode  产品条码(prod_batch_or_sn) 或 物料条码(material_barcode)
     * @return {itemCode, itemName, batchNo}
     */
    public Map<String, Object> itemByBarcode(String itemType, String barcode) {
        if (!StringUtils.hasText(itemType) || !StringUtils.hasText(barcode)) {
            throw new IllegalArgumentException("itemType 与 barcode 不能为空");
        }
        String currentPlant = plant();
        barcode = barcode.trim();

        Map<String, Object> result = new LinkedHashMap<>();
        if ("PRODUCT".equalsIgnoreCase(itemType)) {
            FinishedGoodsInspection fg = selectFirstFg(barcode, currentPlant);
            if (fg == null) {
                throw new IllegalArgumentException("未找到产品条码：" + barcode);
            }
            result.put("itemCode", fg.getMaterialCode());
            result.put("itemName", fg.getProductName());
            result.put("batchNo", fg.getProdBatchOrSn());
        } else if ("MATERIAL".equalsIgnoreCase(itemType)) {
            MaterialInspection mat = matMapper.selectOne(
                    new LambdaQueryWrapper<MaterialInspection>()
                            .eq(MaterialInspection::getMaterialBarcode, barcode)
                            .eq(MaterialInspection::getPlantCode, currentPlant));
            if (mat == null) {
                throw new IllegalArgumentException("未找到物料条码：" + barcode);
            }
            result.put("itemCode", mat.getMaterialCode());
            result.put("itemName", mat.getMaterialName());
            result.put("batchNo", mat.getMaterialBatchNo());
        } else {
            throw new IllegalArgumentException("不支持的 itemType: " + itemType);
        }
        return result;
    }

    /**
     * 按 itemType + 关键字模糊搜索产品/物料主数据，用于录入时下拉候选。
     * 关键字对「条码 / 代码 / 名称」三列做 LIKE %keyword% OR 匹配（不区分大小写），
     * 限定 currentPlant 且未删除（@TableLogic 自动生效）。
     *
     * @param itemType PRODUCT(成品表) / MATERIAL(物料表)
     * @param keyword  条码/代码/名称关键字（自动 trim，空则返回空列表）
     * @param limit    最大返回条数（默认 20）
     * @return [{barcode, itemCode, itemName, batchNo}]
     */
    public List<Map<String, Object>> searchByBarcode(String itemType, String keyword, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!StringUtils.hasText(itemType) || !StringUtils.hasText(keyword)) {
            return results;
        }
        String currentPlant = plant();
        final String kw = keyword.trim();
        int max = limit > 0 ? limit : 20;

        if ("PRODUCT".equalsIgnoreCase(itemType)) {
            List<FinishedGoodsInspection> list = fgMapper.selectList(
                    new LambdaQueryWrapper<FinishedGoodsInspection>()
                            .eq(FinishedGoodsInspection::getPlantCode, currentPlant)
                            .and(w -> w.like(FinishedGoodsInspection::getProdBatchOrSn, kw)
                                    .or().like(FinishedGoodsInspection::getMaterialCode, kw)
                                    .or().like(FinishedGoodsInspection::getProductName, kw))
                            .last("LIMIT " + max));
            for (FinishedGoodsInspection fg : list) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("barcode", fg.getProdBatchOrSn());
                r.put("itemCode", fg.getMaterialCode());
                r.put("itemName", fg.getProductName());
                r.put("batchNo", fg.getProdBatchOrSn());
                results.add(r);
            }
        } else if ("MATERIAL".equalsIgnoreCase(itemType)) {
            List<MaterialInspection> list = matMapper.selectList(
                    new LambdaQueryWrapper<MaterialInspection>()
                            .eq(MaterialInspection::getPlantCode, currentPlant)
                            .and(w -> w.like(MaterialInspection::getMaterialBarcode, kw)
                                    .or().like(MaterialInspection::getMaterialCode, kw)
                                    .or().like(MaterialInspection::getMaterialName, kw))
                            .last("LIMIT " + max));
            for (MaterialInspection mat : list) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("barcode", mat.getMaterialBarcode());
                r.put("itemCode", mat.getMaterialCode());
                r.put("itemName", mat.getMaterialName());
                r.put("batchNo", mat.getMaterialBatchNo());
                results.add(r);
            }
        }
        return results;
    }
}
