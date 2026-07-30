package com.cernecommerce.core.domain.exception.estoque;

/**
 * Kit não tem saldo próprio nem contagem física própria (EST-F015) — {@code AJUSTE} direto no
 * SKU do kit não tem o que ajustar. Balanço de inventário deve contar os componentes, não o kit.
 */
public class KitDirectAdjustmentException extends RuntimeException {
    public KitDirectAdjustmentException(String kitSku) {
        super("Kit não tem saldo próprio para ajustar: " + kitSku);
    }
}
