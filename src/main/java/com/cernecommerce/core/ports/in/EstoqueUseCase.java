package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.estoque.Product;

import java.util.List;

/**
 * Port de entrada do domínio <b>estoque</b>.
 *
 * <p>Stub — expõe apenas uma leitura. Casos de uso previstos (TODO):
 * {@code createProduct} (com variações/atributos), {@code adjustStock},
 * {@code importFromNfeXml} (entrada por XML de NF-e), consulta de saldo por depósito.</p>
 */
public interface EstoqueUseCase {

    /** Lista os produtos (SKU pai). Stub: retorna lista vazia até a implementação. */
    List<Product> listProducts();
}
