package com.cernecommerce.core.domain.exception.estoque;

public class ReplenishmentItemNotFoundException extends RuntimeException {
    public ReplenishmentItemNotFoundException(String sku, String warehouseCode) {
        super("Item de reposição não encontrado: sku=" + sku + ", warehouseCode=" + warehouseCode);
    }
}
