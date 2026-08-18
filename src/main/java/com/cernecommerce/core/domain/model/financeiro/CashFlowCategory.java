package com.cernecommerce.core.domain.model.financeiro;

/**
 * Categoria de um lançamento de fluxo de caixa (FIN-F004). Os 10 valores vêm do contrato de
 * escrita fechado com o front (`mahal-admin`, `Docs/BACKEND_TODO.md`, 2026-08-18) — já eram os
 * usados na versão mock que precedeu este módulo.
 */
public enum CashFlowCategory {
    FORNECEDOR_PRODUTO,
    ALUGUEL,
    FOLHA_PAGAMENTO,
    PRO_LABORE,
    TAXA_CARTAO_PIX,
    SOFTWARE_TI,
    ENERGIA_AGUA,
    VENDA_PDV,
    VENDA_ECOMMERCE,
    OUTROS
}
