package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;

import java.util.Optional;

/**
 * Port de saída para persistência de produtos do estoque.
 */
public interface ProductRepository {

    /**
     * Listagem sem filtro, ordenada por id. Mantida como {@code default} sobre a assinatura
     * filtrada para os chamadores antigos não mudarem — mesmo idioma de evolução usado em
     * {@code EstoqueUseCase}.
     */
    default PageResult<Product> findAll(int page, int size) {
        return findAll(page, size, ProductFilter.EMPTY, ProductSortField.ID, SortDirection.ASC);
    }

    /**
     * Listagem filtrada e ordenada do catálogo.
     *
     * <p>A ordenação sempre desempata por {@code id}, mesmo quando o campo pedido é outro: nome e
     * preço não são chaves únicas, e paginar por chave não-única faz o banco devolver a mesma
     * linha em duas páginas ou em nenhuma. É a mesma lição de EST-C012, que apareceu no ledger de
     * movimentações e vale igual aqui.</p>
     */
    PageResult<Product> findAll(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction);

    /**
     * Só produto ativo e precificado, paginado — a base do catálogo público (ECM-F002). Filtrado
     * na consulta, não em memória sobre {@link #findAll}, para a página e o total baterem certo.
     *
     * @param onSale filtro opcional de promoção (Estágio 01 do admin) — {@code null} não filtra;
     *        {@code true}/{@code false} restringe à sinalização exata.
     */
    default PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale) {
        return findAllActiveAndPriced(page, size, onSale, null);
    }

    /**
     * @param categoryId filtro opcional de categoria — {@code null} não filtra. A ordenação é
     *        sempre a da vitrine: categoria em destaque primeiro, depois ordem de exibição,
     *        depois id.
     */
    PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale, Long categoryId);

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

    /**
     * Atualiza em lote a coluna denormalizada {@code category} de todos os produtos vinculados a
     * uma categoria renomeada.
     *
     * <p>Update em massa e não carregar-e-salvar produto a produto: a operação toca só uma coluna
     * escalar, e materializar o agregado inteiro (com variações e atributos) de cada produto de
     * uma categoria grande para reescrever um texto seria caro sem nenhum ganho.</p>
     *
     * @return quantidade de produtos atualizados
     */
    int renameCategory(Long categoryId, String newName);
}
