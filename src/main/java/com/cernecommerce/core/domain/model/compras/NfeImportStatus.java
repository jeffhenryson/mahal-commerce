package com.cernecommerce.core.domain.model.compras;

/**
 * Ciclo de vida de um {@link NfeImport} (EST-F005): {@code PREVIEWED} enquanto aguarda
 * confirmação do operador, {@code CONFIRMED} quando virou um {@code GoodsReceipt} de verdade,
 * {@code REJECTED} quando o CNPJ do emitente não bate com nenhum fornecedor cadastrado — nesse
 * caso o import nunca chega a ter itens a confirmar.
 */
public enum NfeImportStatus {
    PREVIEWED, CONFIRMED, REJECTED
}
