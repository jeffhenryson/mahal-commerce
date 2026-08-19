package com.cernecommerce.core.domain.model.estoque;

/**
 * Vocabulário de nomes de {@link ProductAttribute#type()} — "Sabor", "Aroma", "Concentração de
 * Nicotina", "Potência/Voltagem"... Antes disto, essa lista vivia hardcoded no frontend: cadastrar
 * dez produtos com um tipo novo exigia digitar o mesmo texto dez vezes, e um erro de digitação
 * virava um atributo órfão que ninguém percebia até alguém filtrar por ele.
 *
 * <p>Escopo deliberadamente mínimo: só o nome. Sem hierarquia, sem valores predefinidos por tipo,
 * sem tipagem (texto/número/lista) — {@link ProductAttribute#value()} continua texto livre. Não há
 * FK de {@code product_attribute.attr_type} para esta tabela: é uma lista de sugestão/consistência,
 * não uma restrição — o texto livre em atributo já existente continua funcionando mesmo se o tipo
 * correspondente nunca for cadastrado aqui.</p>
 */
public record AttributeType(Long id, String name) {

    public AttributeType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name do tipo de atributo é obrigatório");
        }
        name = name.trim();
    }

    /** Cria um novo tipo de atributo (sem id). */
    public static AttributeType create(String name) {
        return new AttributeType(null, name);
    }

    /** Reconstitui a partir de persistência. */
    public static AttributeType of(Long id, String name) {
        return new AttributeType(id, name);
    }
}
