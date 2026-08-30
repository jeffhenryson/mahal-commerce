package com.cernecommerce.core.domain.exception.pdv;

import java.math.BigDecimal;

/**
 * Acréscimo negativo (PDV-F011).
 *
 * <p>Recusado em vez de tratado como desconto: acréscimo e desconto são operações opostas com o
 * mesmo peso contábil, e o relatório precisa distinguir "cobramos a mais" de "cobramos a menos".
 * Um {@code surchargeAmount} negativo seria um desconto entrando pela porta errada, sem passar por
 * {@code PDV_SALE_DISCOUNT} nem pelo teto de desconto.</p>
 */
public class SurchargeInvalidException extends RuntimeException {
    public SurchargeInvalidException(BigDecimal surchargeAmount) {
        super("surchargeAmount não pode ser negativo: " + surchargeAmount
                + " — acréscimo não é desconto pela porta dos fundos");
    }
}
