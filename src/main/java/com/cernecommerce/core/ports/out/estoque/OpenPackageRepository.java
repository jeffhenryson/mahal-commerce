package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.OpenPackage;

import java.util.List;
import java.util.Optional;

/** Persistência da lata aberta (EST-F027). */
public interface OpenPackageRepository {

    /**
     * A lata em uso de um par SKU/depósito, se houver. No máximo uma existe por par — garantido
     * por índice único parcial, não só pela aplicação.
     */
    Optional<OpenPackage> findOpen(String sku, Long warehouseId);

    /** Todas as latas em uso de um depósito, para a tela de acompanhamento. */
    List<OpenPackage> findAllOpen(Long warehouseId);

    OpenPackage save(OpenPackage openPackage);
}
