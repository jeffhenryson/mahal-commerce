package com.cernecommerce.core.domain.exception.pdv;

/**
 * Junção de comandas recusada por incompatibilidade entre origem e destino (PDV-F016).
 *
 * <p>Cobre juntar uma mesa nela mesma e juntar mesas de depósitos diferentes. O segundo caso importa
 * mais do que parece: o estoque de cada linha saiu do depósito da comanda que a recebeu, e mesclar
 * entre depósitos faria o pedido resultante declarar um {@code warehouseCode} do qual parte da
 * mercadoria nunca saiu.</p>
 */
public class ComandaMergeNotAllowedException extends RuntimeException {

    public ComandaMergeNotAllowedException(String reason) {
        super(reason);
    }
}
