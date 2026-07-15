package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.ports.in.EcommerceUseCase;

import java.util.List;

/**
 * Implementação stub do {@link EcommerceUseCase}.
 *
 * <p>Classe pura conectada via {@code @Bean} em {@code CoreBeanConfig}. Quando os
 * adapters existirem, injetar {@code CartRepository} e {@code PaymentGatewayPort}
 * pelo construtor (ver {@code core.ports.out.ecommerce}).</p>
 */
public class EcommerceService implements EcommerceUseCase {

    // TODO: injetar CartRepository / PaymentGatewayPort (core.ports.out.ecommerce).

    @Override
    public List<Cart> listCarts() {
        // TODO: delegar ao CartRepository.findAll().
        return List.of();
    }
}
