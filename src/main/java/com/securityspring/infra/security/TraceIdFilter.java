package com.securityspring.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    // Apenas alphanumeric e hífen, máximo 64 chars — previne log injection e header injection.
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[a-zA-Z0-9\\-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && SAFE_TRACE_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        String ua = request.getHeader("User-Agent");
        DeviceInfoContext.set(request.getRemoteAddr(), ua != null && ua.length() > 512 ? ua.substring(0, 512) : ua);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            DeviceInfoContext.clear();
        }
    }
}
