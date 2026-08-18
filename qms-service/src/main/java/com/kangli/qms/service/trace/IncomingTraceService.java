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
            return result(root, upVisited.size() + downVisited.size(), direction, 0, graph);
        } else {
            Set<String> visited = new LinkedHashSet<>();
            boolean up = "UP".equals(direction);
            root.put("children", expand(rootBarcode, up, new LinkedHashSet<>(), visited, graph));
            return result(root, visited.size(), direction, 0, graph);
        }
    }

    /**
     * 单节点详情。
     *
     * <p>v1.2 改造：id 支持条码编码（fg:{barcode} / mi:{barcode}）与旧版数字主键两种格式；
     * 新增可选参数 sonLotNo，半成品节点优先用其查成品表（prod_batch_or_sn = sonLotNo）。</p>
     *
     * @param id       节点 id（fg:{barcode} / mi:{barcode}，兼容旧版数字主键）
     * @param type     "fg" = finished_goods_inspection, "mi" = material_inspection
     * @param sonLotNo 子项批号（可选）：半成品节点用其查成品表，为空则回退 id 中解析的条码
     */
    public Map<String, Object> node(String id, String type, String sonLotNo) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("缺少 type 参数（fg/mi）");
        }
        String currentPlant = plant();

        if ("fg".equalsIgnoreCase(type)) {
            // 半成品节点：优先用 sonLotNo 查成品表（可能为批号，成品表存的是批号）
            if (StringUtils.hasText(sonLotNo)) {
                FinishedGoodsInspection fgBySon = selectFirstFg(sonLotNo.trim(), currentPlant);
                if (fgBySon != null) {
                    return buildNodeFromFg(fgBySon);
                }
                // sonLotNo 查不到时不立即报错，继续回退 id 中解析的条码
            }
            // 成品节点：按唯一条码查；半成品回退：按 material_barcode 查
            FinishedGoodsInspection fg = resolveFgNode(id, currentPlant);
            if (fg == null) {
                // 绑定表回退：部分半成品仅存在于绑定表（material_barcode），成品表无对应记录
                String barcode = resolveBarcodeFromId(id);
                if (barcode != null) {
                    CriticalMaterialBinding binding = bindingMapper.selectOne(
                            new LambdaQueryWrapper<CriticalMaterialBinding>()
                                    .eq(CriticalMaterialBinding::getPlantCode, currentPlant)
                                    .eq(CriticalMaterialBinding::getMaterialBarcode, barcode)
                                    .last("LIMIT 1"));
                    if (binding != null) {
                        return buildNodeFromBinding(binding,
                                "半成品".equals(binding.getCategory()) ? "SEMI_FINISHED" : "MATERIAL");
                    }
                }
                throw new IllegalArgumentException("节点不存在");
            }
            return buildNodeFromFg(fg);
        } else if ("mi".equalsIgnoreCase(type)) {
            MaterialInspection mat = resolveMatNode(id, currentPlant);
            if (mat == null) {
                throw new IllegalArgumentException("节点不存在");
            }
            return buildNodeFromMat(mat);
        }
        throw new IllegalArgumentException("不支持的 type: " + type);
    }

    /**
     * 从节点 id 解析成品表记录：
     * <ul>
     *   <li>fg:{barcode} → prod_batch_or_sn = barcode 查询（含厂区过滤，多条取第一条）</li>
     *   <li>纯数字 → 旧版主键 selectById</li>
     * </ul>
     */
    private FinishedGoodsInspection resolveFgNode(String id, String currentPlant) {
        String barcode = resolveBarcodeFromId(id);
        if (barcode != null) {
            return selectFirstFg(barcode, currentPlant);
        }
        if (StringUtils.hasText(id) && id.trim().matches("\\d+")) {
            FinishedGoodsInspection fg = fgMapper.selectById(Long.parseLong(id.trim()));
            if (fg != null && Objects.equals(fg.getPlantCode(), currentPlant)) {
                return fg;
            }
        }
        return null;
    }

    /**
     * 从节点 id 解析物料表记录：
     * <ul>
     *   <li>mi:{barcode} → material_barcode = barcode 查询</li>
     *   <li>纯数字 → 旧版主键 selectById</li>
     * </ul>
     */
    private MaterialInspection resolveMatNode(String id, String currentPlant) {
        String barcode = resolveBarcodeFromId(id);
        if (barcode != null) {
            return matMapper.selectOne(
                    new LambdaQueryWrapper<MaterialInspection>()
                            .eq(MaterialInspection::getMaterialBarcode, barcode)
                            .eq(MaterialInspection::getPlantCode, currentPlant));
        }
        if (StringUtils.hasText(id) && id.trim().matches("\\d+")) {
            MaterialInspection mat = matMapper.selectById(Long.parseLong(id.trim()));
            if (mat != null && Objects.equals(mat.getPlantCode(), currentPlant)) {
                return mat;
            }
        }
        return null;
    }

    /** 从 fg:{barcode} / mi:{barcode} 中解析条码；纯数字或空返回 null */
    private String resolveBarcodeFromId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        String trimmed = id.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon < trimmed.length() - 1) {
            return trimmed.substring(colon + 1);
        }
        // 兼容：前端解析 "fg:{barcode}" 后仅传纯条码（无前缀）的情况
        if (!trimmed.matches("\\d+")) {
            return trimmed;
        }
        return null;
    }

    /**
     * 所有节点列表（成品/半成品 + 物料合并），输出格式兼容前端。
     */
    public List<Map<String, Object>> nodes() {
        String currentPlant = plant();
        List<Map<String, Object>> result = new ArrayList<>();

        // 成品 / 半成品（该接口仅用于前端快捷查询条码，取前 N 条即可，避免全表扫描拖慢页面加载）
        LambdaQueryWrapper<FinishedGoodsInspection> fgWrapper = new LambdaQueryWrapper<>();
        fgWrapper.eq(FinishedGoodsInspection::getPlantCode, currentPlant)
                 .orderByAsc(FinishedGoodsInspection::getId)
                 .last("LIMIT 200");
        for (FinishedGoodsInspection fg : fgMapper.selectList(fgWrapper)) {
            result.add(buildNodeFromFg(fg));
        }

        // 物料（仅含 material_barcode 非空的记录，才能参与追溯）
        LambdaQueryWrapper<MaterialInspection> matWrapper = new LambdaQueryWrapper<>();
        matWrapper.eq(MaterialInspection::getPlantCode, currentPlant)
                  .isNotNull(MaterialInspection::getMaterialBarcode)
                  .orderByAsc(MaterialInspection::getId)
                  .last("LIMIT 200");
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
            row.put("plantCode", b.getPlantCode());
            row.put("sonLotNo", b.getSonLotNo());
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
                // 向上-父节点：优先用成品表记录构建，正确区分成品/半成品（v1.5）
                // 绑定表 category 仅描述子项类型；父项类型需查成品表（与 buildRootNode v1.4 一致）
                FinishedGoodsInspection parentFg = graph.finishedGoodsByBarcode.get(targetBarcode);
                child = parentFg != null ? buildNodeFromFg(parentFg)
                                         : buildNodeFromBinding(binding, "FINISHED_GOOD");
                child.put("children", expand(targetBarcode, true, new LinkedHashSet<>(path), visited, graph));
            } else if ("\u534a\u6210\u54c1".equals(binding.getCategory())) {
                // 向下-半成品：携带 sonLotNo（详情查询用，v1.2）
                child = buildNodeFromBinding(binding, "SEMI_FINISHED");
                child.put("children", expand(targetBarcode, false, new LinkedHashSet<>(path), visited, graph));
            } else {
                // 向下-物料：优先取来料检验单，带出实际物料批号。
                MaterialInspection material = graph.materialsByBarcode.get(targetBarcode);
                child = material != null ? buildNodeFromMat(material)
                        : buildNodeFromBinding(binding, "MATERIAL");
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

    /**
     * 按追溯方向逐层加载绑定关系，只读取当前根节点可达的关系，避免扫描整个分公司的绑定表。
     */
    private TraceGraphContext loadGraphContext(Collection<String> rootBarcodes, String direction) {
        String currentPlant = plant();
        TraceGraphContext graph = new TraceGraphContext(currentPlant);
        Set<String> finishedBarcodes = new LinkedHashSet<>();
        Set<String> materialBarcodes = new LinkedHashSet<>();

        if ("FULL".equals(direction) || "UP".equals(direction)) {
            loadBindingLayers(rootBarcodes, true, graph, finishedBarcodes, materialBarcodes);
        }
        if ("FULL".equals(direction) || "DOWN".equals(direction)) {
            loadBindingLayers(rootBarcodes, false, graph, finishedBarcodes, materialBarcodes);
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

    private void loadBindingLayers(Collection<String> rootBarcodes, boolean up,
                                   TraceGraphContext graph,
                                   Set<String> finishedBarcodes,
                                   Set<String> materialBarcodes) {
        Set<String> frontier = new LinkedHashSet<>(rootBarcodes);
        Set<String> queried = new HashSet<>();
        String lookupColumn = up ? "material_barcode" : "product_barcode";

        while (!frontier.isEmpty()) {
            frontier.removeAll(queried);
            if (frontier.isEmpty()) break;

            QueryWrapper<CriticalMaterialBinding> wrapper = new QueryWrapper<>();
            // v1.1：补齐节点展示所需字段（product_name/material_name/son_lot_no 等）
            wrapper.select("product_barcode", "product_name", "material_barcode",
                    "material_name", "material_code", "category", "spec_model",
                    "plant_code", "son_lot_no")
                    .eq("plant_code", graph.plantCode)
                    .in(lookupColumn, frontier);
            List<CriticalMaterialBinding> bindings = bindingMapper.selectList(wrapper);
            queried.addAll(frontier);

            Set<String> next = new LinkedHashSet<>();
            for (CriticalMaterialBinding binding : bindings) {
                graph.downstream.computeIfAbsent(binding.getProductBarcode(), key -> new ArrayList<>()).add(binding);
                graph.upstream.computeIfAbsent(binding.getMaterialBarcode(), key -> new ArrayList<>()).add(binding);

                String target = up ? binding.getProductBarcode() : binding.getMaterialBarcode();
                if (up || "\u534a\u6210\u54c1".equals(binding.getCategory())) {
                    finishedBarcodes.add(target);
                    next.add(target);
                } else {
                    materialBarcodes.add(target);
                }
            }
            frontier = next;
        }
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

        return result(root, visited.size(), "BATCH_IMPACT", lots.size(), graph);
    }

    // ==================== 节点构建（§6.4 字段映射） ====================

    /**
     * 基于绑定表记录构建树节点（v1.1：不预查业务表）。
     *
     * <p>id 编码规则（v1.2）：成品/半成品 → fg:{barcode}；物料 → mi:{barcode}。
     * 半成品节点携带 sonLotNo 供详情查询使用。</p>
     *
     * @param binding  绑定表记录
     * @param nodeType FINISHED_GOOD / SEMI_FINISHED / MATERIAL
     */
    private Map<String, Object> buildNodeFromBinding(CriticalMaterialBinding binding, String nodeType) {
        boolean isFinished = "FINISHED_GOOD".equals(nodeType);
        boolean isMaterial = "MATERIAL".equals(nodeType);
        String barcode = isFinished ? binding.getProductBarcode() : binding.getMaterialBarcode();
        String name = isFinished ? binding.getProductName() : binding.getMaterialName();
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", (isMaterial ? "mi:" : "fg:") + barcode);
        node.put("nodeType", nodeType);
        node.put("barcode", barcode);
        node.put("name", StringUtils.hasText(name) ? name : barcode);
        node.put("productCode", isFinished ? null : binding.getMaterialCode());
        node.put("materialCode", isFinished ? null : binding.getMaterialCode());
        // 绑定表没有保存来料批号；半成品可用关联的子项批号作为批次展示。
        node.put("batchNo", "SEMI_FINISHED".equals(nodeType) ? binding.getSonLotNo() : null);
        node.put("materialBatchNo", null);
        node.put("specification", binding.getSpecModel());
        node.put("plantCode", binding.getPlantCode());
        node.put("finishedGoodsInspectionId", null);
        node.put("materialInspectionId", null);
        node.put("category", binding.getCategory());
        node.put("sonLotNo", binding.getSonLotNo());
        return node;
    }

    /** 构建成品/半成品节点 DTO */
    private Map<String, Object> buildNodeFromFg(FinishedGoodsInspection fg) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "fg_" + fg.getId());
        node.put("nodeType", "半成品".equals(fg.getCategory()) ? "SEMI_FINISHED" : "FINISHED_GOOD");
        node.put("barcode", fg.getProdBatchOrSn());
        node.put("name", StringUtils.hasText(fg.getProductName()) ? fg.getProductName() : fg.getProdBatchOrSn());
        node.put("productCode", fg.getMaterialCode());
        node.put("materialCode", null);
        node.put("batchNo", fg.getProdBatchOrSn());
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
        node.put("batchNo", mat.getMaterialBatchNo());
        node.put("materialBatchNo", mat.getMaterialBatchNo());
        node.put("specification", mat.getSpecModel());
        node.put("plantCode", mat.getPlantCode());
        node.put("finishedGoodsInspectionId", null);
        node.put("materialInspectionId", mat.getId());
        return node;
    }

    // ==================== 辅助方法 ====================

    /**
     * 根节点构建（v1.4：绑定表优先）。
     *
     * <p>当看板传入半成品唯一条码（如 S001）时，成品表存的是批号，直接按条码查成品表查不到，
     * 因此必须<strong>先查绑定表</strong>（material_barcode = 输入 或 product_barcode = 输入），
     * 命中后用绑定表字段构建根节点；绑定表无记录才回退业务表兜底。</p>
     */
    private Map<String, Object> buildRootNode(String barcode) {
        String currentPlant = plant();

        // 1) 绑定表优先（v1.4）：子项或父项条码 = 输入
        CriticalMaterialBinding binding = bindingMapper.selectOne(
                new LambdaQueryWrapper<CriticalMaterialBinding>()
                        .eq(CriticalMaterialBinding::getPlantCode, currentPlant)
                        .and(w -> w.eq(CriticalMaterialBinding::getMaterialBarcode, barcode)
                                .or().eq(CriticalMaterialBinding::getProductBarcode, barcode))
                        .last("LIMIT 1"));
        if (binding != null) {
            // 输入为父项（product_barcode）时，优先用成品表构建：成品表能按 category 正确区分
            // 成品/半成品（梅州数据中半成品条码同时充当父项与子项，仅凭绑定表会被误判为成品）。
            if (barcode.equals(binding.getProductBarcode())) {
                FinishedGoodsInspection parentFg = selectFirstFg(barcode, currentPlant);
                if (parentFg != null) {
                    return buildNodeFromFg(parentFg);
                }
            }
            // 输入为物料条码时，优先使用来料检验单，才能返回物料批号。
            if (barcode.equals(binding.getMaterialBarcode())) {
                MaterialInspection material = matMapper.selectOne(
                        new LambdaQueryWrapper<MaterialInspection>()
                                .eq(MaterialInspection::getMaterialBarcode, barcode)
                                .eq(MaterialInspection::getPlantCode, currentPlant)
                                .last("LIMIT 1"));
                if (material != null) {
                    return buildNodeFromMat(material);
                }
            }
            String nodeType = classifyRootNode(binding, barcode);
            return buildNodeFromBinding(binding, nodeType);
        }

        // 2) 业务表兜底：孤立成品/物料（仅展示自身，无关联）
        FinishedGoodsInspection fg = selectFirstFg(barcode, currentPlant);
        if (fg != null) {
            return buildNodeFromFg(fg);
        }
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
     * 根据绑定记录与输入条码判定根节点类型：
     * <ul>
     *   <li>输入 = product_barcode → 该行为父项，判定为成品（FINISHED_GOOD）</li>
     *   <li>输入 = material_barcode 且分类为半成品 → SEMI_FINISHED</li>
     *   <li>输入 = material_barcode 且分类为物料 → MATERIAL</li>
     * </ul>
     */
    private String classifyRootNode(CriticalMaterialBinding binding, String inputBarcode) {
        if (inputBarcode.equals(binding.getProductBarcode())) {
            return "FINISHED_GOOD";
        }
        return "\u534a\u6210\u54c1".equals(binding.getCategory()) ? "SEMI_FINISHED" : "MATERIAL";
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
                                        String direction, int batchLots,
                                        TraceGraphContext graph) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("root", root);
        r.put("direction", direction);
        Map<String, Object> lightweightSummary = new LinkedHashMap<>();
        lightweightSummary.put("materialBatches", graph.materialsByBarcode.size());
        lightweightSummary.put("supplierCount", 0);
        lightweightSummary.put("nodeCount", graph.finishedGoodsByBarcode.size()
                + graph.materialsByBarcode.size());
        r.put("summary", lightweightSummary);
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
    public List<Map<String, Object>> searchByBarcode(String itemType, String keyword, int limit, String itemCode) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!StringUtils.hasText(itemType) || !StringUtils.hasText(keyword)) {
            return results;
        }
        String currentPlant = plant();
        final String kw = keyword.trim();
        int max = limit > 0 ? limit : 20;
        final boolean restrictItemCode = StringUtils.hasText(itemCode);

        if ("PRODUCT".equalsIgnoreCase(itemType)) {
            LambdaQueryWrapper<FinishedGoodsInspection> qw =
                    new LambdaQueryWrapper<FinishedGoodsInspection>()
                            .eq(FinishedGoodsInspection::getPlantCode, currentPlant);
            if (restrictItemCode) {
                qw.eq(FinishedGoodsInspection::getMaterialCode, itemCode);
            }
            qw.and(w -> w.like(FinishedGoodsInspection::getProdBatchOrSn, kw)
                    .or().like(FinishedGoodsInspection::getMaterialCode, kw)
                    .or().like(FinishedGoodsInspection::getProductName, kw))
              .last("LIMIT " + max);
            List<FinishedGoodsInspection> list = fgMapper.selectList(qw);
            for (FinishedGoodsInspection fg : list) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("barcode", fg.getProdBatchOrSn());
                r.put("itemCode", fg.getMaterialCode());
                r.put("itemName", fg.getProductName());
                r.put("batchNo", fg.getProdBatchOrSn());
                results.add(r);
            }
        } else if ("MATERIAL".equalsIgnoreCase(itemType)) {
            LambdaQueryWrapper<MaterialInspection> qw =
                    new LambdaQueryWrapper<MaterialInspection>()
                            .eq(MaterialInspection::getPlantCode, currentPlant);
            if (restrictItemCode) {
                qw.eq(MaterialInspection::getMaterialCode, itemCode);
            }
            qw.and(w -> w.like(MaterialInspection::getMaterialBarcode, kw)
                    .or().like(MaterialInspection::getMaterialBatchNo, kw)
                    .or().like(MaterialInspection::getMaterialCode, kw)
                    .or().like(MaterialInspection::getMaterialName, kw))
              .last("LIMIT " + max);
            List<MaterialInspection> list = matMapper.selectList(qw);
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
