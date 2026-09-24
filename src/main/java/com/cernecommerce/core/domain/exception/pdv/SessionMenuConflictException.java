package com.cernecommerce.core.domain.exception.pdv;

/**
 * Cadastro do cardápio de sessão (PDV-F021) em conflito: nome de faixa ou código de utensílio
 * repetido, ou configuração incompleta para lançar a sessão (vaso padrão/grande não definido).
 */
public class SessionMenuConflictException extends RuntimeException {
    public SessionMenuConflictException(String message) {
        super(message);
    }
}
