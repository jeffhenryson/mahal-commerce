package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.KitTemplate;

import java.util.List;
import java.util.Optional;

/** Persistência dos modelos de kit montável (EST-F031). */
public interface KitTemplateRepository {

    /** Cria ou substitui o modelo inteiro, passos incluídos. */
    KitTemplate save(KitTemplate template);

    Optional<KitTemplate> findById(Long id);

    /** Todos os modelos, por nome. */
    List<KitTemplate> findAll();

    /** Comparação sem caixa — mesma regra de nome de categoria. */
    Optional<KitTemplate> findByNameIgnoreCase(String name);

    void deleteById(Long id);
}
