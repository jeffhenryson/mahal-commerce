package com.cernecommerce.core.ports.out.compras;

import com.cernecommerce.core.domain.model.compras.Supplier;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de fornecedores.
 *
 * <p>Stub — a ser implementado por um adapter em {@code adapter/out/persistence}.</p>
 */
public interface SupplierRepository {

    List<Supplier> findAll();

    Optional<Supplier> findByTaxId(String taxId);

    Supplier save(Supplier supplier);
}
