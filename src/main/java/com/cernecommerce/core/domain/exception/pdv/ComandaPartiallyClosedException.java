package com.cernecommerce.core.domain.exception.pdv;

/**
 * A comanda já teve parte da conta cobrada (PDV-F017) e por isso não pode ser juntada a outra
 * (PDV-F016).
 *
 * <p>Mover o restante deixaria metade do consumo numa mesa e metade na outra, com um
 * {@code sales_order.comanda_id} apontando para uma comanda que não tem mais aquelas linhas — o
 * recibo já entregue ao cliente deixaria de bater com a mesa que o gerou. Quem quiser juntar depois
 * de um fechamento parcial precisa fechar o resto, não mesclar.</p>
 */
public class ComandaPartiallyClosedException extends RuntimeException {

    public ComandaPartiallyClosedException(Long comandaId) {
        super("A comanda " + comandaId + " já teve parte da conta cobrada e não pode ser juntada a outra.");
    }
}
