package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

@Data
public class CategoryResponseDTO {
    private Long id;
    private String name;

    /** Categoria em destaque — a vitrine a coloca na primeira linha. */
    private boolean featured;

    /** Posição relativa dentro do grupo; destacadas e não destacadas ordenam separadamente. */
    private int displayOrder;

    private boolean active;

    /** Produtos vinculados a esta categoria (Bloco 2.1) — evita a varredura de catálogo no admin. */
    private long productCount;
}
