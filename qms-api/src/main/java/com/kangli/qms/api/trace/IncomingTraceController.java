package com.kangli.qms.api.trace;

import com.kangli.qms.common.R;
import com.kangli.qms.service.trace.IncomingTraceService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 追溯查询 API — 后端已切换到三张业务表实现，输出格式兼容旧版前端。
 */
@RestController
@RequestMapping("/api/v2/incoming-trace")
public class IncomingTraceController {

    private final IncomingTraceService service;

    public IncomingTraceController(IncomingTraceService service) {
        this.service = service;
    }

    /** 追溯树查询 */
    @GetMapping("/tree")
    public R<Map<String, Object>> tree(@RequestParam String rootBarcode,
                                       @RequestParam(defaultValue = "DOWN") String direction) {
        return R.ok(service.tree(rootBarcode, direction));
    }

    /**
     * 单节点详情。
     *
     * <p>id 支持条码编码（fg:{barcode} / mi:{barcode}）与旧版数字主键；
     * sonLotNo 为半成品子项批号（可选），优先用其查成品表。</p>
     */
    @GetMapping("/nodes/{id}")
    public R<Map<String, Object>> node(@PathVariable String id,
                                       @RequestParam String type,
                                       @RequestParam(required = false) String sonLotNo) {
        return R.ok(service.node(id, type, sonLotNo));
    }

    /** 所有节点列表 */
    @GetMapping("/nodes")
    public R<List<Map<String, Object>>> nodes() {
        return R.ok(service.nodes());
    }

    /** 所有关系列表 */
    @GetMapping("/relations")
    public R<List<Map<String, Object>>> relations() {
        return R.ok(service.relations());
    }

    /** 追溯仪表盘统计 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(service.summary());
    }

    /** 按分类+条码查询单条产品/物料主数据（自动带出代码/名称/批次号） */
    @GetMapping("/item")
    public R<Map<String, Object>> itemByBarcode(@RequestParam String itemType,
                                                @RequestParam String barcode) {
        return R.ok(service.itemByBarcode(itemType, barcode));
    }

    /** 按分类+关键字模糊搜索产品/物料主数据，用于录入时下拉候选 */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> searchByBarcode(
            @RequestParam String itemType,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(service.searchByBarcode(itemType, keyword, limit));
    }
}
