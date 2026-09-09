package com.cernecommerce.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.infra.handler.ApiError;
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
import java.nio.charset.StandardCharsets;

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
            // PLAT-C050 — o charset é obrigatório aqui: MediaType.APPLICATION_JSON_VALUE é
            // "application/json" seco, e sem ele o getWriter() do Tomcat cai no default do
            // container. A mensagem acentuada saía mangled ("Muitas tentativas ? aguarde"), e o
            // cliente ainda recebia um Content-Type sem charset. Mesmo par setContentType +
            // setCharacterEncoding de RestAuthenticationEntryPoint e RestAccessDeniedHandler.
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            MAPPER.writeValue(response.getWriter(), error);
            return;
        }
        chain.doFilter(request, response);
    }
}
