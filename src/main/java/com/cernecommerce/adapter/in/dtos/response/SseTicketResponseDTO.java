package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Bilhete de uso único para abrir o stream SSE de notificações (PLAT-C051). */
@Data
@Schema(description = "Bilhete de uso único para abrir GET /notifications/stream")
public class SseTicketResponseDTO {

    @Schema(description = "Valor opaco a enviar como ?ticket= na abertura do stream",
            example = "kK3v9c1Zx0aQ8m2s7Lb4eR6tY5uI1oP3aS0dF7gH9jK")
    private String ticket;

    @Schema(description = "Segundos de validade do bilhete a partir da emissão", example = "30")
    private long expiresInSeconds;
}
