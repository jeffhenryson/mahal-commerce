package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * Utensílio alocado a uma linha de sessão (PDV-F021). Aberta enquanto {@code liberadoEm} é nulo —
 * é isso que conta como "em uso" no cálculo de disponibilidade.
 */
public record SessionAssetAllocation(Long id, Long comandaItemId, Long assetTypeId, int quantidade,
        Instant alocadoEm, Instant liberadoEm) {

    public SessionAssetAllocation {
        if (assetTypeId == null) {
            throw new IllegalArgumentException("assetTypeId é obrigatório");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade tem que ser positiva");
        }
        if (alocadoEm == null) {
            throw new IllegalArgumentException("alocadoEm é obrigatório");
        }
    }

    public static SessionAssetAllocation allocate(Long comandaItemId, Long assetTypeId, Instant at) {
        return new SessionAssetAllocation(null, comandaItemId, assetTypeId, 1, at, null);
    }
}
