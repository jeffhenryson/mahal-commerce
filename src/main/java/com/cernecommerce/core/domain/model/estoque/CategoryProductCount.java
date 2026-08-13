package com.cernecommerce.core.domain.model.estoque;

/** Categoria com mais produtos vinculados — usado pelo KPI de resumo do catálogo. */
public record CategoryProductCount(String name, long count) {
}
