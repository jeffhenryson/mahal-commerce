package com.cernecommerce.core.domain.exception.pdv;

/**
 * Cortesia lançada por quem não tem {@code PDV_COMANDA_COURTESY}.
 *
 * <p>403 pela mesma razão de {@code PDV_SALE_DISCOUNT} em {@code SaleItemRequest.discountAmount}:
 * lançar uma linha a preço zero é um desconto de 100%, e desconto tem dono.</p>
 */
public class CourtesyNotAllowedException extends RuntimeException {
    public CourtesyNotAllowedException(String username) {
        super("Usuário " + username + " não tem permissão para lançar cortesia na comanda");
    }
}
