package com.kangli.qms.api.trace;

import com.kangli.qms.common.R;
import com.kangli.qms.service.trace.IncomingTraceService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/incoming-trace")
public class IncomingTraceController {
    private final IncomingTraceService service;
    public IncomingTraceController(IncomingTraceService service){this.service=service;}
    @GetMapping("/tree") public R<Map<String,Object>> tree(@RequestParam String rootBarcode,@RequestParam(defaultValue="DOWN")String direction){return R.ok(service.tree(rootBarcode,direction));}
    @GetMapping("/root-barcode") public R<String> rootBarcode(@RequestParam String sourceType, @RequestParam long sourceId){return R.ok(service.rootBarcode(sourceType, sourceId));}
    @GetMapping("/nodes/{id}") public R<Map<String,Object>> node(@PathVariable long id){return R.ok(service.node(id));}
    @GetMapping("/nodes") public R<List<Map<String,Object>>> nodes(){return R.ok(service.nodes());}
    @GetMapping("/relations") public R<List<Map<String,Object>>> relations(){return R.ok(service.relations());}
    @GetMapping("/summary") public R<Map<String,Object>> summary(){return R.ok(service.summary());}
    @PostMapping("/nodes") public R<Long> createNode(@RequestBody Map<String,Object> body){return R.ok(service.createNode(body));}
    @PostMapping("/relations") public R<Long> createRelation(@RequestBody Map<String,Object> body){return R.ok(service.createRelation(body));}

    /** 快捷绑定：将来料检验记录与成品检验记录关联到追溯图 */
    @PostMapping("/bind")
    public R<Map<String,Object>> bind(@RequestBody Map<String,Object> body) {
        Long materialId = body.get("materialInspectionId") instanceof Number
            ? ((Number) body.get("materialInspectionId")).longValue() : null;
        Long finishedGoodsId = body.get("finishedGoodsInspectionId") instanceof Number
            ? ((Number) body.get("finishedGoodsInspectionId")).longValue() : null;
        if (materialId == null || finishedGoodsId == null)
            throw new IllegalArgumentException("请提供 materialInspectionId 和 finishedGoodsInspectionId");
        return R.ok(service.bindMaterialToFinishedGoods(materialId, finishedGoodsId));
    }
}
