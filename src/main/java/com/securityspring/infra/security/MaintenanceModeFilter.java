package com.securityspring.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.core.ports.out.SystemConfigPort;
import com.securityspring.infra.handler.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final String[] ALLOWED_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/system/config/public"
    };

    private final SystemConfigPort systemConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public MaintenanceModeFilter(SystemConfigPort systemConfig) {
        this.systemConfig = systemConfig;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String allowed : ALLOWED_PATHS) {
            if (pathMatcher.match(allowed, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (systemConfig.getBoolean("security.maintenance.enabled", false)) {
            ApiError error = ApiError.of(
                    "Sistema em manutenção — tente novamente em breve",
                    "SERVICE_UNAVAILABLE",
                    request.getRequestURI(),
                    MDC.get("traceId"));
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            MAPPER.writeValue(response.getWriter(), error);
            return;
        }
        chain.doFilter(request, response);
    }
}
