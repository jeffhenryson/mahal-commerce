package com.securityspring.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.securityspring.infra.handler.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    // Static mapper avoids a Spring-managed ObjectMapper dependency that can be absent
    // in lightweight test contexts (e.g. @SpringBootTest without full Jackson auto-config).
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final LoginRateLimiterPort rateLimiter;
    private final long windowSeconds;

    public LoginRateLimitingFilter(LoginRateLimiterPort rateLimiter,
                                   @Value("${rate.limit.login.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Subtract context-path so the check works with or without server.servlet.context-path.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method)) return true;
        return !"/auth/login".equals(path)
                && !"/auth/register".equals(path)
                && !"/auth/verify-email".equals(path)
                && !"/auth/resend-verification".equals(path)
                && !"/auth/refresh".equals(path)
                && !"/auth/forgot-password".equals(path)
                && !"/auth/reset-password".equals(path)
                && !"/auth/2fa/verify".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimiter.tryConsume(clientIp(request))) {
            ApiError error = ApiError.of(
                    "Muitas tentativas — aguarde antes de tentar novamente",
                    "TOO_MANY_REQUESTS",
                    request.getRequestURI(),
                    MDC.get("traceId"));
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            MAPPER.writeValue(response.getWriter(), error);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        // Rely on server.forward-headers-strategy (native in hml/prod, none in dev).
        // Spring/Tomcat already resolves the real client IP before this filter runs.
        return request.getRemoteAddr();
    }
}
