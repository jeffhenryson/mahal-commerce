package com.cernecommerce.core.domain.exception.pdv;

/**
 * O operador já tem um caixa aberto (PDV-F001).
 *
 * <p>Uma sessão por operador é o que torna a conferência do fechamento possível: com duas abertas,
 * não há como dizer em qual gaveta o dinheiro de uma venda entrou. A regra é garantida também por
 * índice parcial único no schema (V66) — o domínio checa antes, o banco sobrevive a duas
 * requisições simultâneas.</p>
 */
public class CashRegisterSessionAlreadyOpenException extends RuntimeException {

    public CashRegisterSessionAlreadyOpenException(String operator, Long openSessionId) {
        super("O operador " + operator + " já tem o caixa " + openSessionId
                + " aberto. Feche-o antes de abrir outro.");
    }
}
