package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findBySku(String sku);

    // Cobre SKU pai e SKU de variação numa consulta só. Ambas as colunas já têm índice único
    // (uk_product_sku e uk_product_variant_sku, da V44), então o LEFT JOIN não custa varredura.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.sku = :sku OR v.sku = :sku")
    boolean existsBySkuOrVariantSku(String sku);

    // Padrão ID-first: pagina apenas os ids, depois faz JOIN FETCH das variações
    // pelos ids já resolvidos — evita LIMIT/OFFSET junto de fetch de coleção (bug clássico de paginação com JOIN FETCH).
    @Query("SELECT p.id FROM ProductEntity p ORDER BY p.id")
    Page<Long> findAllIds(Pageable pageable);

    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v LEFT JOIN FETCH v.attributes "
            + "WHERE p.id IN :ids ORDER BY p.id")
    List<ProductEntity> findAllByIdsWithVariants(List<Long> ids);
}
