package com.cernecommerce.core.domain.model.pdv;

/**
 * Natureza de um {@link CashMovement} — dinheiro que entra ou sai da gaveta fora de uma venda
 * (PDV-F002).
 *
 * <p>O <b>sinal vem daqui, não do valor</b>: {@code amount} é sempre positivo. Permitir valor
 * negativo criaria duas representações para a mesma sangria — {@code SANGRIA -50} e
 * {@code SUPRIMENTO 50} —, e qualquer {@code SUM} sobre a tabela passaria a depender de quem
 * gravou.</p>
 */
public enum CashMovementType {

    /** Retirada de dinheiro do caixa: sobra do troco indo para o cofre, pagamento avulso, depósito. */
    SANGRIA,

    /** Aporte de dinheiro no caixa: reforço de troco durante o expediente. */
    SUPRIMENTO;

    /** Efeito no saldo esperado da gaveta: {@code -1} para sangria, {@code +1} para suprimento. */
    public int signum() {
        return this == SANGRIA ? -1 : 1;
    }
}
