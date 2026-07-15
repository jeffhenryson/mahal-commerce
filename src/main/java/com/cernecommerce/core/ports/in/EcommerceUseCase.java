package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.ecommerce.Cart;

import java.util.List;

/**
 * Port de entrada do domínio <b>ecommerce</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code addToCart}, {@code applyCoupon}, {@code evaluatePromotions} (motor de
 * promoções), {@code checkout} (integração de pagamentos).</p>
 */
public interface EcommerceUseCase {

    /** Lista os carrinhos. Stub: retorna lista vazia até a implementação. */
    List<Cart> listCarts();
}
