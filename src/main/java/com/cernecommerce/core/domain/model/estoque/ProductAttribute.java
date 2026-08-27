package com.cernecommerce.core.domain.model.estoque;

/**
 * Atributo de uma variação de produto (ex: sabor=menta, tamanho=P, cor=preto).
 * Sem identidade própria — sempre filho de um {@link ProductVariant}.
 */
public record ProductAttribute(String type, String value) {

    public ProductAttribute {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value é obrigatório");
        }
    }
}
