package com.kangli.qms.common;

/**
 * TraceId 持有器（基于 ThreadLocal）。
 * <p>由 {@link com.kangli.qms.config.TraceIdFilter} 在请求入口生成并写入，
 * {@link R} 响应体构建时读取，请求结束后由 Filter 清除。</p>
 */
public final class TraceIdHolder {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
