package com.cernecommerce.core.domain.exception.estoque;

/**
 * Componente de kit precisa ter {@code kitComponentEligible = true} — flag de negócio, opt-in,
 * distinta da regra de domínio que já exige {@code type == SIMPLES}.
 */
public class KitComponentNotEligibleException extends RuntimeException {
    public KitComponentNotEligibleException(String kitSku, String componentSku) {
        super("Componente " + componentSku + " do kit " + kitSku + " não está marcado como elegível para kit");
    }
}
