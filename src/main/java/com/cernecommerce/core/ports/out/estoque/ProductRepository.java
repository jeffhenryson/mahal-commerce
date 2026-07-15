package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.Product;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de produtos do estoque.
 *
 * <p>Stub — a ser implementado por um adapter em {@code adapter/out/persistence}.</p>
 */
public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findBySku(String sku);

    Product save(Product product);
}
