package com.kangli.qms.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 统一响应体。
 * <p>结构：{ code, message, data, timestamp, traceId }</p>
 * <ul>
 *   <li>code — 0=成功，非0=失败（业务错误码如 1001~1005）</li>
 *   <li>message — 描述信息</li>
 *   <li>data — 业务数据（成功时返回，失败时为 null）</li>
 *   <li>timestamp — 服务器时间戳</li>
 *   <li>traceId — 链路追踪 ID（由 TraceIdFilter 生成，贯穿整条请求）</li>
 * </ul>
 */
@Data
@ApiModel(description = "统一响应体")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码 */
    public static final int CODE_SUCCESS = 0;

    @ApiModelProperty(value = "状态码：0=成功，非0=失败", example = "0")
    private int code;

    @ApiModelProperty(value = "描述信息", example = "登录成功")
    private String message;

    @ApiModelProperty(value = "业务数据")
    private T data;

    @ApiModelProperty(value = "服务器时间戳", example = "2026-07-17 10:30:00")
    private LocalDateTime timestamp;

    @ApiModelProperty(value = "链路追踪ID", example = "a1b2c3d4")
    private String traceId;

    public R() {
    }

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        this.traceId = TraceIdHolder.getTraceId();
    }

    // ---- 静态工厂 ----

    public static <T> R<T> ok() {
        return new R<>(CODE_SUCCESS, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CODE_SUCCESS, "success", data);
    }

    public static <T> R<T> ok(T data, String message) {
        return new R<>(CODE_SUCCESS, message, data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }
}
