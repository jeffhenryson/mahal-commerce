package com.cernecommerce.core.domain.model.cashback;

/**
 * Tipo de lançamento no ledger de cashback (CRM-F003), append-only.
 *
 * <p>Só {@link #EARNED} e {@link #EXPIRED} têm código que os escreve nesta fatia. {@link
 * #REDEEMED} (resgate no balcão) e {@link #REVERSED} (estorno por cancelamento/devolução) já
 * existem aqui — e no {@code CHECK} do schema — para que a fatia que os implementar não precise
 * de uma migration só para alargar a constraint.</p>
 */
public enum CashbackEntryType {
    EARNED,
    REDEEMED,
    REVERSED,
    EXPIRED
}
