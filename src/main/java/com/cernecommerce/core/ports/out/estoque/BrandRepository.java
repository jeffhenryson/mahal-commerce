package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Brand;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de marcas do catálogo.
 */
public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    /**
     * Busca por nome, <b>ignorando maiúsculas</b> — é o caminho de compatibilidade: o admin ainda
     * manda a marca como texto livre no cadastro do produto, e "Zomo" e "zomo" não podem virar
     * duas marcas.
     */
    Optional<Brand> findByName(String name);

    /** Listagem do admin, ordenada por nome. Inclui as inativas. */
    PageResult<Brand> findAll(int page, int size);

    /** Marcas cujo nome contém {@code search} (sem diferenciar maiúsculas), ordenadas por nome. */
    PageResult<Brand> findByNameContaining(String search, int page, int size);

    /** Marcas ativas, ordenadas por nome. */
    List<Brand> findActiveOrdered();

    /**
     * Remove a marca. O chamador ({@code EstoqueService.deleteBrand}) já garantiu que não há
     * produto vinculado — esta camada não repete a checagem.
     */
    void deleteById(Long id);
}
