package com.cernecommerce.core.domain.model.estoque;

/**
 * Marca do catálogo, promovida de campo texto livre a entidade.
 *
 * <p>{@code product.brand} era {@code VARCHAR(100)} solto desde a V82 — sem onde pendurar
 * renomear, desativar ou consolidar grafias divergentes ("Zomo"/"zomo"/"Zomo "). A entidade segue
 * o mesmo caminho aditivo de {@link Category} (V90): {@code product.brand} continua existindo
 * como texto denormalizado, e o produto ganha um vínculo opcional {@code brandId}.</p>
 *
 * <p>Mais simples que {@code Category}: sem {@code featured}/{@code displayOrder} — marca não tem
 * pedido de destaque de vitrine, só o de consolidação (renomear, desativar, deduplicar).</p>
 */
public record Brand(Long id, String name, boolean active) {

    public Brand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name da marca é obrigatório");
        }
        name = name.trim();
    }

    /** Cria uma marca nova: sem id, ativa. */
    public static Brand create(String name) {
        return new Brand(null, name, true);
    }

    /** Reconstitui a partir de persistência. */
    public static Brand of(Long id, String name, boolean active) {
        return new Brand(id, name, active);
    }

    /** Alteração parcial: argumento nulo significa <b>não mexer</b> — mesma semântica de {@code Category.withDetails}. */
    public Brand withDetails(String newName) {
        return new Brand(id, newName == null ? name : newName, active);
    }

    /** Ativa ou desativa a marca, preservando o resto. */
    public Brand withActive(boolean newActive) {
        return new Brand(id, name, newActive);
    }
}
