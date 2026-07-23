package com.cernecommerce.core.ports.out.compras;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.Supplier;

import java.util.Optional;

/**
 * Port de saída para persistência de fornecedores.
 */
public interface SupplierRepository {

    PageResult<Supplier> findAll(int page, int size);

    Optional<Supplier> findByTaxId(String taxId);

    Optional<Supplier> findById(Long id);

    Supplier save(Supplier supplier);
}
