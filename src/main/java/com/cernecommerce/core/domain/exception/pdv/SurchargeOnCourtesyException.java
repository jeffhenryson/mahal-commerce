package com.cernecommerce.core.domain.exception.pdv;

import java.math.BigDecimal;

/**
 * Acréscimo numa linha marcada como cortesia (PDV-F011).
 *
 * <p>A combinação é uma contradição, não um caso de borda: uma linha que o cliente não paga não
 * pode ter um valor extra cobrado. Espelha o CHECK
 * {@code ck_comanda_item_surcharge_not_on_courtesy}.</p>
 */
public class SurchargeOnCourtesyException extends RuntimeException {
    public SurchargeOnCourtesyException(BigDecimal surchargeAmount) {
        super("Acréscimo de " + surchargeAmount + " numa linha de cortesia: o cliente não paga a "
                + "linha, então não há sobre o que cobrar a mais");
    }
}
