package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findBySku(String sku);

    // Cobre SKU pai e SKU de variação numa consulta só. Ambas as colunas já têm índice único
    // (uk_product_sku e uk_product_variant_sku, da V44), então o LEFT JOIN não custa varredura.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.sku = :sku OR v.sku = :sku")
    boolean existsBySkuOrVariantSku(String sku);

    // EST-F019: resolve o produto pai a partir de QUALQUER sku do catálogo — o próprio pai ou o
    // de uma variação. É o caminho de consulta de preço do PDV, que lê o código de barras da
    // variação (o sabor específico na prateleira) mas precisa do preço, que mora no pai.
    // O JOIN FETCH evita o N+1 de carregar as variações depois.
    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.attributes "
            + "WHERE p.sku = :sku OR p.id IN (SELECT v2.product.id FROM ProductVariantEntity v2 WHERE v2.sku = :sku)")
    Optional<ProductEntity> findByAnySku(String sku);

    // EST-F018: um SKU só conta como ativo se o produto pai estiver ativo. Para SKU de variação
    // exige-se as duas pontas — desativar o pai tira a grade inteira de circulação, sem precisar
    // percorrer variação por variação.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.active = TRUE "
            + "AND (p.sku = :sku OR (v.sku = :sku AND v.active = TRUE))")
    boolean isSkuActive(String sku);

    /**
     * Listagem filtrada do catálogo, no mesmo idioma {@code :param IS NULL OR ...} de
     * {@code OrderJpaRepository.findFiltered} — uma Specification daria o mesmo para quatro
     * critérios fixos, com muito mais cerimônia.
     *
     * <p>Sem {@code ORDER BY} na query, de propósito: a ordenação chega pelo {@code Pageable},
     * porque o campo é escolhido pelo cliente dentro de uma lista fechada
     * ({@code ProductSortField}). Concatenar o nome da coluna na string da query seria injeção.</p>
     *
     * <p>{@code search} chega já em minúsculas e com os {@code %} aplicados pelo adapter, e cobre
     * nome e SKU — é o mesmo campo de busca único da tela do admin.</p>
     *
     * <p>Padrão ID-first, como toda paginação do módulo: pagina só os ids e o fetch das variações
     * vem depois, por {@code findAllByIdsWithVariants} — {@code LIMIT}/{@code OFFSET} junto de
     * {@code JOIN FETCH} de coleção é o bug clássico de paginação com JPA. Substituiu o antigo
     * {@code findAllIds}, que era este mesmo com todos os filtros nulos.</p>
     */
    @Query("""
            SELECT p.id FROM ProductEntity p
            WHERE (:search   IS NULL OR LOWER(p.name) LIKE :search OR LOWER(p.sku) LIKE :search)
              AND (:category IS NULL OR LOWER(p.category) = :category)
              AND (:brand    IS NULL OR LOWER(p.brand)    = :brand)
              AND (:active   IS NULL OR p.active = :active)
            """)
    Page<Long> findFilteredIds(@Param("search") String search,
            @Param("category") String category,
            @Param("brand") String brand,
            @Param("active") Boolean active,
            Pageable pageable);

    // ECM-F002: mesma condição de Pricing#isPriced() traduzida para SQL — salePrice preenchido,
    // ou custo+markup preenchidos o bastante para sugerir um preço. Kit nunca tem costPrice
    // próprio (KitCostNotEditableException), então só o ramo do salePrice se aplica a ele.
    // onSale segue o mesmo idioma de filtro opcional de OrderJpaRepository.findFiltered: nulo não filtra.
    @Query("SELECT p.id FROM ProductEntity p WHERE p.active = TRUE "
            + "AND (p.salePrice IS NOT NULL OR (p.costPrice IS NOT NULL AND p.markupPercent IS NOT NULL)) "
            + "AND (:onSale IS NULL OR p.onSale = :onSale) "
            + "ORDER BY p.id")
    Page<Long> findActivePricedIds(Pageable pageable, Boolean onSale);

    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.attributes "
            + "WHERE p.id IN :ids ORDER BY p.id")
    List<ProductEntity> findAllByIdsWithVariants(List<Long> ids);
}
