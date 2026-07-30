package com.cernecommerce.core.domain.exception.estoque;

/**
 * Custo de kit é sempre derivado da soma dos componentes (EST-F015) — aceitar um
 * {@code costPrice} digitado o tornaria dado morto, sobrescrito na próxima leitura de
 * {@code findPricingBySku}. Rejeitado em vez de aceito e ignorado, mesmo espírito de
 * {@code changeAmount} só existir em BALCAO ou {@code installments} só em CREDITO.
 */
public class KitCostNotEditableException extends RuntimeException {
    public KitCostNotEditableException(String sku) {
        super("Custo de kit é derivado dos componentes, não pode ser definido diretamente: " + sku);
    }
}
