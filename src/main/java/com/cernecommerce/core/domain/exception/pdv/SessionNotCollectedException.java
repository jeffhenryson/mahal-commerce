package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F023 — encerrar a mesa com sessão ainda no salão. Encerrar liberaria os utensílios de um
 * narguilé que continua na mesa; cancelar segue permitido. 409.
 */
public class SessionNotCollectedException extends RuntimeException {
    public SessionNotCollectedException(Long comandaId) {
        super("A mesa " + comandaId + " tem sessão não recolhida — recolha antes de encerrar");
    }
}
