package com.kangli.qms.config;

import com.kangli.qms.common.TraceIdHolder;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 过滤器。
 * <p>在请求最前端生成唯一 traceId（短 UUID 无连字符），写入 ThreadLocal。
 * 若请求头已携带 X-Trace-Id 则沿用。响应结束后清除 ThreadLocal。</p>
 */
@Component
@WebFilter(filterName = "traceIdFilter", urlPatterns = "/*")
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        try {
            TraceIdHolder.setTraceId(traceId);
            chain.doFilter(request, response);
        } finally {
            TraceIdHolder.clear();
        }
    }
}
