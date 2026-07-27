package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;

import java.util.Optional;

/**
 * Port de saída para persistência de produtos do estoque.
 */
public interface ProductRepository {

    PageResult<Product> findAll(int page, int size);

    Optional<Product> findBySku(String sku);

    /**
     * Indica se o SKU existe no catálogo, seja como SKU pai de um produto ou como SKU de uma
     * variação. É a checagem usada antes de movimentar saldo, para impedir que uma digitação
     * errada crie saldo e ledger órfãos.
     */
    boolean existsBySku(String sku);

    /**
     * Indica se o SKU está <b>ativo</b> e, portanto, pode receber entrada de estoque (EST-F018).
     * SKU de variação exige que a variação e o produto pai estejam ativos.
     *
     * <p>Distinto de {@link #existsBySku(String)}: um SKU desativado continua existindo — o
     * histórico e o saldo dele seguem válidos, e a saída continua permitida.</p>
     */
    boolean isSkuActive(String sku);

    Product save(Product product);
}
