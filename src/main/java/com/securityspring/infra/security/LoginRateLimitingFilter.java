package com.securityspring.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.securityspring.infra.handler.ApiError;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter rateLimitBlockedCounter;

    public LoginRateLimitingFilter(LoginRateLimiterPort rateLimiter,
                                   MeterRegistry meterRegistry,
                                   @Value("${rate.limit.login.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.windowSeconds = windowSeconds;
        this.rateLimitBlockedCounter = meterRegistry.counter("auth.rate_limit.blocked.total");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Subtract context-path so the check works with or without server.servlet.context-path.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        // DELETE /auth/2fa accepts password + TOTP code — protect against brute-force.
        if ("DELETE".equalsIgnoreCase(method)) {
            return !"/auth/2fa".equals(path);
        }

        // PUT /notifications/preferences/{type} accepts user input — guard against spam.
        if ("PUT".equalsIgnoreCase(method)) {
            return !path.startsWith("/notifications/preferences/");
        }

        if (!"POST".equalsIgnoreCase(method)) return true;
        return !"/auth/login".equals(path)
                && !"/auth/register".equals(path)
                && !"/auth/verify-email".equals(path)
                && !"/auth/resend-verification".equals(path)
                && !"/auth/refresh".equals(path)
                && !"/auth/forgot-password".equals(path)
                && !"/auth/reset-password".equals(path)
                && !"/auth/2fa/verify".equals(path)
                && !"/auth/2fa/confirm".equals(path)
                && !"/auth/2fa/replace".equals(path)
                && !"/auth/2fa/backup-codes/regenerate".equals(path)
                && !"/auth/oauth2/google".equals(path)
                && !"/auth/dev/first-code".equals(path)
                && !"/auth/dev/complete".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimiter.tryConsume(clientIp(request))) {
            rateLimitBlockedCounter.increment();
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
