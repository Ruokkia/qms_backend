package com.kangli.qms.integration.testctrl;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅用于测试的辅助 Controller：稳定触发未捕获 {@link RuntimeException}，
 * 以验证 {@code GlobalExceptionHandler} 兜底分支（XC-032）。
 * 位于 src/test 源码树，不进入生产包，不影响任何业务端点。
 */
@RestController
public class BoomController {

    /** GET /api/v1/exceptions/__boom__ -> 故意抛出未捕获 RuntimeException（路径落在已映射模块，可绕过权限到达 controller）。 */
    @GetMapping("/api/v1/exceptions/__boom__")
    public String boom() {
        throw new RuntimeException("boom-unittest-trigger");
    }
}
