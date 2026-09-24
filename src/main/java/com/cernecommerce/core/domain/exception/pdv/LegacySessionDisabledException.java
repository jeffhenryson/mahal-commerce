package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F021 — a sessão baseada em produto (PDV-F010: produto de sessão, OPEN_ROSH, SABOR_EXTRA,
 * TROCA) foi substituída pelo cardápio de sessão. Lançamento novo por esse caminho é recusado; o
 * histórico continua legível. Reativável por {@code pdv.sessao.legacy-enabled=true}.
 */
public class LegacySessionDisabledException extends RuntimeException {
    public LegacySessionDisabledException(String sku) {
        super("Sessão por produto desativada (" + sku + "): lance a sessão pelo cardápio da mesa "
                + "(POST /pdv/comandas/{id}/sessoes)");
    }
}
