package com.cernecommerce.core.domain.model.ecommerce;

import java.math.BigDecimal;

/**
 * Carrinho de compras online. Os itens ({@code CartItem}) e a aplicação de
 * cupons/promoções serão modelados na sequência.
 *
 * <p>Stub de domínio — campos representativos.</p>
 */
public record Cart(
    Long id,
    String customerRef,
    int itemCount,
    BigDecimal subtotal,
    String appliedCoupon
) {
}
