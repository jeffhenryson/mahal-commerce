package com.cernecommerce.infra.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.cernecommerce.infra.handler.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Resposta de 403 para requisição autenticada sem a permissão exigida.
 *
 * <p><b>PLAT-C050.</b> Mesmo alinhamento do {@link RestAuthenticationEntryPoint}: o corpo passou
 * de {@code {"error":"forbidden"}} para {@link ApiError}, com {@code errorCode ACCESS_DENIED} —
 * o mesmo que o {@code GlobalExceptionHandler} usa quando a {@code AccessDeniedException} chega
 * até ele em vez de ser interceptada aqui.</p>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        ApiError error = ApiError.of("Acesso negado", "ACCESS_DENIED",
                request.getRequestURI(), MDC.get("traceId"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), error);
    }
}
