package com.cernecommerce.core.domain.exception.pdv;

/** Adicional de sessão (PDV-F024) inexistente — ou inativo, quando o lançamento pede um. 404. */
public class SessionAddonNotFoundException extends RuntimeException {
    public SessionAddonNotFoundException(Long addonId) {
        super("Adicional de sessão não encontrado ou inativo: " + addonId);
    }
}
