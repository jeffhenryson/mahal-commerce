package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.SessionTier;

import java.math.BigDecimal;

public record SessionTierResponseDTO(Long id, String nome, BigDecimal preco, String marcas, int ordem,
        boolean ativo) {

    public static SessionTierResponseDTO of(SessionTier t) {
        return new SessionTierResponseDTO(t.id(), t.nome(), t.preco(), t.marcas(), t.ordem(), t.ativo());
    }
}
