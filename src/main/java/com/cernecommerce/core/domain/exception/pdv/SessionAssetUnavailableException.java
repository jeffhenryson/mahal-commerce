package com.cernecommerce.core.domain.exception.pdv;

/**
 * Não há utensílio livre para montar a sessão (PDV-F021) — todos os vasos/pinças/pratos/tapetes
 * daquele tipo estão alocados em mesas abertas. 409: é o estado do salão que impede, e ele muda
 * sozinho quando uma mesa fecha.
 */
public class SessionAssetUnavailableException extends RuntimeException {

    private final String assetCode;

    public SessionAssetUnavailableException(String assetCode, String assetName, int total) {
        super("Sem " + assetName + " livre para a sessão (" + total + " no total, todos em uso)");
        this.assetCode = assetCode;
    }

    public String getAssetCode() {
        return assetCode;
    }
}
