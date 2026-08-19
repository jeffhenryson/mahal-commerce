package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.AttributeType;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência do vocabulário de tipos de atributo.
 */
public interface AttributeTypeRepository {

    AttributeType save(AttributeType type);

    /** Busca por nome, ignorando maiúsculas — mesmo caminho de compatibilidade de {@code BrandRepository}. */
    Optional<AttributeType> findByName(String name);

    /** Todos os tipos cadastrados, ordenados por nome. */
    List<AttributeType> findAllOrderByName();
}
