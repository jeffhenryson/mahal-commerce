package com.cernecommerce.core.domain.model.estoque;

/**
 * Campos pelos quais o catálogo pode ser ordenado.
 *
 * <p>É uma <b>lista fechada</b> e não um nome de coluna vindo do request: ordenação montada por
 * concatenação de string é injeção de SQL disfarçada de conveniência. A tradução de cada valor
 * para o nome do atributo JPA fica no adapter de persistência, para o domínio não precisar
 * conhecer o mapeamento.</p>
 */
public enum ProductSortField {
    ID,
    NAME,
    SALE_PRICE
}
