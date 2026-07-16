package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.Cart;

/**
 * Port de entrada do domínio <b>ecommerce</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code addToCart}, {@code applyCoupon}, {@code evaluatePromotions} (motor de
 * promoções), {@code checkout} (integração de pagamentos).</p>
 */
public interface EcommerceUseCase {

    /** Lista os carrinhos paginados. Stub: retorna página vazia até a implementação. */
    PageResult<Cart> listCarts(int page, int size);
}
