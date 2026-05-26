package com.securityspring.infra.security.ratelimit;

import com.securityspring.core.ports.out.LoginRateLimiterPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoginRateLimitingFilter extends OncePerRequestFilter {

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
        // /auth/refresh is intentionally excluded: refresh tokens are opaque and rotation already
        // detects reuse (full session revocation). Per-IP rate limiting adds NAT false positives
        // without meaningful security benefit over the existing token-reuse detection.
        return !"/auth/login".equals(path)
                && !"/auth/verify-email".equals(path)
                && !"/auth/resend-verification".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimiter.tryConsume(clientIp(request))) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"Too many login attempts\"}");
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
