package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.ports.in.EstoqueUseCase;

import java.util.List;

/**
 * Implementação stub do {@link EstoqueUseCase}.
 *
 * <p>Classe pura conectada via {@code @Bean} em {@code CoreBeanConfig}. Quando o
 * adapter de persistência existir, injetar {@code ProductRepository} pelo
 * construtor (ver {@code core.ports.out.estoque}).</p>
 */
public class EstoqueService implements EstoqueUseCase {

    // TODO: injetar ProductRepository (core.ports.out.estoque) quando o adapter existir.

    @Override
    public List<Product> listProducts() {
        // TODO: delegar ao ProductRepository.findAll().
        return List.of();
    }
}
