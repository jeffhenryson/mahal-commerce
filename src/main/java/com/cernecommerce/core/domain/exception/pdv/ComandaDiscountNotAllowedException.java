package com.cernecommerce.core.domain.exception.pdv;

/**
 * Desconto no fechamento de mesa lançado por quem não tem {@code PDV_COMANDA_DISCOUNT} (PDV-F014).
 *
 * <p>403 pela mesma razão de {@link CourtesyNotAllowedException} e
 * {@link SurchargeNotAllowedException}: abater da conta é decisão comercial com dono, e o desconto
 * de fim de noite é decidido no salão, sem tabela que o justifique depois.</p>
 *
 * <p>Permissão própria, e não {@code PDV_SALE_DISCOUNT}: alçada de mesa e alçada de caixa são
 * concedidas a pessoas diferentes. O <b>teto</b>, esse sim, é compartilhado
 * ({@code pdv.sale.max-discount-percent}) — é política comercial da casa, não característica do
 * canal, e duas chaves de configuração poderiam divergir em silêncio.</p>
 */
public class ComandaDiscountNotAllowedException extends RuntimeException {
    public ComandaDiscountNotAllowedException(String username) {
        super("Usuário " + username + " não tem permissão para dar desconto no fechamento da comanda");
    }
}
