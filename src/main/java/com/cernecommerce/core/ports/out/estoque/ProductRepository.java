package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;

import java.util.Optional;

/**
 * Port de saída para persistência de produtos do estoque.
 */
public interface ProductRepository {

    PageResult<Product> findAll(int page, int size);

    /**
     * Só produto ativo e precificado, paginado — a base do catálogo público (ECM-F002). Filtrado
     * na consulta, não em memória sobre {@link #findAll}, para a página e o total baterem certo.
     *
     * @param onSale filtro opcional de promoção (Estágio 01 do admin) — {@code null} não filtra;
     *        {@code true}/{@code false} restringe à sinalização exata.
     */
    PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale);

    Optional<Product> findBySku(String sku);

    /**
     * Busca o produto pai a partir de <b>qualquer</b> SKU do catálogo — o do próprio pai ou o de
     * uma variação. Distinto de {@link #findBySku(String)}, que só encontra pelo SKU pai.
     *
     * <p>É o caminho de resolução de preço (EST-F019): o PDV lê o código da variação na
     * prateleira, mas a {@code Pricing} mora no pai.</p>
     */
    Optional<Product> findByAnySku(String sku);

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
