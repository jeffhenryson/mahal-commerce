package com.cernecommerce.core.domain.exception.pdv;

/**
 * SKU sem {@code availableForTable} lançado numa comanda de mesa.
 *
 * <p>É a regra "bebida e narguilé saem na mesa, cigarro e isqueiro não" existindo no servidor. Sem
 * ela, o filtro viveria só no cliente — e uma tela desatualizada, ou uma chamada direta à API,
 * passaria por cima.</p>
 */
public class NotAvailableForTableException extends RuntimeException {
    public NotAvailableForTableException(String sku) {
        super("SKU " + sku + " não está disponível para comanda de mesa");
    }
}
