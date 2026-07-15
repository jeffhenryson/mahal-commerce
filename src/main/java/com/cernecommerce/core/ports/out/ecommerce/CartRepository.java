package com.cernecommerce.core.ports.out.ecommerce;

import com.cernecommerce.core.domain.model.ecommerce.Cart;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de carrinhos.
 *
 * <p>Stub — a ser implementado por um adapter em {@code adapter/out/persistence}.</p>
 */
public interface CartRepository {

    List<Cart> findAll();

    Optional<Cart> findByCustomerRef(String customerRef);

    Cart save(Cart cart);
}
