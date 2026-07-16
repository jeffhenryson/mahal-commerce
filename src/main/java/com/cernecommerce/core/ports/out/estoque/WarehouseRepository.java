package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.Warehouse;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de depósitos de estoque.
 */
public interface WarehouseRepository {

    Warehouse save(Warehouse warehouse);

    Optional<Warehouse> findByCode(String code);

    List<Warehouse> findAll();
}
