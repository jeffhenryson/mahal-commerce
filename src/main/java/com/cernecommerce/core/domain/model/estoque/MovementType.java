package com.cernecommerce.core.domain.model.estoque;

/**
 * Tipo de movimentação de estoque.
 *
 * <p><b>Atenção à assimetria de {@code AJUSTE}</b> (EST-C009): em {@code ENTRADA} e {@code SAIDA}
 * a quantidade é um <i>delta</i> — quanto entrou, quanto saiu. Em {@code AJUSTE} ela é o
 * <i>saldo resultante</i>: o valor contado na prateleira. É o que permite corrigir inventário
 * para baixo, coisa que antes só dava para fazer lançando uma {@code SAIDA} falsa.</p>
 */
public enum MovementType {

    /** Delta positivo: soma ao saldo. */
    ENTRADA,

    /** Delta negativo: subtrai do saldo, sem deixá-lo negativo. */
    SAIDA,

    /**
     * Saldo-alvo: o saldo passa a valer exatamente a quantidade informada, para cima ou para
     * baixo. Zero é um alvo válido (item que acabou ou sumiu).
     */
    AJUSTE
}
