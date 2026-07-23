package com.cernecommerce.core.domain.exception.compras;

public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException(Long supplierId) {
        super("Fornecedor não encontrado: " + supplierId);
    }
}
