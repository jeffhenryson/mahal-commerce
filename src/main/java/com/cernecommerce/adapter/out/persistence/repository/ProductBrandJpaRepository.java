package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductBrandEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductBrandJpaRepository extends JpaRepository<ProductBrandEntity, Long> {

    // Case-insensitive: o admin ainda manda a marca como texto livre no cadastro do produto, e
    // "Zomo"/"zomo" não podem virar duas marcas. O índice funcional criado na V107 (LOWER(name))
    // é o que sustenta esta consulta.
    @Query("SELECT b FROM ProductBrandEntity b WHERE LOWER(b.name) = LOWER(:name)")
    Optional<ProductBrandEntity> findByNameIgnoringCase(@Param("name") String name);

    Page<ProductBrandEntity> findAllByOrderByNameAsc(Pageable pageable);

    @Query("SELECT b FROM ProductBrandEntity b WHERE :search IS NULL OR LOWER(b.name) LIKE :search ORDER BY b.name ASC")
    Page<ProductBrandEntity> search(@Param("search") String search, Pageable pageable);

    List<ProductBrandEntity> findByActiveTrueOrderByNameAsc();
}
