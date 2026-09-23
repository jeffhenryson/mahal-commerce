package com.cernecommerce.core.domain.model.estoque;

/**
 * Um passo do kit montável (EST-F031): "escolha a seda", "escolha o isqueiro". O cliente escolhe
 * até {@code maxItems} produtos da {@link Category} {@code categoryId}.
 *
 * <p>O passo aponta para categoria, e não para uma lista de SKUs, de propósito: o lojista já
 * organiza o catálogo por categoria, e produto novo de seda entra no passo "Seda" sem ninguém
 * lembrar de editar o kit.</p>
 */
public record KitTemplateStep(Long id, String name, int displayOrder, Long categoryId, boolean required,
        int maxItems) {

    public static final int NAME_MAX_LENGTH = 100;

    public KitTemplateStep {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name do passo é obrigatório");
        }
        name = name.trim();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name do passo excede " + NAME_MAX_LENGTH + " caracteres");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId do passo é obrigatório");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder não pode ser negativo: " + displayOrder);
        }
        if (maxItems < 1) {
            throw new IllegalArgumentException("maxItems deve ser pelo menos 1: " + maxItems);
        }
    }

    public static KitTemplateStep create(String name, int displayOrder, Long categoryId, boolean required,
            int maxItems) {
        return new KitTemplateStep(null, name, displayOrder, categoryId, required, maxItems);
    }
}
