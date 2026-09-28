package com.cernecommerce.adapter.in.dtos.response;

import java.math.BigDecimal;

/** Adicional como foi cobrado na linha de sessão (PDV-F024) — snapshot, não o cadastro de hoje. */
public record ComandaItemAddonResponseDTO(Long id, String nome, BigDecimal preco) {
}
