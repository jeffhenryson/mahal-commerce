package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BrandResponseDTO {
    private Long id;
    private String name;
    private boolean active;

    /** Produtos vinculados a esta marca — evita a varredura de catálogo no admin. */
    private long productCount;

    /**
     * Margem média (sobre a venda) dos produtos vinculados, ou nula se nenhum produto vinculado
     * tem precificação suficiente para calcular margem. Mesma fórmula de
     * {@code Pricing#marginPercent()}, promediada por produto.
     */
    private BigDecimal averageMarginPercent;
}
