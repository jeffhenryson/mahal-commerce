package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashMovement;

import java.math.BigDecimal;

/**
 * Port de saída para o ledger de movimentos de caixa (PDV-F002).
 *
 * <p>Append-only por desenho: não há {@code update} nem {@code delete}. O valor esperado na gaveta é
 * derivado destes lançamentos, e corrigir um movimento editando a linha tornaria o fechamento
 * inauditável — a correção é um movimento novo em sentido contrário.</p>
 */
public interface CashMovementRepository {

    CashMovement save(CashMovement movement);

    /** Movimentos da sessão, na ordem em que aconteceram. Paginado desde PDV-C012. */
    PageResult<CashMovement> findBySessionId(Long sessionId, int page, int size);

    /**
     * Efeito líquido dos movimentos no saldo da gaveta: {@code suprimentos − sangrias}.
     * Zero quando não houve movimento — nunca {@code null}, para o fechamento não precisar tratar
     * o caso vazio.
     */
    BigDecimal sumSignedAmountBySessionId(Long sessionId);
}
