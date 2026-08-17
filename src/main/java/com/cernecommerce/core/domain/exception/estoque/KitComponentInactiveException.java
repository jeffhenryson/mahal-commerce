package com.cernecommerce.core.domain.exception.estoque;

/**
 * Componente de kit precisa estar ativo — checagem irmã de {@link KitComponentNotEligibleException},
 * mas sobre {@code Product.active}, não sobre o opt-in {@code kitComponentEligible}. Um componente
 * desativado não deveria compor receita nova nem ser reafirmado numa redefinição de receita.
 */
public class KitComponentInactiveException extends RuntimeException {
    public KitComponentInactiveException(String kitSku, String componentSku) {
        super("Componente " + componentSku + " do kit " + kitSku + " está desativado");
    }
}
