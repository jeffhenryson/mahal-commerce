package com.cernecommerce.core.domain.exception.estoque;

/**
 * Depósito padrão do marketplace (ECM-F002/F003) ainda não configurado — ver
 * {@code EstoqueUseCase#getDefaultWarehouse}. Nenhuma migration semeia um código real: criar um
 * depósito é ação do operador, não algo que a aplicação possa inventar sozinha.
 */
public class DefaultWarehouseNotConfiguredException extends RuntimeException {
    public DefaultWarehouseNotConfiguredException() {
        super("Depósito padrão do marketplace não configurado — defina a chave "
                + "estoque.warehouse.default-code em PUT /system/config/{key}");
    }
}
