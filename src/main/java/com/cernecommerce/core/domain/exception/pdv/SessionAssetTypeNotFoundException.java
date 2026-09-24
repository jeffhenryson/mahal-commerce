package com.cernecommerce.core.domain.exception.pdv;

/** Tipo de utensílio da sessão (PDV-F021) inexistente. */
public class SessionAssetTypeNotFoundException extends RuntimeException {
    public SessionAssetTypeNotFoundException(Object idOrCode) {
        super("Utensílio de sessão não encontrado: " + idOrCode);
    }
}
