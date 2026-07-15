package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de sessões de caixa do PDV.
 *
 * <p>Stub — a ser implementado por um adapter em {@code adapter/out/persistence}.</p>
 */
public interface CashRegisterRepository {

    List<CashRegisterSession> findAll();

    Optional<CashRegisterSession> findOpenByOperator(String operator);

    CashRegisterSession save(CashRegisterSession session);
}
