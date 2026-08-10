package com.cernecommerce.core.domain.model.estoque;

/**
 * Categoria do catálogo.
 *
 * <p>Nasceu de um pedido concreto do admin: poder mandar uma categoria para a primeira linha do
 * app/marketplace. Isso é impossível enquanto categoria for texto livre dentro do produto — não há
 * onde pendurar "destaque" nem "ordem" de algo que não existe como registro.</p>
 *
 * <p><b>A entidade é aditiva, não substitutiva.</b> {@code product.category} continua existindo
 * como texto e continua sendo devolvido nos DTOs: o {@code mahal-market} e o próprio
 * {@code mahal-admin} leem aquele campo hoje, e trocá-lo por um id quebraria os dois de uma vez.
 * O produto ganha um vínculo opcional com esta entidade, e o texto passa a ser o nome
 * denormalizado, mantido em sincronia pelo backend.</p>
 *
 * <p>{@code featured} e {@code displayOrder} são coisas diferentes de propósito: destaque é
 * booleano e responde "aparece na primeira linha?"; ordem é a posição relativa <b>dentro</b> de
 * cada grupo. Ordenar só por um número faria "destacar" virar "editar a ordem de todo mundo".</p>
 */
public record Category(Long id, String name, boolean featured, int displayOrder, boolean active) {

    public Category {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name da categoria é obrigatório");
        }
        name = name.trim();
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder não pode ser negativo");
        }
    }

    /** Cria uma categoria nova: sem id, ativa, sem destaque e no fim da ordem. */
    public static Category create(String name) {
        return create(name, false, 0);
    }

    /** Cria uma categoria nova (sem id, ativa) com destaque e ordem explícitos. */
    public static Category create(String name, boolean featured, int displayOrder) {
        return new Category(null, name, featured, displayOrder, true);
    }

    /** Reconstitui a partir de persistência. */
    public static Category of(Long id, String name, boolean featured, int displayOrder, boolean active) {
        return new Category(id, name, featured, displayOrder, active);
    }

    /**
     * Alteração parcial: argumento nulo significa <b>não mexer</b> — mesma semântica de
     * {@code Product.withDetails}. {@code active} fica de fora, em endpoint próprio, pelo mesmo
     * motivo de produto e depósito (EST-F018): desativar merece evento de auditoria próprio, e
     * não se confundir com uma correção de nome.
     */
    public Category withDetails(String newName, Boolean newFeatured, Integer newDisplayOrder) {
        return new Category(id,
                newName == null ? name : newName,
                newFeatured == null ? featured : newFeatured,
                newDisplayOrder == null ? displayOrder : newDisplayOrder,
                active);
    }

    /** Ativa ou desativa a categoria, preservando o resto. */
    public Category withActive(boolean newActive) {
        return new Category(id, name, featured, displayOrder, newActive);
    }
}
