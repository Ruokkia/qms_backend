package com.kangli.qms.common;

import lombok.Getter;

/**
 * 业务异常 — 所有可预期的业务错误通过此异常抛出，
 * 由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@link R} 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
