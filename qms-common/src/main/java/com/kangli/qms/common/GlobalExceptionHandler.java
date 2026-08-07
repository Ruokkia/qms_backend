package com.kangli.qms.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>统一捕获各类异常，转换为 {@link R} 响应体，避免堆栈泄露到前端。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] traceId={}, uri={}, code={}, msg={}",
                TraceIdHolder.getTraceId(), request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 非法参数异常（业务校验，如未找到节点、参数为空等） */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[非法参数] traceId={}, uri={}, msg={}", TraceIdHolder.getTraceId(), request.getRequestURI(), e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    /** 参数校验异常（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] traceId={}, errors={}", TraceIdHolder.getTraceId(), msg);
        return R.fail(ResultCode.BAD_REQUEST, msg);
    }

    /** 参数绑定异常（表单/QS） */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] traceId={}, errors={}", TraceIdHolder.getTraceId(), msg);
        return R.fail(ResultCode.BAD_REQUEST, msg);
    }

    /** 请求体反序列化失败（如JSON格式错误、字段类型不匹配） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[请求体解析失败] traceId={}, uri={}, msg={}", TraceIdHolder.getTraceId(), request.getRequestURI(), e.getMessage());
        String detail = e.getMessage();
        if (detail != null && detail.contains(":")) {
            detail = detail.substring(detail.lastIndexOf(":") + 1).trim();
        }
        return R.fail(ResultCode.BAD_REQUEST, "请求数据格式错误，请检查：" + (detail != null ? detail : "字段类型不匹配"));
    }

    /** 未知异常兜底 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] traceId={}, uri={}", TraceIdHolder.getTraceId(), request.getRequestURI(), e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }
}
