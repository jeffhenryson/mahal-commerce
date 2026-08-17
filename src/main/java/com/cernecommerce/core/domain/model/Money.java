package com.cernecommerce.core.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Constantes de escala e arredondamento compartilhadas pelos cálculos financeiros do domínio. */
public final class Money {

    /** Casas decimais de valores monetários — alinhado com {@code NUMERIC(14,2)} no schema. */
    public static final int MONEY_SCALE = 2;

    /** Casas decimais dos percentuais derivados (markup, margem, desconto, cashback). */
    public static final int PERCENT_SCALE = 2;

    /** Escala intermediária usada em divisões antes do arredondamento final. */
    public static final int INTERMEDIATE_SCALE = 6;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }
}
