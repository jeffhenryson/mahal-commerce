package com.cernecommerce.infra.handler;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resposta de erro padrão da API")
public record ApiError(
        @Schema(description = "Mensagem de erro legível") String message,
        @Schema(description = "Código de erro para tratamento programático") String errorCode,
        @Schema(description = "Momento em que o erro ocorreu") Instant timestamp,
        @Schema(description = "Caminho da requisição que gerou o erro") String path,
        @Schema(description = "ID de rastreabilidade para correlação de logs") String traceId
) {
    public static ApiError of(String message, String errorCode, String path, String traceId) {
        return new ApiError(message, errorCode, Instant.now(), path, traceId);
    }
}
