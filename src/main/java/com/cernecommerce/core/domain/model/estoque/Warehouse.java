package com.cernecommerce.core.domain.model.estoque;

/**
 * Depósito de estoque (loja física ou e-commerce). Cada {@link StockBalance} é
 * mantido por SKU dentro de um depósito.
 */
public record Warehouse(Long id, String code, String name, WarehouseType type, boolean active) {

    public Warehouse {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code é obrigatório");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
    }

    /** Cria um novo depósito (sem id, ativo por padrão). */
    public static Warehouse create(String code, String name, WarehouseType type) {
        return new Warehouse(null, code, name, type, true);
    }

    /** Reconstitui um depósito a partir de persistência. */
    public static Warehouse of(Long id, String code, String name, WarehouseType type, boolean active) {
        return new Warehouse(id, code, name, type, active);
    }

    /**
     * Alteração parcial (EST-F018): argumento nulo significa <b>não mexer neste campo</b>.
     *
     * <p>{@code code} não entra: é a identidade pública do depósito, usada como chave em toda a
     * API (`?warehouseCode=`) e resolvida por {@code findByCode} antes de cada escrita de saldo.</p>
     */
    public Warehouse withDetails(String newName, WarehouseType newType) {
        return new Warehouse(id, code,
                newName == null ? name : newName,
                newType == null ? type : newType,
                active);
    }

    /** Ativa ou desativa o depósito, preservando o resto. */
    public Warehouse withActive(boolean newActive) {
        return new Warehouse(id, code, name, type, newActive);
    }
}
