package com.cernecommerce.core.domain.exception.pdv;

/** Faixa de sessão (PDV-F021) inexistente — ou inativa, quando o lançamento pede uma. */
public class SessionTierNotFoundException extends RuntimeException {
    public SessionTierNotFoundException(Long tierId) {
        super("Faixa de sessão não encontrada ou inativa: " + tierId);
    }
}
