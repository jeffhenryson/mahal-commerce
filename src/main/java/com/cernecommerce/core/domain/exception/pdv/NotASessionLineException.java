package com.cernecommerce.core.domain.exception.pdv;

/**
 * Rosh extra (PDV-F021) pendurado numa linha que não é uma sessão do cardápio aberta nesta comanda.
 * 409 pelo mesmo raciocínio de {@link NotAnOpenRoshException}: a linha existe, o estado dela é que
 * impede.
 */
public class NotASessionLineException extends RuntimeException {
    public NotASessionLineException(Long itemId, Long comandaId) {
        super("A linha " + itemId + " não é uma sessão em aberto da comanda " + comandaId);
    }
}
