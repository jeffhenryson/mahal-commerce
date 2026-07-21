package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

@Data
public class TagSummaryResponseDTO {
    private Long id;
    private String nome;
    private long clientesCount;
}
