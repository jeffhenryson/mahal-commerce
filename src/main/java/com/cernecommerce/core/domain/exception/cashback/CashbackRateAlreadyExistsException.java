package com.cernecommerce.core.domain.exception.cashback;

import com.cernecommerce.core.domain.model.cashback.CashbackScope;

/**
 * Já existe uma taxa ativa e vigente para a mesma abrangência. 409, não 400: a requisição está
 * bem formada, o que conflita é o estado atual do cadastro — mesma regra de
 * {@code DiscountLimitExceededException}.
 */
public class CashbackRateAlreadyExistsException extends RuntimeException {
    public CashbackRateAlreadyExistsException(CashbackScope scope, String scopeRef) {
        super("Já existe uma taxa de cashback ativa para " + scope
                + (scopeRef == null ? "" : " " + scopeRef));
    }
}
