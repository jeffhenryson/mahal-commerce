package com.cernecommerce.core.domain.model.estoque;

/**
 * Ciclo de vida de um balanço de inventário (EST-F006).
 *
 * <p>Não existe um estado "em contagem" separado de {@link #ABERTA}: uma contagem aberta já está
 * sendo contada, e o estado extra não mudaria nenhuma regra. {@link #CANCELADA}, ao contrário,
 * resolve um caso real — abandonar um balanço sem aplicar ajuste nenhum.</p>
 */
public enum StockCountStatus {

    /** Aceita registro de itens contados. Único estado a partir do qual se fecha ou cancela. */
    ABERTA,

    /** Ajustes aplicados ao saldo. Estado terminal: não aceita mais nenhuma alteração. */
    FECHADA,

    /** Abandonada sem tocar no saldo. Estado terminal; a contagem fica no histórico. */
    CANCELADA
}
