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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Resposta de 401 para requisição que chega sem autenticação válida.
 *
 * <p><b>PLAT-C050.</b> O corpo era {@code {"error":"unauthorized","message":"Authentication
 * required"}} — sem {@code errorCode}, {@code path} nem {@code traceId}, contrato divergente de
 * todo o resto da API, que responde {@link ApiError}. Quem tomava esse 401 não tinha como
 * correlacionar com o log nem tratar por código. Agora é o mesmo envelope do
 * {@code GlobalExceptionHandler}.</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Mapper estático pelo mesmo motivo dos filtros de rate limit: não depender de um ObjectMapper
    // gerenciado pelo Spring, que pode não existir em contexto de teste enxuto.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        ApiError error = ApiError.of("Autenticação necessária — faça login novamente", "UNAUTHORIZED",
                request.getRequestURI(), MDC.get("traceId"));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), error);
    }
}
