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

    /** 单节点详情（新增 type 参数：fg=成品表, mi=物料表） */
    @GetMapping("/nodes/{id}")
    public R<Map<String, Object>> node(@PathVariable long id,
                                       @RequestParam String type) {
        return R.ok(service.node(id, type));
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
}
